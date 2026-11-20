package com.example.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.ActionCategory
import com.example.data.local.ActionPriority
import com.example.data.local.SmartActionEntity

object NotificationHelper {
    const val CHANNEL_CAPTURE = "daypulse_capture"
    const val CHANNEL_REMINDERS = "daypulse_reminders"
    const val CHANNEL_EVENING_REVIEW = "daypulse_evening_review"
    const val CHANNEL_CHUNK_TASKS = "daypulse_chunk_tasks"

    const val FGS_NOTIFICATION_ID = 1001
    const val EVENING_REVIEW_NOTIFICATION_ID = 2001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val captureChannel = NotificationChannel(
                CHANNEL_CAPTURE,
                context.getString(R.string.fgs_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.fgs_channel_desc)
                setShowBadge(false)
            }

            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.reminder_channel_desc)
            }

            val eveningChannel = NotificationChannel(
                CHANNEL_EVENING_REVIEW,
                context.getString(R.string.evening_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.evening_channel_desc)
            }

            val chunkTasksChannel = NotificationChannel(
                CHANNEL_CHUNK_TASKS,
                context.getString(R.string.chunk_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.chunk_channel_desc)
            }

            manager.createNotificationChannels(
                listOf(captureChannel, remindersChannel, eveningChannel, chunkTasksChannel)
            )
        }
    }

    fun launchAppPendingIntent(context: Context, openEveningTab: Boolean = false): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (openEveningTab) {
                putExtra("open_tab", "evening")
            }
        }
        return PendingIntent.getActivity(
            context,
            if (openEveningTab) 2 else 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun postNotificationSafely(
        context: Context,
        notificationId: Int,
        channelId: String,
        title: String,
        body: String,
        openEveningTab: Boolean = false
    ) {
        ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(launchAppPendingIntent(context, openEveningTab))
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /**
     * Sends a push notification ONLY when a recorded/analyzed chunk actually produced new tasks,
     * reminders, or calendar schedules. If [newActions] is empty, stays completely silent.
     */
    fun postChunkTasksDetected(context: Context, newActions: List<SmartActionEntity>) {
        if (newActions.isEmpty()) return
        ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val notifId = 4000 + ((System.currentTimeMillis() % 900).toInt())
        val count = newActions.size
        val title = if (count == 1) {
            "1 new item ready for approval"
        } else {
            "$count new items ready for approval"
        }

        val bodyLines = newActions.take(4).joinToString("\n") { item ->
            val prio = when (item.priority) {
                ActionPriority.HIGH.name -> "HIGH"
                ActionPriority.LOW.name -> "LOW"
                else -> "MED"
            }
            val cat = when (item.category) {
                ActionCategory.CALENDAR_EVENT.name -> "Calendar"
                ActionCategory.REMINDER.name -> "Reminder"
                else -> "Task"
            }
            "[$prio • $cat] ${item.title}"
        }

        val approveIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_APPROVE_CHUNK_TASKS
            putExtra(NotificationActionReceiver.EXTRA_ACTION_IDS, newActions.map { it.id }.toLongArray())
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
        }
        val approvePendingIntent = PendingIntent.getBroadcast(
            context,
            notifId,
            approveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val approveLabel = if (count == 1) "Approve" else "Approve All ($count)"

        val notification = NotificationCompat.Builder(context, CHANNEL_CHUNK_TASKS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(bodyLines)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyLines))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(launchAppPendingIntent(context, openEveningTab = true))
            .addAction(0, approveLabel, approvePendingIntent)
            .addAction(0, "Quick Review", launchAppPendingIntent(context, openEveningTab = true))
            .build()

        NotificationManagerCompat.from(context).notify(notifId, notification)
    }
}
