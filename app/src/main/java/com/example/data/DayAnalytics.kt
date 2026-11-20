package com.example.data

import com.example.data.local.ActionCategory
import com.example.data.local.ActionPriority
import com.example.data.local.CaptureEntryEntity
import com.example.data.local.CaptureType
import com.example.data.local.ReviewStatus
import com.example.data.local.SmartActionEntity
import java.util.Locale
import kotlin.math.roundToInt

enum class ActivityBucket(val label: String) {
    MEETINGS_CALENDAR("Meetings & Calls"),
    DEEP_WORK("Deep Work & Tasks"),
    VOICE_REFLECTION("Voice Reflections"),
    PERSONAL_WELLNESS("Personal & Wellness")
}

data class ActivityTimeSlice(
    val bucket: ActivityBucket,
    val minutes: Int,
    val sharePercent: Int
)

data class PriorityCompletionStat(
    val priority: ActionPriority,
    val label: String,
    val completed: Int,
    val total: Int
)

data class StatusBarStat(
    val key: String,
    val label: String,
    val count: Int
)

data class DayAnalyticsUiState(
    val totalTasks: Int = 0,
    val completedTasks: Int = 0,
    val approvedActiveTasks: Int = 0,
    val pendingReviewTasks: Int = 0,
    val completionRatePercent: Int = 0,
    val totalTrackedMinutes: Int = 0,
    val audioCaptureMinutes: Int = 0,
    val calendarScheduleMinutes: Int = 0,
    val activitySlices: List<ActivityTimeSlice> = emptyList(),
    val priorityStats: List<PriorityCompletionStat> = emptyList(),
    val statusBars: List<StatusBarStat> = emptyList(),
    val hasAnyActivityOrTasks: Boolean = false
)

object DayAnalyticsCalculator {
    private const val DEFAULT_CALENDAR_DURATION_SECONDS = 30 * 60L
    private const val MIN_CALENDAR_DURATION_SECONDS = 5 * 60L
    private const val MAX_CALENDAR_DURATION_SECONDS = 12 * 60 * 60L

    private val meetingKeywords = listOf(
        "meeting", "standup", "sync", "call", "client", "demo", "interview",
        "presentation", "zoom", "meet", "1:1", "huddle", "rapat", "diskusi",
        "ketemu", "telepon", "presentasi"
    )

    private val deepWorkKeywords = listOf(
        "code", "coding", "build", "design", "write", "draft", "review", "report",
        "sprint", "project", "deploy", "fix", "debug", "research", "study", "plan",
        "kerja", "tugas", "proyek", "laporan", "riset", "belajar", "fokus", "kirim"
    )

    private val personalKeywords = listOf(
        "gym", "workout", "run", "walk", "lunch", "dinner", "breakfast", "coffee",
        "doctor", "dentist", "family", "shop", "grocery", "health", "meditate", "rest",
        "olahraga", "makan", "istirahat", "dokter", "keluarga", "belanja", "tidur", "kesehatan"
    )

