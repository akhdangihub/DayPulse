package com.example.widget

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import androidx.glance.material3.ColorProviders
import com.example.MainActivity
import com.example.data.GoogleWorkspaceSyncService
import com.example.data.JournalRepository
import com.example.data.local.ActionCategory
import com.example.data.local.ActionPriority
import com.example.data.local.AppDatabase
import com.example.data.local.CaptureType
import com.example.data.local.DateKeyUtil
import com.example.data.local.ReviewStatus
import com.example.data.local.SmartActionEntity
import com.example.service.AudioCaptureService
import com.example.service.CaptureServiceStatus
import com.example.ui.theme.DarkColorScheme
import com.example.ui.theme.LightColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object DayPulseGlanceColorScheme {
    val colors = ColorProviders(
        light = LightColorScheme,
        dark = DarkColorScheme
    )
}

object WidgetCommands {
    const val EXTRA_WIDGET_COMMAND = "widget_command"
    const val CMD_START_CAPTURE = "start_capture"
    const val CMD_QUICK_REVIEW = "quick_review"
    const val CMD_QUICK_NOTE = "quick_note"

    fun createLaunchIntent(context: Context, command: String): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = "com.example.widget.LAUNCH_$command"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_WIDGET_COMMAND, command)
            if (command == CMD_QUICK_REVIEW) {
                putExtra("open_tab", "evening")
            }
        }
    }
}

data class WidgetSnapshot(
    val recordingStatus: CaptureServiceStatus,
    val todayCapturesCount: Int,
    val newReadyForReviewCount: Int,
    val pendingActions: List<SmartActionEntity>,
    val approvedActions: List<SmartActionEntity>,
    val nextCalendarEvent: SmartActionEntity?,
    val isGoogleConnected: Boolean
)

object WidgetSyncHelper {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun loadSnapshot(context: Context): WidgetSnapshot {
        val db = AppDatabase.getInstance(context.applicationContext)
        val today = DateKeyUtil.todayDateKey()
        val captures = db.captureDao().getCapturesForDateOnce(today)
        val actions = db.smartActionDao().getActionsForDateOnce(today)

        val priorityRank: (SmartActionEntity) -> Int = {
            when (it.priority) {
                ActionPriority.HIGH.name -> 0
                ActionPriority.MEDIUM.name -> 1
                else -> 2
            }
        }

        val pending = actions
            .filter { it.reviewStatus == ReviewStatus.PENDING_REVIEW.name }
            .sortedWith(compareBy(priorityRank, { it.id }))

        val approved = actions
            .filter { it.reviewStatus == ReviewStatus.ACCEPTED.name && !it.isCompleted }
            .sortedWith(
                compareBy(
                    { it.category != ActionCategory.CALENDAR_EVENT.name },
                    priorityRank,
                    { it.suggestedTimeMillis ?: Long.MAX_VALUE }
                )
            )

        val nextCal = approved.firstOrNull { it.category == ActionCategory.CALENDAR_EVENT.name }
            ?: pending.firstOrNull { it.category == ActionCategory.CALENDAR_EVENT.name }

        val newReady = captures.count {
            !it.includedInSummary && (it.transcriptText.isNotBlank() || it.type == CaptureType.TEXT_NOTE.name)
        }

        return WidgetSnapshot(
            recordingStatus = AudioCaptureService.state.value.status,
            todayCapturesCount = captures.size,
            newReadyForReviewCount = newReady,
            pendingActions = pending,
            approvedActions = approved,
            nextCalendarEvent = nextCal,
            isGoogleConnected = GoogleWorkspaceSyncService.isConnected.value
        )
    }

    suspend fun updateAllWidgets(context: Context) {
        val appContext = context.applicationContext
        val manager = GlanceAppWidgetManager(appContext)
        if (manager.getGlanceIds(QuickAccessWidget::class.java).isNotEmpty()) {
            QuickAccessWidget().updateAll(appContext)
        }
        if (manager.getGlanceIds(TaskApprovalWidget::class.java).isNotEmpty()) {
            TaskApprovalWidget().updateAll(appContext)
        }
        if (manager.getGlanceIds(DayHubWidget::class.java).isNotEmpty()) {
            DayHubWidget().updateAll(appContext)
        }
    }

