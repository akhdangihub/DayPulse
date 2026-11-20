package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JournalRepository
import com.example.data.local.ChatMessageEntity
import com.example.data.local.DateKeyUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ChatUiState {
    data object Idle : ChatUiState
    data class Streaming(val partialText: String) : ChatUiState
    data class Error(val message: String) : ChatUiState
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = JournalRepository(application.applicationContext)
    private val dateKey: String = DateKeyUtil.todayDateKey()

    val messages: StateFlow<List<ChatMessageEntity>> = repository
        .observeChatForDate(dateKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun sendMessage(userInput: String) {
        val trimmed = userInput.trim()
        if (trimmed.isEmpty() || _uiState.value is ChatUiState.Streaming) return

        viewModelScope.launch {
            _uiState.value = ChatUiState.Streaming("")
            try {
                val historyPairs = repository.getChatHistoryOnce(dateKey)
                    .map { it.role to it.text }
                repository.saveChatMessage(dateKey, "user", trimmed)

                val dayContext = repository.buildDayContextForChat(dateKey)
                var latestResponse = ""
                repository.streamCompanionResponse(
                    dateKey = dateKey,
                    dayContext = dayContext,
                    history = historyPairs,
                    userMessage = trimmed
                ).collect { accumulated ->
                    latestResponse = accumulated
                    _uiState.value = ChatUiState.Streaming(accumulated)
                }

                if (latestResponse.isNotBlank()) {
                    repository.saveChatMessage(dateKey, "model", latestResponse)
                    _uiState.value = ChatUiState.Idle
                } else {
                    _uiState.value = ChatUiState.Error("Evening Companion returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Companion chat timed out", e)
                _uiState.value = ChatUiState.Error("Response timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Companion chat failed", e)
                _uiState.value = ChatUiState.Error(
                    e.localizedMessage ?: "Unable to reach Evening Companion."
                )
            }
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            repository.clearChatForDate(dateKey)
            _uiState.value = ChatUiState.Idle
        }
    }

    fun dismissError() {
        if (_uiState.value is ChatUiState.Error) {
            _uiState.value = ChatUiState.Idle
        }
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
