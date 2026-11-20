package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DayAnalyticsCalculator
import com.example.data.DayAnalyticsUiState
import com.example.data.GoogleWorkspaceSyncService
import com.example.data.JournalRepository
import com.example.data.QuickReviewOutcome
import com.example.data.local.ActionCategory
import com.example.data.local.ActionPriority
import com.example.data.local.DailySummaryEntity
import com.example.data.local.DateKeyUtil
import com.example.data.local.ReviewStatus
import com.example.data.local.SmartActionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface EveningSynthesisUiState {
    data object Idle : EveningSynthesisUiState
    data object Loading : EveningSynthesisUiState
    data object Success : EveningSynthesisUiState
    data class Error(val message: String) : EveningSynthesisUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class EveningReviewViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = JournalRepository(application.applicationContext)

    private val _selectedDateKey = MutableStateFlow(DateKeyUtil.todayDateKey())
    val selectedDateKey: StateFlow<String> = _selectedDateKey.asStateFlow()

    val availableDateKeys: StateFlow<List<String>> = repository.distinctDatesFlow
        .map { dates ->
            val today = DateKeyUtil.todayDateKey()
            if (dates.contains(today)) dates else listOf(today) + dates
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(DateKeyUtil.todayDateKey()))

    val newCapturesCount: StateFlow<Int> = _selectedDateKey
        .flatMapLatest { key -> repository.observeNewForSummaryCount(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val summary: StateFlow<DailySummaryEntity?> = _selectedDateKey
        .flatMapLatest { key -> repository.observeSummaryForDate(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val capturesForDate = _selectedDateKey
        .flatMapLatest { key -> repository.observeCapturesForDate(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val actionsForDate = _selectedDateKey
        .flatMapLatest { key -> repository.observeActionsForDate(key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dayAnalytics: StateFlow<DayAnalyticsUiState> = combine(capturesForDate, actionsForDate) { captures, actions ->
        DayAnalyticsCalculator.compute(captures, actions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DayAnalyticsUiState())

    val pendingActions: StateFlow<List<SmartActionEntity>> = actionsForDate
        .map { list -> list.filter { it.reviewStatus == ReviewStatus.PENDING_REVIEW.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val acceptedActions: StateFlow<List<SmartActionEntity>> = actionsForDate
        .map { list -> list.filter { it.reviewStatus == ReviewStatus.ACCEPTED.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dismissedActions: StateFlow<List<SmartActionEntity>> = actionsForDate
        .map { list -> list.filter { it.reviewStatus == ReviewStatus.DISMISSED.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingCount: StateFlow<Int> = repository
        .observePendingReviewCount(DateKeyUtil.todayDateKey())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isGoogleConnected: StateFlow<Boolean> = GoogleWorkspaceSyncService.isConnected

    private val _synthesisState = MutableStateFlow<EveningSynthesisUiState>(EveningSynthesisUiState.Idle)
    val synthesisState: StateFlow<EveningSynthesisUiState> = _synthesisState.asStateFlow()

    private val _editingAction = MutableStateFlow<SmartActionEntity?>(null)
    val editingAction: StateFlow<SmartActionEntity?> = _editingAction.asStateFlow()

    private val _lastDismissed = MutableStateFlow<SmartActionEntity?>(null)
    val lastDismissed: StateFlow<SmartActionEntity?> = _lastDismissed.asStateFlow()

    private val _reviewBannerMessage = MutableStateFlow<String?>(null)
    val reviewBannerMessage: StateFlow<String?> = _reviewBannerMessage.asStateFlow()

    fun selectDate(dateKey: String) {
        _selectedDateKey.value = dateKey
        _synthesisState.value = EveningSynthesisUiState.Idle
    }

    fun synthesizeEveningReview(forceFullRebuild: Boolean = false) {
        val dateKey = _selectedDateKey.value
        viewModelScope.launch {
            _synthesisState.value = EveningSynthesisUiState.Loading
            val res = repository.synthesizeQuickReview(dateKey, forceFullRebuild = forceFullRebuild)
            res.fold(
                onSuccess = { outcome ->
                    _synthesisState.value = EveningSynthesisUiState.Success
                    _reviewBannerMessage.value = when (outcome) {
                        is QuickReviewOutcome.AlreadyUpToDate ->
                            "Already up to date — no new captures since your last Quick Review."
                        is QuickReviewOutcome.Updated ->
                            "Processed ${outcome.processedCount} new capture(s), added ${outcome.newSuggestionsCount} new suggestion(s)."
                    }
                },
                onFailure = { err ->
                    _synthesisState.value = EveningSynthesisUiState.Error(
                        err.localizedMessage ?: "Unable to run Quick Review."
                    )
                }
            )
        }
    }

    fun acceptAction(action: SmartActionEntity) {
        viewModelScope.launch {
            repository.acceptAction(action)
        }
    }

    fun syncActionToGoogle(action: SmartActionEntity) {
        viewModelScope.launch {
            val res = repository.syncActionToGoogle(action)
            res.fold(
                onSuccess = { label -> _reviewBannerMessage.value = "$label: ${action.title}" },
                onFailure = { err -> _reviewBannerMessage.value = err.localizedMessage ?: "Google sync failed." }
            )
        }
    }

    fun syncAllApprovedToGoogle() {
        val dateKey = _selectedDateKey.value
        viewModelScope.launch {
            val res = repository.syncAllApprovedToGoogle(dateKey)
            res.fold(
                onSuccess = { count ->
                    _reviewBannerMessage.value = if (count > 0) {
                        "Synced $count item(s) to Google Calendar & Tasks."
                    } else {
                        "All approved items are already synced."
                    }
                },
                onFailure = { err ->
                    _reviewBannerMessage.value = err.localizedMessage ?: "Google sync failed."
                }
            )
        }
    }

    fun notifyMessage(msg: String) {
        _reviewBannerMessage.value = msg
    }

    fun clearBannerMessage() {
        _reviewBannerMessage.value = null
    }

    fun dismissAction(action: SmartActionEntity) {
        viewModelScope.launch {
            repository.dismissAction(action)
            _lastDismissed.value = action
        }
    }

    fun undoLastDismiss() {
        val dismissed = _lastDismissed.value ?: return
        _lastDismissed.value = null
        viewModelScope.launch { repository.restoreActionToPending(dismissed) }
    }

    fun clearLastDismissed() {
        _lastDismissed.value = null
    }

    fun restoreAction(action: SmartActionEntity) {
        viewModelScope.launch { repository.restoreActionToPending(action) }
    }

    fun openEditSheet(action: SmartActionEntity) {
        _editingAction.value = action
    }

    fun openNewActionSheet() {
        _editingAction.value = SmartActionEntity(
            id = 0L,
            dateKey = _selectedDateKey.value,
            category = ActionCategory.TODO.name,
            title = "",
            notes = "",
            suggestedTimeMillis = DateKeyUtil.buildSuggestedMillis(0, 14, 0),
            priority = ActionPriority.MEDIUM.name,
            reviewStatus = ReviewStatus.PENDING_REVIEW.name
        )
    }

    fun closeEditSheet() {
        _editingAction.value = null
    }

    fun saveAndAcceptEditedAction(updated: SmartActionEntity) {
        _editingAction.value = null
        viewModelScope.launch { repository.saveAndAcceptAction(updated) }
    }

    fun toggleActionCompleted(action: SmartActionEntity) {
        viewModelScope.launch { repository.toggleActionCompleted(action) }
    }

    fun deleteAction(action: SmartActionEntity) {
        viewModelScope.launch { repository.deleteAction(action) }
    }
}
