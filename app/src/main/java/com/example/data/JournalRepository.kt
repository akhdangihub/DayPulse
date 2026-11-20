package com.example.data

import android.content.Context
import android.net.Uri
import com.example.ai.ExtractedActionSuggestion
import com.example.ai.GeminiAiService
import com.example.data.local.ActionCategory
import com.example.data.local.AppDatabase
import com.example.data.local.CaptureEntryEntity
import com.example.data.local.CaptureType
import com.example.data.local.ChatMessageEntity
import com.example.data.local.DailySummaryEntity
import com.example.data.local.DateKeyUtil
import com.example.data.local.ProcessingStatus
import com.example.data.local.ReviewStatus
import com.example.data.local.SmartActionEntity
import com.example.data.prefs.AppThemeMode
import com.example.data.prefs.PrivacyPreferences
import com.example.data.prefs.PrivacyPreferencesStore
import com.example.widget.WidgetSyncHelper
import com.example.worker.EveningReviewScheduleWorker
import com.example.worker.ReminderNotificationWorker
import com.example.worker.TranscriptionWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

sealed interface QuickReviewOutcome {
    data class Updated(val processedCount: Int, val newSuggestionsCount: Int) : QuickReviewOutcome
    data object AlreadyUpToDate : QuickReviewOutcome
}

class JournalRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val captureDao = db.captureDao()
    private val summaryDao = db.summaryDao()
    private val actionDao = db.smartActionDao()
    private val chatDao = db.chatMessageDao()
    private val prefsStore = PrivacyPreferencesStore(context)
    private val aiService = GeminiAiService()

    val preferencesFlow: Flow<PrivacyPreferences> = prefsStore.preferencesFlow
    val queuedCountFlow: Flow<Int> = captureDao.observeQueuedCount()
    val distinctDatesFlow: Flow<List<String>> = captureDao.observeDistinctDateKeys()
    val allSummariesFlow: Flow<List<DailySummaryEntity>> = summaryDao.observeAllSummaries()
    val allAcceptedActionsFlow: Flow<List<SmartActionEntity>> = actionDao.observeAllAcceptedActions()

    fun observeCapturesForDate(dateKey: String): Flow<List<CaptureEntryEntity>> =
        captureDao.observeCapturesForDate(dateKey)

    fun observeNewForSummaryCount(dateKey: String): Flow<Int> =
        captureDao.observeNewForSummaryCount(dateKey)

    fun observeSummaryForDate(dateKey: String): Flow<DailySummaryEntity?> =
        summaryDao.observeSummaryForDate(dateKey)

    fun observeActionsForDate(dateKey: String): Flow<List<SmartActionEntity>> =
        actionDao.observeActionsForDate(dateKey)

    fun observePendingReviewCount(dateKey: String): Flow<Int> =
        actionDao.observePendingReviewCount(dateKey)

    fun observeChatForDate(dateKey: String): Flow<List<ChatMessageEntity>> =
        chatDao.observeMessagesForDate(dateKey)

    fun searchCaptures(query: String): Flow<List<CaptureEntryEntity>> =
        captureDao.searchCaptures(query.trim())

    suspend fun addOfflineTextNote(text: String, dateKey: String = DateKeyUtil.todayDateKey()) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val title = clean.lineSequence().firstOrNull()?.take(48)?.ifBlank { "Quick Note" } ?: "Quick Note"
        captureDao.insert(
            CaptureEntryEntity(
                dateKey = dateKey,
                timestamp = System.currentTimeMillis(),
                type = CaptureType.TEXT_NOTE.name,
                transcriptText = clean,
                aiCaption = title,
                processingStatus = ProcessingStatus.COMPLETED.name
            )
        )
        WidgetSyncHelper.updateAllWidgets(context)
    }

    suspend fun importPhotoAndQueue(uri: Uri, dateKey: String = DateKeyUtil.todayDateKey()): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
                val dest = File(photosDir, "photo_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Unable to open selected photo.")

                captureDao.insert(
                    CaptureEntryEntity(
                        dateKey = dateKey,
                        timestamp = System.currentTimeMillis(),
                        type = CaptureType.PHOTO.name,
                        localMediaPath = dest.absolutePath,
                        aiCaption = "Photo captured — queued for Gemini analysis",
                        processingStatus = ProcessingStatus.QUEUED_OFFLINE.name
                    )
                )
                val prefs = prefsStore.preferencesFlow.first()
                if (TranscriptionProcessor.isNetworkAvailable(context, prefs.wifiOnlyProcessing)) {
                    TranscriptionProcessor.triggerAsync(context, includeFailed = false)
                }
                TranscriptionWorker.enqueue(context, prefs.wifiOnlyProcessing)
            }
        }

    suspend fun triggerOfflineQueueSync() {
        val prefs = prefsStore.preferencesFlow.first()
        if (TranscriptionProcessor.isNetworkAvailable(context, prefs.wifiOnlyProcessing)) {
            TranscriptionProcessor.triggerAsync(context, includeFailed = true)
        }
        TranscriptionWorker.enqueue(context, prefs.wifiOnlyProcessing)
    }

    fun recoverStuckTranscriptions() {
        TranscriptionProcessor.recoverStuckAndTrigger(context)
    }

    fun retryCapture(entry: CaptureEntryEntity) {
        TranscriptionProcessor.triggerSingleAsync(context, entry.id)
    }

    suspend fun updateCaptureTranscript(entry: CaptureEntryEntity, newTranscript: String) {
        val clean = newTranscript.trim()
        val firstLine = clean.lineSequence().firstOrNull()?.take(48).orEmpty()
        captureDao.update(
            entry.copy(
                transcriptText = clean,
                aiCaption = if (entry.aiCaption.isBlank() || entry.aiCaption.startsWith("Voice segment")) {
                    firstLine.ifBlank { entry.aiCaption }
                } else {
                    entry.aiCaption
                },
                processingStatus = ProcessingStatus.COMPLETED.name,
                errorMessage = null
            )
        )
    }

    suspend fun toggleBookmark(entry: CaptureEntryEntity) {
        captureDao.update(entry.copy(isBookmarked = !entry.isBookmarked))
    }

    suspend fun deleteCapture(entry: CaptureEntryEntity) = withContext(Dispatchers.IO) {
        entry.localMediaPath?.let { runCatching { File(it).delete() } }
        captureDao.deleteById(entry.id)
        WidgetSyncHelper.updateAllWidgets(context)
    }

    suspend fun synthesizeQuickReview(
        dateKey: String,
        forceFullRebuild: Boolean = false
    ): Result<QuickReviewOutcome> {
        if (forceFullRebuild) {
            captureDao.resetSummarizedForDate(dateKey)
        }

        val captures = captureDao.getCapturesForDateOnce(dateKey)
        if (captures.isEmpty()) {
            return Result.failure(IllegalStateException("Capture at least one voice memo, note, or photo first."))
        }

        val readyCaptures = captures.filter {
            it.transcriptText.isNotBlank() || it.type == CaptureType.TEXT_NOTE.name
        }
        if (readyCaptures.isEmpty()) {
            val hasQueued = captures.any {
                it.processingStatus == ProcessingStatus.QUEUED_OFFLINE.name ||
                    it.processingStatus == ProcessingStatus.TRANSCRIBING.name
            }
            return Result.failure(
                IllegalStateException(
                    if (hasQueued) {
                        "Your recordings are still transcribing. Wait a moment or tap Retry."
                    } else {
                        "No transcribed captures found yet for $dateKey."
                    }
                )
            )
        }

        val newCaptures = if (forceFullRebuild) {
            readyCaptures
        } else {
            readyCaptures.filter { !it.includedInSummary }
        }

        val existingSummary = if (forceFullRebuild) null else summaryDao.getSummaryForDateOnce(dateKey)
        if (!forceFullRebuild && newCaptures.isEmpty() && existingSummary != null) {
            return Result.success(QuickReviewOutcome.AlreadyUpToDate)
        }

        if (!TranscriptionProcessor.isNetworkAvailable(context, wifiOnly = false)) {
            return Result.failure(
                IllegalStateException("You are currently offline. Reconnect to run Quick Review with Gemini.")
            )
        }

        val existingActions = actionDao.getActionsForDateOnce(dateKey)
        val existingActionTitles = existingActions.map { it.title }

        val result = aiService.synthesizeQuickReview(
            dateKey = dateKey,
            newCaptures = newCaptures,
            existingSummary = existingSummary,
            existingActionTitles = existingActionTitles
        )

        return result.map { synthesis ->
            val mergedHighlights = buildList {
                existingSummary?.highlightsSerialized?.lines()?.filter { it.isNotBlank() }?.forEach { add(it) }
                synthesis.highlights.forEach { hl ->
                    if (none { normalizeText(it) == normalizeText(hl) }) add(hl)
                }
            }
            val mergedLoops = buildList {
                existingSummary?.openLoopsSerialized?.lines()?.filter { it.isNotBlank() }?.forEach { add(it) }
                synthesis.openLoops.forEach { loop ->
                    if (none { normalizeText(it) == normalizeText(loop) }) add(loop)
                }
            }

            summaryDao.upsert(
                DailySummaryEntity(
                    dateKey = dateKey,
                    narrativeSummary = synthesis.narrativeSummary,
                    highlightsSerialized = mergedHighlights.joinToString("\n"),
                    openLoopsSerialized = mergedLoops.joinToString("\n"),
                    moodTag = synthesis.moodTag,
                    generatedAt = System.currentTimeMillis(),
                    sourceCaptureCount = readyCaptures.size
                )
            )

            val inserted = deduplicateAndInsertActions(dateKey, synthesis.suggestedActions)
            captureDao.markCapturesAsSummarized(newCaptures.map { it.id })
            WidgetSyncHelper.updateAllWidgets(context)
            QuickReviewOutcome.Updated(
                processedCount = newCaptures.size,
                newSuggestionsCount = inserted.size
            )
        }
    }

    suspend fun deduplicateAndInsertActions(
        dateKey: String,
        suggestions: List<ExtractedActionSuggestion>
    ): List<SmartActionEntity> {
        if (suggestions.isEmpty()) return emptyList()
        val existingActionTitles = actionDao.getActionsForDateOnce(dateKey).map { it.title }
        val existingNormalized = existingActionTitles.map { normalizeText(it) }.toMutableSet()
        val newEntities = mutableListOf<SmartActionEntity>()
        for (suggestion in suggestions) {
            val norm = normalizeText(suggestion.title)
            if (norm.isBlank() || isDuplicateTitle(norm, existingNormalized)) continue
            existingNormalized.add(norm)
            newEntities.add(
                SmartActionEntity(
                    dateKey = dateKey,
                    category = suggestion.category.name,
                    title = suggestion.title,
                    notes = suggestion.notes,
                    suggestedTimeMillis = suggestion.suggestedTimeMillis,
                    endTimeMillis = suggestion.endTimeMillis,
                    priority = suggestion.priority.name,
                    sourceExcerpt = suggestion.sourceExcerpt,
                    reviewStatus = ReviewStatus.PENDING_REVIEW.name
                )
            )
        }
        if (newEntities.isEmpty()) return emptyList()
        val ids = actionDao.insertAll(newEntities)
        return newEntities.mapIndexed { index, entity ->
            entity.copy(id = ids.getOrElse(index) { entity.id })
        }
    }

    private fun normalizeText(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isDuplicateTitle(candidate: String, existingSet: Set<String>): Boolean {
        if (existingSet.contains(candidate)) return true
        val candWords = candidate.split(" ").filter { it.length > 2 }.toSet()
        if (candWords.isEmpty()) return false
        return existingSet.any { existing ->
            if (existing.contains(candidate) || candidate.contains(existing)) return@any true
            val exWords = existing.split(" ").filter { it.length > 2 }.toSet()
            if (exWords.isEmpty()) return@any false
            val overlap = candWords.intersect(exWords).size.toFloat() / maxOf(candWords.size, exWords.size)
            overlap >= 0.75f
        }
    }

    suspend fun acceptAction(action: SmartActionEntity): SmartActionEntity {
        val updated = action.copy(reviewStatus = ReviewStatus.ACCEPTED.name)
        actionDao.update(updated)
        if (updated.category == ActionCategory.REMINDER.name && updated.suggestedTimeMillis != null) {
            ReminderNotificationWorker.schedule(
                context = context,
                actionId = updated.id,
                title = updated.title,
                notes = updated.notes,
                triggerAtMillis = updated.suggestedTimeMillis
            )
        }
        // Automatically sync to Google Calendar / Tasks if already authorized!
        if (GoogleWorkspaceSyncService.isConnected.value && !updated.syncedToGoogle) {
            syncActionToGoogle(updated)
        }
        WidgetSyncHelper.updateAllWidgets(context)
        return updated
    }

    suspend fun syncActionToGoogle(action: SmartActionEntity): Result<String> {
        if (action.syncedToGoogle) {
            return Result.success(action.externalSyncNote.ifBlank { "Already synced to Google" })
        }
        val res = GoogleWorkspaceSyncService.syncAction(action)
        res.onSuccess { syncLabel ->
            actionDao.update(
                action.copy(
                    syncedToGoogle = true,
                    externalSyncNote = syncLabel
                )
            )
            WidgetSyncHelper.updateAllWidgets(context)
        }
        return res
    }

    suspend fun syncAllApprovedToGoogle(dateKey: String): Result<Int> {
        val acceptedUnsynced = actionDao.getActionsForDateOnce(dateKey).filter {
            it.reviewStatus == ReviewStatus.ACCEPTED.name && !it.syncedToGoogle
        }
        if (acceptedUnsynced.isEmpty()) return Result.success(0)

        var syncedCount = 0
        var lastError: Throwable? = null
        for (item in acceptedUnsynced) {
            val res = syncActionToGoogle(item)
            res.onSuccess { syncedCount++ }
                .onFailure { lastError = it }
        }
        return if (syncedCount > 0 || lastError == null) {
            Result.success(syncedCount)
        } else {
            Result.failure(lastError ?: IllegalStateException("Sync failed."))
        }
    }

    suspend fun saveAndAcceptAction(updated: SmartActionEntity) {
        val accepted = updated.copy(reviewStatus = ReviewStatus.ACCEPTED.name)
        val savedId = if (accepted.id == 0L) {
            actionDao.insert(accepted)
        } else {
            actionDao.update(accepted)
            accepted.id
        }
        val persisted = accepted.copy(id = savedId)
        if (persisted.category == ActionCategory.REMINDER.name && persisted.suggestedTimeMillis != null) {
            ReminderNotificationWorker.schedule(
                context = context,
                actionId = savedId,
                title = persisted.title,
                notes = persisted.notes,
                triggerAtMillis = persisted.suggestedTimeMillis
            )
        } else {
            ReminderNotificationWorker.cancel(context, savedId)
        }
        if (GoogleWorkspaceSyncService.isConnected.value && !persisted.syncedToGoogle) {
            syncActionToGoogle(persisted)
        }
        WidgetSyncHelper.updateAllWidgets(context)
    }

    suspend fun dismissAction(action: SmartActionEntity) {
        actionDao.update(action.copy(reviewStatus = ReviewStatus.DISMISSED.name))
        ReminderNotificationWorker.cancel(context, action.id)
        WidgetSyncHelper.updateAllWidgets(context)
    }

    suspend fun restoreActionToPending(action: SmartActionEntity) {
        actionDao.update(action.copy(reviewStatus = ReviewStatus.PENDING_REVIEW.name))
        WidgetSyncHelper.updateAllWidgets(context)
    }

    suspend fun toggleActionCompleted(action: SmartActionEntity) {
        actionDao.update(action.copy(isCompleted = !action.isCompleted))
        WidgetSyncHelper.updateAllWidgets(context)
    }

    suspend fun deleteAction(action: SmartActionEntity) {
        ReminderNotificationWorker.cancel(context, action.id)
        actionDao.deleteById(action.id)
        WidgetSyncHelper.updateAllWidgets(context)
    }

    suspend fun buildDayContextForChat(dateKey: String): String {
        val summary = summaryDao.getSummaryForDateOnce(dateKey)
        val captures = captureDao.getCapturesForDateOnce(dateKey)
        val sb = StringBuilder()
        if (summary != null) {
            sb.appendLine("Daily Summary (${summary.moodTag}):")
            sb.appendLine(summary.narrativeSummary)
            if (summary.highlightsSerialized.isNotBlank()) {
                sb.appendLine("Highlights:\n${summary.highlightsSerialized}")
            }
            if (summary.openLoopsSerialized.isNotBlank()) {
                sb.appendLine("Open Loops:\n${summary.openLoopsSerialized}")
            }
        }
        if (captures.isNotEmpty()) {
            sb.appendLine("\nTimeline:")
            captures.forEach { entry ->
                val text = entry.transcriptText.ifBlank { entry.aiCaption }
                if (text.isNotBlank()) {
                    sb.appendLine("- [${DateKeyUtil.formatClockTime(entry.timestamp)}] $text")
                }
            }
        }
        return sb.toString().ifBlank { "No captures recorded yet for $dateKey." }
    }

    suspend fun getChatHistoryOnce(dateKey: String): List<ChatMessageEntity> =
        chatDao.getMessagesForDateOnce(dateKey)

    suspend fun saveChatMessage(dateKey: String, role: String, text: String) {
        chatDao.insert(ChatMessageEntity(dateKey = dateKey, role = role, text = text))
    }

    suspend fun clearChatForDate(dateKey: String) {
        chatDao.clearForDate(dateKey)
    }

    fun streamCompanionResponse(
        dateKey: String,
        dayContext: String,
        history: List<Pair<String, String>>,
        userMessage: String
    ): Flow<String> = aiService.streamCompanionChat(dateKey, dayContext, history, userMessage)

    suspend fun setAutoDeleteAudio(enabled: Boolean) = prefsStore.setAutoDeleteAudio(enabled)
    suspend fun setSegmentDurationMinutes(minutes: Int) = prefsStore.setSegmentDurationMinutes(minutes)
    suspend fun setWifiOnlyProcessing(enabled: Boolean) = prefsStore.setWifiOnlyProcessing(enabled)
    suspend fun setThemeMode(mode: AppThemeMode) = prefsStore.setThemeMode(mode)
    suspend fun setGlassEffectsEnabled(enabled: Boolean) = prefsStore.setGlassEffectsEnabled(enabled)
    suspend fun setEveningReminder(enabled: Boolean, hour: Int, minute: Int) {
        prefsStore.setEveningReminder(enabled, hour, minute)
        if (enabled) {
            EveningReviewScheduleWorker.scheduleNext(context, hour, minute)
        } else {
            EveningReviewScheduleWorker.cancel(context)
        }
    }

    suspend fun deleteAllLocalAudioFiles(): Int = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "recordings")
        var count = 0
        dir.listFiles()?.forEach { file ->
            if (file.delete()) count++
        }
        count
    }
}
