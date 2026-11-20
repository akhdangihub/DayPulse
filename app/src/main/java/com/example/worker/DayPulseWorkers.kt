package com.example.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.TranscriptionProcessor
import com.example.data.prefs.PrivacyPreferencesStore
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

class TranscriptionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = PrivacyPreferencesStore(applicationContext).preferencesFlow.first()
        if (!TranscriptionProcessor.isNetworkAvailable(applicationContext, prefs.wifiOnlyProcessing)) {
            return Result.retry()
        }
        val allSucceeded = TranscriptionProcessor.processPendingOnce(
            context = applicationContext,
            includeFailed = false
        )
        return if (allSucceeded) Result.success() else Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "daypulse_offline_queue"

        fun isNetworkAvailable(context: Context): Boolean =
            TranscriptionProcessor.isNetworkAvailable(context, wifiOnly = false)

        fun enqueue(context: Context, wifiOnly: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .build()

            val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            // Use KEEP so an already-running worker is never cancelled mid-transcription.
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}

class ReminderNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val actionId = inputData.getLong(KEY_ACTION_ID, -1L)
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        val notes = inputData.getString(KEY_NOTES).orEmpty()

        val body = if (notes.isNotBlank()) notes else "Scheduled reminder from your DayPulse evening review."
        NotificationHelper.postNotificationSafely(
            context = applicationContext,
            notificationId = 3000 + (actionId.toInt() and 0x0FFF),
            channelId = NotificationHelper.CHANNEL_REMINDERS,
            title = title,
            body = body,
            openEveningTab = true
        )
        return Result.success()
    }

    companion object {
        private const val KEY_ACTION_ID = "action_id"
        private const val KEY_TITLE = "title"
        private const val KEY_NOTES = "notes"

        fun schedule(
            context: Context,
            actionId: Long,
            title: String,
            notes: String,
            triggerAtMillis: Long
        ) {
            val delayMillis = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(5_000L)
            val data = Data.Builder()
                .putLong(KEY_ACTION_ID, actionId)
                .putString(KEY_TITLE, title)
                .putString(KEY_NOTES, notes)
                .build()

            val request = OneTimeWorkRequestBuilder<ReminderNotificationWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "daypulse_reminder_$actionId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context, actionId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork("daypulse_reminder_$actionId")
        }
    }
}

class EveningReviewScheduleWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = PrivacyPreferencesStore(applicationContext).preferencesFlow.first()
        if (prefs.eveningReminderEnabled) {
            NotificationHelper.postNotificationSafely(
                context = applicationContext,
                notificationId = NotificationHelper.EVENING_REVIEW_NOTIFICATION_ID,
                channelId = NotificationHelper.CHANNEL_EVENING_REVIEW,
                title = "Time for a Quick Review",
                body = "Review your newly captured moments and approve tasks or calendar events.",
                openEveningTab = true
            )
            scheduleNext(applicationContext, prefs.eveningReviewHour, prefs.eveningReviewMinute)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "daypulse_daily_evening_checkin"

        fun scheduleNext(context: Context, hour24: Int, minute: Int) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour24.coerceIn(16, 23))
                set(Calendar.MINUTE, minute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (!after(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            val delayMillis = (target.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
            val request = OneTimeWorkRequestBuilder<EveningReviewScheduleWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
