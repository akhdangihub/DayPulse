package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class CaptureType {
    AUDIO,
    TEXT_NOTE,
    PHOTO
}

enum class ProcessingStatus {
    QUEUED_OFFLINE,
    TRANSCRIBING,
    COMPLETED,
    FAILED
}

enum class ActionCategory {
    REMINDER,
    TODO,
    CALENDAR_EVENT
}

enum class ActionPriority {
    LOW,
    MEDIUM,
    HIGH
}

enum class ReviewStatus {
    PENDING_REVIEW,
    ACCEPTED,
    DISMISSED
}

@Entity(tableName = "capture_entries")
data class CaptureEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateKey: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = CaptureType.AUDIO.name,
    val localMediaPath: String? = null,
    val durationSeconds: Int = 0,
    val transcriptText: String = "",
    val aiCaption: String = "",
    val processingStatus: String = ProcessingStatus.QUEUED_OFFLINE.name,
    val errorMessage: String? = null,
    val audioDeleted: Boolean = false,
    val isBookmarked: Boolean = false,
    val includedInSummary: Boolean = false
)

@Entity(tableName = "daily_summaries")
data class DailySummaryEntity(
    @PrimaryKey val dateKey: String,
    val narrativeSummary: String,
    val highlightsSerialized: String,
    val openLoopsSerialized: String,
    val moodTag: String,
    val generatedAt: Long = System.currentTimeMillis(),
    val sourceCaptureCount: Int = 0
)

@Entity(tableName = "smart_actions")
data class SmartActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateKey: String,
    val category: String = ActionCategory.TODO.name,
    val title: String,
    val notes: String = "",
    val suggestedTimeMillis: Long? = null,
    val endTimeMillis: Long? = null,
    val priority: String = ActionPriority.MEDIUM.name,
    val sourceExcerpt: String = "",
    val reviewStatus: String = ReviewStatus.PENDING_REVIEW.name,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val syncedToGoogle: Boolean = false,
    val externalSyncNote: String = ""
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateKey: String,
    val role: String, // "user" or "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

object DateKeyUtil {
    fun todayDateKey(): String = dateKeyForMillis(System.currentTimeMillis())

    fun dateKeyForMillis(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date(millis))
    }

    fun formatDateKeyLabel(dateKey: String): String {
        if (dateKey == todayDateKey()) return "Today"
        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        if (dateKey == dateKeyForMillis(yesterdayCal.timeInMillis)) return "Yesterday"
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = parser.parse(dateKey) ?: return dateKey
            SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(date)
        } catch (_: Exception) {
            dateKey
        }
    }

    fun formatClockTime(millis: Long): String {
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
    }

    fun formatDateTimeShort(millis: Long): String {
        return SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()).format(Date(millis))
    }

    fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    fun buildSuggestedMillis(dayOffset: Int, hour24: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, dayOffset.coerceIn(0, 30))
        cal.set(Calendar.HOUR_OF_DAY, hour24.coerceIn(0, 23))
        cal.set(Calendar.MINUTE, minute.coerceIn(0, 59))
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