    fun updateAllWidgetsAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching { updateAllWidgets(appContext) }
        }
    }
}

object WidgetActionParams {
    val KEY_ACTION_ID = ActionParameters.Key<Long>("action_id")
    val KEY_SERVICE_CMD = ActionParameters.Key<String>("service_cmd")
}

class ApproveWidgetActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val actionId = parameters[WidgetActionParams.KEY_ACTION_ID] ?: return
        val appContext = context.applicationContext
        val dao = AppDatabase.getInstance(appContext).smartActionDao()
        val item = dao.getById(actionId) ?: return
        JournalRepository(appContext).acceptAction(item)
        WidgetSyncHelper.updateAllWidgets(appContext)
    }
}

class DismissWidgetActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val actionId = parameters[WidgetActionParams.KEY_ACTION_ID] ?: return
        val appContext = context.applicationContext
        val dao = AppDatabase.getInstance(appContext).smartActionDao()
        val item = dao.getById(actionId) ?: return
        JournalRepository(appContext).dismissAction(item)
        WidgetSyncHelper.updateAllWidgets(appContext)
    }
}

class ToggleCompleteWidgetActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val actionId = parameters[WidgetActionParams.KEY_ACTION_ID] ?: return
        val appContext = context.applicationContext
        val dao = AppDatabase.getInstance(appContext).smartActionDao()
        val item = dao.getById(actionId) ?: return
        JournalRepository(appContext).toggleActionCompleted(item)
        WidgetSyncHelper.updateAllWidgets(appContext)
    }
}

class OpenCalendarInsertCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val actionId = parameters[WidgetActionParams.KEY_ACTION_ID]
        val appContext = context.applicationContext
        val action = if (actionId != null) {
            AppDatabase.getInstance(appContext).smartActionDao().getById(actionId)
        } else {
            null
        }

        if (action != null && action.reviewStatus == ReviewStatus.PENDING_REVIEW.name) {
            JournalRepository(appContext).acceptAction(action)
            WidgetSyncHelper.updateAllWidgets(appContext)
        }

        val intent = if (action != null) {
            Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(CalendarContract.Events.TITLE, action.title)
                if (action.notes.isNotBlank()) {
                    putExtra(CalendarContract.Events.DESCRIPTION, action.notes)
                }
                action.suggestedTimeMillis?.let { start ->
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                    putExtra(
                        CalendarContract.EXTRA_EVENT_END_TIME,
                        action.endTimeMillis ?: (start + 30 * 60_000L)
                    )
                }
            }
        } else {
            WidgetCommands.createLaunchIntent(appContext, WidgetCommands.CMD_QUICK_REVIEW)
        }

        runCatching { appContext.startActivity(intent) }
    }
}

class RecordingControlWidgetCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val cmd = parameters[WidgetActionParams.KEY_SERVICE_CMD] ?: return
        val appContext = context.applicationContext
        val currentStatus = AudioCaptureService.state.value.status

        if (currentStatus == CaptureServiceStatus.IDLE) {
            appContext.startActivity(
                WidgetCommands.createLaunchIntent(appContext, WidgetCommands.CMD_START_CAPTURE)
            )
            return
        }

        val serviceAction = when (cmd) {
            "pause" -> AudioCaptureService.ACTION_PAUSE
            "resume" -> AudioCaptureService.ACTION_RESUME
            "split" -> AudioCaptureService.ACTION_SPLIT_SEGMENT
            "stop" -> AudioCaptureService.ACTION_STOP
            else -> null
        }

        if (serviceAction != null) {
            runCatching {
                appContext.startService(
                    Intent(appContext, AudioCaptureService::class.java).apply {
                        action = serviceAction
                    }
                )
            }
            WidgetSyncHelper.updateAllWidgets(appContext)
        }
    }
}
