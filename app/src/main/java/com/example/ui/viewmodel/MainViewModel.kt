package com.example.ui.viewmodel

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.JournalRepository
import com.example.data.local.CaptureEntryEntity
import com.example.data.local.DailySummaryEntity
import com.example.data.local.DateKeyUtil
import com.example.data.local.SmartActionEntity
import com.example.data.prefs.AppThemeMode
import com.example.data.prefs.PrivacyPreferences
import com.example.service.AudioCaptureService
import com.example.service.RecordingServiceState
import com.example.worker.EveningReviewScheduleWorker
import com.example.worker.TranscriptionWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = JournalRepository(application.applicationContext)

    val recordingState: StateFlow<RecordingServiceState> = AudioCaptureService.state

    val todayDateKey: String = DateKeyUtil.todayDateKey()

    val todayCaptures: StateFlow<List<CaptureEntryEntity>> = repository
        .observeCapturesForDate(todayDateKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayActions: StateFlow<List<SmartActionEntity>> = repository
        .observeActionsForDate(todayDateKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queuedCount: StateFlow<Int> = repository
        .queuedCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val preferences: StateFlow<PrivacyPreferences> = repository
        .preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PrivacyPreferences())

    val allSummaries: StateFlow<List<DailySummaryEntity>> = repository
        .allSummariesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAcceptedActions: StateFlow<List<SmartActionEntity>> = repository
        .allAcceptedActionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<CaptureEntryEntity>> = _searchQuery
        .flatMapLatest { query -> repository.searchCaptures(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isOnline = MutableStateFlow(TranscriptionWorker.isNetworkAvailable(application))
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private val connectivityManager =
        application.getSystemService(ConnectivityManager::class.java)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
            viewModelScope.launch {
                val prefs = repository.preferencesFlow.first()
                if (com.example.data.TranscriptionProcessor.isNetworkAvailable(getApplication(), prefs.wifiOnlyProcessing)) {
                    com.example.data.TranscriptionProcessor.triggerAsync(getApplication(), includeFailed = false)
                }
            }
        }

        override fun onLost(network: Network) {
            _isOnline.value = TranscriptionWorker.isNetworkAvailable(getApplication())
        }
    }

    init {
        repository.recoverStuckTranscriptions()

        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        }

        viewModelScope.launch {
            val prefs = repository.preferencesFlow.first()
            if (prefs.eveningReminderEnabled) {
                EveningReviewScheduleWorker.scheduleNext(
                    getApplication(),
                    prefs.eveningReviewHour,
                    prefs.eveningReviewMinute
                )
            }
        }
    }

    fun startRecording() {
        AudioCaptureService.sendCommand(getApplication(), AudioCaptureService.ACTION_START)
    }

    fun pauseRecording() {
        AudioCaptureService.sendCommand(getApplication(), AudioCaptureService.ACTION_PAUSE)
    }

    fun resumeRecording() {
        AudioCaptureService.sendCommand(getApplication(), AudioCaptureService.ACTION_RESUME)
    }

    fun splitSegment() {
        AudioCaptureService.sendCommand(getApplication(), AudioCaptureService.ACTION_SPLIT_SEGMENT)
        _uiMessage.value = "Audio segment saved locally and queued for transcription."
    }

    fun stopRecording() {
        AudioCaptureService.sendCommand(getApplication(), AudioCaptureService.ACTION_STOP)
    }

    fun addQuickNote(text: String) {
        viewModelScope.launch {
            repository.addOfflineTextNote(text, todayDateKey)
            _uiMessage.value = "Note saved locally."
        }
    }

    fun importPhoto(uri: Uri) {
        viewModelScope.launch {
            val res = repository.importPhotoAndQueue(uri, todayDateKey)
            res.fold(
                onSuccess = {
                    _uiMessage.value = if (_isOnline.value) {
                        "Photo saved locally and analyzing with Gemini…"
                    } else {
                        "Photo saved offline. It will analyze when connected."
                    }
                },
                onFailure = { err ->
                    _uiMessage.value = err.localizedMessage ?: "Failed to import photo."
                }
            )
        }
    }

    fun syncQueueNow() {
        viewModelScope.launch {
            if (!_isOnline.value) {
                _uiMessage.value = "Still offline — items remain safely queued in SQLite."
            } else {
                repository.triggerOfflineQueueSync()
                _uiMessage.value = "Transcribing queued captures now…"
            }
        }
    }

    fun retryCapture(entry: CaptureEntryEntity) {
        if (!_isOnline.value) {
            _uiMessage.value = "You are offline. Reconnect to transcribe with Gemini, or edit text manually."
            return
        }
        repository.retryCapture(entry)
    }

    fun updateCaptureTranscript(entry: CaptureEntryEntity, newText: String) {
        viewModelScope.launch {
            repository.updateCaptureTranscript(entry, newText)
            _uiMessage.value = "Transcript updated in local SQLite."
        }
    }

    fun toggleBookmark(entry: CaptureEntryEntity) {
        viewModelScope.launch { repository.toggleBookmark(entry) }
    }

    fun deleteCapture(entry: CaptureEntryEntity) {
        viewModelScope.launch { repository.deleteCapture(entry) }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setAutoDeleteAudio(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoDeleteAudio(enabled) }
    }

    fun setSegmentDurationMinutes(minutes: Int) {
        viewModelScope.launch { repository.setSegmentDurationMinutes(minutes) }
    }

    fun setWifiOnlyProcessing(enabled: Boolean) {
        viewModelScope.launch { repository.setWifiOnlyProcessing(enabled) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setGlassEffectsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setGlassEffectsEnabled(enabled) }
    }

    fun setEveningReminder(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch { repository.setEveningReminder(enabled, hour, minute) }
    }

    fun purgeAllLocalAudioFiles() {
        viewModelScope.launch {
            val deleted = repository.deleteAllLocalAudioFiles()
            _uiMessage.value = "Removed $deleted local audio file(s) from device."
        }
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    override fun onCleared() {
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }
}
