package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.R
import com.example.data.local.ActionCategory
import com.example.data.local.DateKeyUtil
import com.example.data.local.SmartActionEntity
import com.example.service.CaptureServiceStatus

class DayHubWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSyncHelper.loadSnapshot(context)
        provideContent {
            GlanceTheme(colors = DayPulseGlanceColorScheme.colors) {
                DayHubContent(snapshot)
            }
        }
    }

    @Composable
    private fun DayHubContent(snapshot: WidgetSnapshot) {
        val context = LocalContext.current
        val isRecording = snapshot.recordingStatus == CaptureServiceStatus.RECORDING
        val isActive = snapshot.recordingStatus != CaptureServiceStatus.IDLE
        val pending = snapshot.pendingActions
        val approved = snapshot.approvedActions

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(ImageProvider(R.drawable.widget_glass_bg))
                .cornerRadius(24.dp)
                .padding(14.dp)
        ) {
            // Header
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "DayPulse Hub",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = when {
                            isRecording -> "● Recording chunk • ${snapshot.todayCapturesCount} saved"
                            isActive -> "❚❚ Paused • ${snapshot.todayCapturesCount} saved"
                            else -> "${snapshot.todayCapturesCount} captures • ${snapshot.newReadyForReviewCount} ready"
                        },
                        style = TextStyle(
                            color = if (isRecording) GlanceTheme.colors.tertiary else GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )
                }

                if (snapshot.isGoogleConnected) {
                    Text(
                        text = "Google Sync On",
                        style = TextStyle(
                            color = GlanceTheme.colors.secondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(GlanceModifier.height(8.dp))

            // Quick Access Bar
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isActive) {
                    WidgetPillButton(
                        text = "Start Capture",
                        onClick = actionStartActivity(
                            WidgetCommands.createLaunchIntent(context, WidgetCommands.CMD_START_CAPTURE)
                        ),
                        backgroundColor = GlanceTheme.colors.primary,
                        contentColor = GlanceTheme.colors.onPrimary,
                        modifier = GlanceModifier.defaultWeight()
                    )
                } else {
                    WidgetPillButton(
                        text = "Save Chunk",
                        onClick = actionRunCallback<RecordingControlWidgetCallback>(
                            actionParametersOf(WidgetActionParams.KEY_SERVICE_CMD to "split")
                        ),
                        backgroundColor = GlanceTheme.colors.tertiary,
                        contentColor = GlanceTheme.colors.onTertiary,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    WidgetPillButton(
                        text = "Stop",
                        onClick = actionRunCallback<RecordingControlWidgetCallback>(
                            actionParametersOf(WidgetActionParams.KEY_SERVICE_CMD to "stop")
                        ),
                        backgroundColor = GlanceTheme.colors.tertiaryContainer,
                        contentColor = GlanceTheme.colors.onTertiaryContainer
                    )
                }

                Spacer(GlanceModifier.width(6.dp))

                WidgetPillButton(
                    text = "Quick Review",
                    onClick = actionStartActivity(
                        WidgetCommands.createLaunchIntent(context, WidgetCommands.CMD_QUICK_REVIEW)
                    ),
                    backgroundColor = GlanceTheme.colors.primaryContainer,
                    contentColor = GlanceTheme.colors.onPrimaryContainer,
                    modifier = GlanceModifier.defaultWeight()
                )

                Spacer(GlanceModifier.width(6.dp))

                WidgetPillButton(
                    text = "Note",
                    onClick = actionStartActivity(
                        WidgetCommands.createLaunchIntent(context, WidgetCommands.CMD_QUICK_NOTE)
                    ),
                    backgroundColor = GlanceTheme.colors.surfaceVariant,
                    contentColor = GlanceTheme.colors.onSurface
                )
            }

            Spacer(GlanceModifier.height(10.dp))

            LazyColumn(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
            ) {
                if (pending.isNotEmpty()) {
                    item {
                        Text(
                            text = "NEEDS APPROVAL (${pending.size})",
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = GlanceModifier.padding(bottom = 6.dp)
                        )
                    }
                    items(items = pending.take(4), itemId = { it.id }) { action ->
                        Column {
                            PendingApprovalWidgetRow(
                                action = action,
                                onApprove = actionRunCallback<ApproveWidgetActionCallback>(
                                    actionParametersOf(WidgetActionParams.KEY_ACTION_ID to action.id)
                                ),
                                onDismiss = actionRunCallback<DismissWidgetActionCallback>(
                                    actionParametersOf(WidgetActionParams.KEY_ACTION_ID to action.id)
                                ),
                                onScheduleCalendar = actionRunCallback<OpenCalendarInsertCallback>(
                                    actionParametersOf(WidgetActionParams.KEY_ACTION_ID to action.id)
                                )
                            )
                            Spacer(GlanceModifier.height(6.dp))
                        }
                    }
                }

                item {
                    Text(
                        text = if (approved.isNotEmpty()) {
                            "CALENDAR & TASKS (${approved.size})"
                        } else {
                            "CALENDAR & TASKS"
                        },
                        style = TextStyle(
                            color = GlanceTheme.colors.secondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.padding(top = 4.dp, bottom = 6.dp)
                    )
                }

                if (approved.isEmpty()) {
                    item {
                        Column(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(ImageProvider(R.drawable.widget_inner_glass_bg))
                                .cornerRadius(16.dp)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "No scheduled items yet",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Capture audio or run Quick Review to populate tasks & calendar schedules.",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                } else {
                    items(items = approved.take(6), itemId = { 1_000_000L + it.id }) { action ->
                        Column {
                            ApprovedScheduleWidgetRow(action)
                            Spacer(GlanceModifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ApprovedScheduleWidgetRow(action: SmartActionEntity) {
        val isCalendar = action.category == ActionCategory.CALENDAR_EVENT.name
        val timeLabel = action.suggestedTimeMillis?.let { DateKeyUtil.formatDateTimeShort(it) } ?: "Today"

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ImageProvider(R.drawable.widget_inner_glass_bg))
                .cornerRadius(16.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WidgetPriorityBadge(action.priority)
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = "${categoryShortLabel(action.category)} • $timeLabel",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )
                }
                Spacer(GlanceModifier.height(3.dp))
                Text(
                    text = action.title,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }

            Spacer(GlanceModifier.width(8.dp))

            if (isCalendar) {
                WidgetPillButton(
                    text = if (action.syncedToGoogle) "Synced" else "+Cal",
                    onClick = actionRunCallback<OpenCalendarInsertCallback>(
                        actionParametersOf(WidgetActionParams.KEY_ACTION_ID to action.id)
                    ),
                    backgroundColor = if (action.syncedToGoogle) {
                        GlanceTheme.colors.secondaryContainer
                    } else {
                        GlanceTheme.colors.primaryContainer
                    },
                    contentColor = if (action.syncedToGoogle) {
                        GlanceTheme.colors.onSecondaryContainer
                    } else {
                        GlanceTheme.colors.onPrimaryContainer
                    }
                )
            } else {
                WidgetPillButton(
                    text = "Done",
                    onClick = actionRunCallback<ToggleCompleteWidgetActionCallback>(
                        actionParametersOf(WidgetActionParams.KEY_ACTION_ID to action.id)
                    ),
                    backgroundColor = GlanceTheme.colors.secondaryContainer,
                    contentColor = GlanceTheme.colors.onSecondaryContainer
                )
            }
        }
    }
}

class DayHubWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DayHubWidget()
}
