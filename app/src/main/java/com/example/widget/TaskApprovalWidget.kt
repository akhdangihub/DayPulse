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
import com.example.service.CaptureServiceStatus

class TaskApprovalWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSyncHelper.loadSnapshot(context)
        provideContent {
            GlanceTheme(colors = DayPulseGlanceColorScheme.colors) {
                TaskApprovalContent(snapshot)
            }
        }
    }

    @Composable
    private fun TaskApprovalContent(snapshot: WidgetSnapshot) {
        val context = LocalContext.current
        val pending = snapshot.pendingActions
        val isActive = snapshot.recordingStatus != CaptureServiceStatus.IDLE

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(ImageProvider(R.drawable.widget_glass_bg))
                .cornerRadius(24.dp)
                .padding(14.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "Task Approvals",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = if (pending.isNotEmpty()) {
                            "${pending.size} waiting • LOW / MED / HIGH"
                        } else {
                            "No approvals needed right now"
                        },
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )
                }

                WidgetPillButton(
                    text = if (isActive) "Split" else "Capture",
                    onClick = if (isActive) {
                        actionRunCallback<RecordingControlWidgetCallback>(
                            actionParametersOf(WidgetActionParams.KEY_SERVICE_CMD to "split")
                        )
                    } else {
                        actionStartActivity(
                            WidgetCommands.createLaunchIntent(context, WidgetCommands.CMD_START_CAPTURE)
                        )
                    },
                    backgroundColor = GlanceTheme.colors.primary,
                    contentColor = GlanceTheme.colors.onPrimary
                )

                Spacer(GlanceModifier.width(6.dp))

                WidgetPillButton(
                    text = "Review",
                    onClick = actionStartActivity(
                        WidgetCommands.createLaunchIntent(context, WidgetCommands.CMD_QUICK_REVIEW)
                    ),
                    backgroundColor = GlanceTheme.colors.primaryContainer,
                    contentColor = GlanceTheme.colors.onPrimaryContainer
                )
            }

            Spacer(GlanceModifier.height(10.dp))

            if (pending.isEmpty()) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .background(ImageProvider(R.drawable.widget_inner_glass_bg))
                        .cornerRadius(16.dp)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "All clear — no pending tasks",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        text = "Chunks without tasks stay silent. New tasks or schedules appear here automatically.",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp
                        ),
                        maxLines = 3
                    )
                }
            } else {
                LazyColumn(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                ) {
                    items(items = pending.take(8), itemId = { it.id }) { action ->
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
                            Spacer(GlanceModifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

class TaskApprovalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskApprovalWidget()
}