    fun compute(
        captures: List<CaptureEntryEntity>,
        actions: List<SmartActionEntity>
    ): DayAnalyticsUiState {
        // 1. Task completion & priority metrics (all non-dismissed actions for the selected day)
        val activePool = actions.filter { it.reviewStatus != ReviewStatus.DISMISSED.name }
        val totalTasks = activePool.size
        val completedTasks = activePool.count { it.isCompleted }
        val approvedActiveTasks = activePool.count {
            it.reviewStatus == ReviewStatus.ACCEPTED.name && !it.isCompleted
        }
        val pendingReviewTasks = activePool.count {
            it.reviewStatus == ReviewStatus.PENDING_REVIEW.name && !it.isCompleted
        }
        val completionRatePercent = if (totalTasks > 0) {
            ((completedTasks * 100f) / totalTasks).roundToInt().coerceIn(0, 100)
        } else {
            0
        }

        val priorityStats = listOf(
            ActionPriority.HIGH to "HIGH",
            ActionPriority.MEDIUM to "MED",
            ActionPriority.LOW to "LOW"
        ).map { (priority, label) ->
            val matching = activePool.filter { parsePriority(it.priority) == priority }
            PriorityCompletionStat(
                priority = priority,
                label = label,
                completed = matching.count { it.isCompleted },
                total = matching.size
            )
        }

        val statusBars = listOf(
            StatusBarStat(key = "completed", label = "Done", count = completedTasks),
            StatusBarStat(key = "approved", label = "Approved", count = approvedActiveTasks),
            StatusBarStat(key = "pending", label = "Pending", count = pendingReviewTasks)
        )

        // 2. Hybrid Activity Time: Audio chunk durations + Calendar schedules
        val bucketSeconds = linkedMapOf(
            ActivityBucket.MEETINGS_CALENDAR to 0L,
            ActivityBucket.DEEP_WORK to 0L,
            ActivityBucket.VOICE_REFLECTION to 0L,
            ActivityBucket.PERSONAL_WELLNESS to 0L
        )
        var rawAudioSeconds = 0L
        var rawCalendarSeconds = 0L

        captures.forEach { entry ->
            if (entry.type == CaptureType.AUDIO.name && entry.durationSeconds > 0) {
                val secs = entry.durationSeconds.toLong()
                rawAudioSeconds += secs
                val bucket = classifyAudioCapture(entry)
                bucketSeconds[bucket] = (bucketSeconds[bucket] ?: 0L) + secs
            }
        }

        activePool.forEach { action ->
            if (action.category == ActionCategory.CALENDAR_EVENT.name) {
                val secs = calendarEventDurationSeconds(action)
                rawCalendarSeconds += secs
                val bucket = classifyCalendarAction(action)
                bucketSeconds[bucket] = (bucketSeconds[bucket] ?: 0L) + secs
            }
        }

        val sliceMinutesMap = bucketSeconds.mapValues { (_, secs) ->
            secondsToRoundedMinutes(secs)
        }
        val totalTrackedMinutes = sliceMinutesMap.values.sum()

        val nonZeroEntries = sliceMinutesMap.entries.filter { it.value > 0 }
        val activitySlices = if (totalTrackedMinutes > 0 && nonZeroEntries.isNotEmpty()) {
            val rawSlices = nonZeroEntries.map { (bucket, mins) ->
                ActivityTimeSlice(
                    bucket = bucket,
                    minutes = mins,
                    sharePercent = ((mins * 100f) / totalTrackedMinutes).roundToInt().coerceAtLeast(1)
                )
            }.sortedByDescending { it.minutes }

            // Normalize rounded percentages so multi-slice totals read cleanly to 100%
            val sumPct = rawSlices.sumOf { it.sharePercent }
            if (rawSlices.isNotEmpty() && sumPct != 100) {
                val delta = 100 - sumPct
                val first = rawSlices.first()
                listOf(first.copy(sharePercent = (first.sharePercent + delta).coerceIn(1, 100))) +
                    rawSlices.drop(1)
            } else {
                rawSlices
            }
        } else {
            emptyList()
        }

        return DayAnalyticsUiState(
            totalTasks = totalTasks,
            completedTasks = completedTasks,
            approvedActiveTasks = approvedActiveTasks,
            pendingReviewTasks = pendingReviewTasks,
            completionRatePercent = completionRatePercent,
            totalTrackedMinutes = totalTrackedMinutes,
            audioCaptureMinutes = secondsToRoundedMinutes(rawAudioSeconds),
            calendarScheduleMinutes = secondsToRoundedMinutes(rawCalendarSeconds),
            activitySlices = activitySlices,
            priorityStats = priorityStats,
            statusBars = statusBars,
            hasAnyActivityOrTasks = captures.isNotEmpty() || activePool.isNotEmpty()
        )
    }

    fun formatMinutesCompact(totalMinutes: Int): String {
        if (totalMinutes <= 0) return "0m"
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    private fun secondsToRoundedMinutes(seconds: Long): Int {
        if (seconds <= 0L) return 0
        return (seconds / 60.0).roundToInt().coerceAtLeast(1)
    }

    private fun calendarEventDurationSeconds(action: SmartActionEntity): Long {
        val start = action.suggestedTimeMillis
        val end = action.endTimeMillis
        return if (start != null && end != null && end > start) {
            ((end - start) / 1000L).coerceIn(MIN_CALENDAR_DURATION_SECONDS, MAX_CALENDAR_DURATION_SECONDS)
        } else {
            DEFAULT_CALENDAR_DURATION_SECONDS
        }
    }

    private fun classifyAudioCapture(entry: CaptureEntryEntity): ActivityBucket {
        val combined = "${entry.aiCaption} ${entry.transcriptText}".lowercase(Locale.getDefault())
        return when {
            meetingKeywords.any { combined.contains(it) } -> ActivityBucket.MEETINGS_CALENDAR
            personalKeywords.any { combined.contains(it) } -> ActivityBucket.PERSONAL_WELLNESS
            deepWorkKeywords.any { combined.contains(it) } -> ActivityBucket.DEEP_WORK
            else -> ActivityBucket.VOICE_REFLECTION
        }
    }

    private fun classifyCalendarAction(action: SmartActionEntity): ActivityBucket {
        val combined = "${action.title} ${action.notes}".lowercase(Locale.getDefault())
        return when {
            personalKeywords.any { combined.contains(it) } -> ActivityBucket.PERSONAL_WELLNESS
            deepWorkKeywords.any { combined.contains(it) } &&
                !meetingKeywords.any { combined.contains(it) } -> ActivityBucket.DEEP_WORK
            else -> ActivityBucket.MEETINGS_CALENDAR
        }
    }

    private fun parsePriority(raw: String): ActionPriority =
        ActionPriority.entries.firstOrNull { it.name == raw } ?: ActionPriority.MEDIUM
}
