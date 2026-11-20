package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.ExtractedActionSuggestion
import com.example.data.ActivityBucket
import com.example.data.DayAnalyticsCalculator
import com.example.data.JournalRepository
import com.example.data.local.ActionCategory
import com.example.data.local.ActionPriority
import com.example.data.local.AppDatabase
import com.example.data.local.CaptureEntryEntity
import com.example.data.local.CaptureType
import com.example.data.local.DateKeyUtil
import com.example.data.local.ProcessingStatus
import com.example.data.local.ReviewStatus
import com.example.data.local.SmartActionEntity
import com.example.widget.WidgetSyncHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `app_name matches DayPulse`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("DayPulse", appName)
    }

    @Test
    fun `offline capture queue and evening triage flow persist in Room`() = runTest {
        val today = DateKeyUtil.todayDateKey()
        val captureId = db.captureDao().insert(
            CaptureEntryEntity(
                dateKey = today,
                type = CaptureType.AUDIO.name,
                localMediaPath = "/tmp/seg_test.m4a",
                durationSeconds = 42,
                processingStatus = ProcessingStatus.QUEUED_OFFLINE.name
            )
        )
        assertTrue(captureId > 0)

        val pending = db.captureDao().getPendingCapturesOnce()
        assertEquals(1, pending.size)
        assertEquals(ProcessingStatus.QUEUED_OFFLINE.name, pending.first().processingStatus)

        val actionId = db.smartActionDao().insert(
            SmartActionEntity(
                dateKey = today,
                category = ActionCategory.REMINDER.name,
                title = "Call Alex back about design review",
                suggestedTimeMillis = DateKeyUtil.buildSuggestedMillis(1, 9, 30),
                reviewStatus = ReviewStatus.PENDING_REVIEW.name
            )
        )
        assertEquals(1, db.smartActionDao().observePendingReviewCount(today).first())

        val insertedAction = db.smartActionDao().getById(actionId)!!
        db.smartActionDao().update(insertedAction.copy(reviewStatus = ReviewStatus.ACCEPTED.name))

        assertEquals(0, db.smartActionDao().observePendingReviewCount(today).first())
        val accepted = db.smartActionDao().observeAllAcceptedActions().first()
        assertEquals(1, accepted.size)
        assertEquals("Call Alex back about design review", accepted.first().title)
    }

    @Test
    fun `incremental quick review marks captures summarized and prevents double counting`() = runTest {
        val today = DateKeyUtil.todayDateKey()
        val id1 = db.captureDao().insert(
            CaptureEntryEntity(
                dateKey = today,
                type = CaptureType.TEXT_NOTE.name,
                transcriptText = "Morning standup about API latency",
                processingStatus = ProcessingStatus.COMPLETED.name,
                includedInSummary = false
            )
        )
        assertEquals(1, db.captureDao().observeNewForSummaryCount(today).first())

        db.captureDao().markCapturesAsSummarized(listOf(id1))
        assertEquals(0, db.captureDao().observeNewForSummaryCount(today).first())

        // Subsequent afternoon capture should increment new count to 1 while keeping morning capture excluded
        db.captureDao().insert(
            CaptureEntryEntity(
                dateKey = today,
                type = CaptureType.TEXT_NOTE.name,
                transcriptText = "Lunch check-in: schedule dentist next Tuesday",
                processingStatus = ProcessingStatus.COMPLETED.name,
                includedInSummary = false
            )
        )
        assertEquals(1, db.captureDao().observeNewForSummaryCount(today).first())

        // Resetting for full day rebuild should make both captures eligible again
        db.captureDao().resetSummarizedForDate(today)
        assertEquals(2, db.captureDao().observeNewForSummaryCount(today).first())

        // Verify syncedToGoogle field persists cleanly
        val actionId = db.smartActionDao().insert(
            SmartActionEntity(
                dateKey = today,
                category = ActionCategory.CALENDAR_EVENT.name,
                title = "Dentist appointment",
                reviewStatus = ReviewStatus.ACCEPTED.name,
                syncedToGoogle = true,
                externalSyncNote = "Synced to Google Calendar"
            )
        )
        val saved = db.smartActionDao().getById(actionId)!!
        assertTrue(saved.syncedToGoogle)
        assertEquals("Synced to Google Calendar", saved.externalSyncNote)
    }

    @Test
    fun `chunk deduplication skips empty chunks and preserves LOW MED HIGH priority order in widget snapshot`() = runTest {
        val repo = JournalRepository(context)
        val today = DateKeyUtil.todayDateKey()

        // Chunk 1 has no tasks -> returns empty list (no notification, no widget approval items)
        val chunk1Inserted = repo.deduplicateAndInsertActions(today, emptyList())
        assertTrue(chunk1Inserted.isEmpty())

        // Chunk 2 yields LOW and HIGH tasks plus duplicate
        val chunk2Suggestions = listOf(
            ExtractedActionSuggestion(
                category = ActionCategory.TODO,
                title = "Read optional design article",
                notes = "",
                suggestedTimeMillis = DateKeyUtil.buildSuggestedMillis(0, 18, 0),
                endTimeMillis = null,
                priority = ActionPriority.LOW,
                sourceExcerpt = "Read article later"
            ),
            ExtractedActionSuggestion(
                category = ActionCategory.CALENDAR_EVENT,
                title = "Client demo meeting",
                notes = "Main room",
                suggestedTimeMillis = DateKeyUtil.buildSuggestedMillis(0, 15, 0),
                endTimeMillis = DateKeyUtil.buildSuggestedMillis(0, 15, 30),
                priority = ActionPriority.HIGH,
                sourceExcerpt = "Demo at 3 PM"
            ),
            ExtractedActionSuggestion(
                category = ActionCategory.CALENDAR_EVENT,
                title = "Client demo meeting",
                notes = "Duplicate",
                suggestedTimeMillis = DateKeyUtil.buildSuggestedMillis(0, 15, 0),
                endTimeMillis = DateKeyUtil.buildSuggestedMillis(0, 15, 30),
                priority = ActionPriority.HIGH,
                sourceExcerpt = "Demo at 3 PM"
            )
        )
        val chunk2Inserted = repo.deduplicateAndInsertActions(today, chunk2Suggestions)
        assertEquals(2, chunk2Inserted.size)

        val snapshot = WidgetSyncHelper.loadSnapshot(context)
        assertEquals(2, snapshot.pendingActions.size)
        // HIGH priority must sort ahead of LOW priority
        assertEquals(ActionPriority.HIGH.name, snapshot.pendingActions[0].priority)
        assertEquals("Client demo meeting", snapshot.pendingActions[0].title)
        assertEquals(ActionPriority.LOW.name, snapshot.pendingActions[1].priority)
        assertEquals(ActionCategory.CALENDAR_EVENT.name, snapshot.nextCalendarEvent?.category)
    }

    @Test
    fun `day analytics aggregates audio chunk durations calendar schedules and task completion`() {
        val today = DateKeyUtil.todayDateKey()
        val captures = listOf(
            CaptureEntryEntity(
                dateKey = today,
                type = CaptureType.AUDIO.name,
                durationSeconds = 900, // 15m
                transcriptText = "Deep coding focus session on payment refactor",
                processingStatus = ProcessingStatus.COMPLETED.name
            ),
            CaptureEntryEntity(
                dateKey = today,
                type = CaptureType.AUDIO.name,
                durationSeconds = 600, // 10m
                transcriptText = "Evening reflection about how the day felt",
                processingStatus = ProcessingStatus.COMPLETED.name
            ),
            CaptureEntryEntity(
                dateKey = today,
                type = CaptureType.TEXT_NOTE.name,
                transcriptText = "Quick text note with zero audio seconds",
                processingStatus = ProcessingStatus.COMPLETED.name
            )
        )

        val meetingStart = DateKeyUtil.buildSuggestedMillis(0, 10, 0)
        val meetingEnd = DateKeyUtil.buildSuggestedMillis(0, 10, 45) // 45m
        val actions = listOf(
            SmartActionEntity(
                dateKey = today,
                category = ActionCategory.CALENDAR_EVENT.name,
                title = "Client demo sync",
                suggestedTimeMillis = meetingStart,
                endTimeMillis = meetingEnd,
                priority = ActionPriority.HIGH.name,
                reviewStatus = ReviewStatus.ACCEPTED.name,
                isCompleted = true
            ),
            SmartActionEntity(
                dateKey = today,
                category = ActionCategory.CALENDAR_EVENT.name,
                title = "Dentist checkup",
                suggestedTimeMillis = DateKeyUtil.buildSuggestedMillis(0, 16, 0),
                endTimeMillis = null, // defaults to 30m
                priority = ActionPriority.MEDIUM.name,
                reviewStatus = ReviewStatus.PENDING_REVIEW.name,
                isCompleted = false
            ),
            SmartActionEntity(
                dateKey = today,
                category = ActionCategory.TODO.name,
                title = "Ship release notes",
                priority = ActionPriority.HIGH.name,
                reviewStatus = ReviewStatus.ACCEPTED.name,
                isCompleted = false
            ),
            SmartActionEntity(
                dateKey = today,
                category = ActionCategory.TODO.name,
                title = "Dismissed noisy task",
                priority = ActionPriority.LOW.name,
                reviewStatus = ReviewStatus.DISMISSED.name,
                isCompleted = false
            )
        )

        val state = DayAnalyticsCalculator.compute(captures, actions)

        // Audio = 15m + 10m = 25m; Calendar = 45m + 30m = 75m; Total = 100m
        assertEquals(25, state.audioCaptureMinutes)
        assertEquals(75, state.calendarScheduleMinutes)
        assertEquals(100, state.totalTrackedMinutes)
        assertEquals(4, state.activitySlices.size)
        assertEquals(100, state.activitySlices.sumOf { it.minutes })
        assertEquals(100, state.activitySlices.sumOf { it.sharePercent })
        assertEquals(ActivityBucket.MEETINGS_CALENDAR, state.activitySlices.first().bucket)
        assertEquals(45, state.activitySlices.first().minutes)

        // Tasks exclude DISMISSED -> 3 active tasks (1 completed, 1 approved active, 1 pending review)
        assertEquals(3, state.totalTasks)
        assertEquals(1, state.completedTasks)
        assertEquals(1, state.approvedActiveTasks)
        assertEquals(1, state.pendingReviewTasks)
        assertEquals(33, state.completionRatePercent)

        val highStat = state.priorityStats.first { it.priority == ActionPriority.HIGH }
        assertEquals(1, highStat.completed)
        assertEquals(2, highStat.total)
    }

    @Test
    fun `theme mode glass toggle and recording saving chunk state behave properly`() = runTest {
        val prefsStore = com.example.data.prefs.PrivacyPreferencesStore(context)
        val initial = prefsStore.preferencesFlow.first()
        assertEquals(com.example.data.prefs.AppThemeMode.SYSTEM, initial.resolvedThemeMode())
        assertTrue(initial.glassEffectsEnabled)

        prefsStore.setThemeMode(com.example.data.prefs.AppThemeMode.DARK)
        prefsStore.setGlassEffectsEnabled(false)
        val updated = prefsStore.preferencesFlow.first()
        assertEquals(com.example.data.prefs.AppThemeMode.DARK, updated.resolvedThemeMode())
        assertEquals(false, updated.glassEffectsEnabled)

        val savingState = com.example.service.RecordingServiceState(
            status = com.example.service.CaptureServiceStatus.RECORDING,
            isSavingChunk = true
        )
        assertTrue(savingState.isSavingChunk)
    }
}
