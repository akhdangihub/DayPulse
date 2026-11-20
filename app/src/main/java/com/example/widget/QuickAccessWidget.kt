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

class QuickAccessWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSyncHelper.loadSnapshot(context)
        provideContent {
            GlanceTheme(colors = DayPulseGlanceColorScheme.colors) {
                QuickAccessContent(snapshot)
            }
        }
    }

    @Composable
    private fun QuickAccessContent(snapshot: WidgetSnapshot) {
        val context = LocalContext.current
        val isRecording = snapshot.recordingStatus == CaptureServiceStatus.RECORDING
        val isPaused = snapshot.recordingStatus == CaptureServiceStatus.PAUSED
        val isActive = isRecording || isPaused

        val pendingCount = snapshot.pendingActions.size
        val topPending = snapshot.pendingActions.firstOrNull()

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(ImageProvider(R.drawable.widget_glass_bg))
                .cornerRadius(24.dp)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DayPulse",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = when {
                        isRecording -> "● Recording"
                        isPaused -> "❚❚ Paused"
                        pendingCount > 0 -> "$pendingCount pending"
                        else -> "${snapshot.todayCapturesCount} saved"
                    },
                    style = TextStyle(
                        color = when {
                            isRecording -> GlanceTheme.colors.tertiary
                            pendingCount > 0 -> GlanceTheme.colors.primary
                            else -> GlanceTheme.colors.onSurfaceVariant
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }

            Spacer(GlanceModifier.height(8.dp))

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
                    text = if (pendingCount > 0) "Review ($pendingCount)" else "Quick Review",
                    onClick = actionStartActivity(
                        WidgetCommands.createLaunchIntent(context, WidgetCommands.CMD_QUICK_REVIEW)
                    ),
                    backgroundColor = GlanceTheme.colors.primaryContainer,
                    contentColor = GlanceTheme.colors.onPrimaryContainer,
                    modifier = GlanceModifier.defaultWeight()
                )

                Spacer(GlanceModifier.width(6.dp))

                if (snapshot.nextCalendarEvent != null) {
                    WidgetPillButton(
                        text = "Calendar",
                        onClick = actionRunCallback<OpenCalendarInsertCallback>(
                            actionParametersOf(WidgetActionParams.KEY_ACTION_ID to snapshot.nextCalendarEvent.id)
                        ),
                        backgroundColor = GlanceTheme.colors.secondaryContainer,
                        contentColor = GlanceTheme.colors.onSecondaryContainer
                    )
                } else {
                    WidgetPillButton(
                        text = "Note",
                        onClick = actionStartActivity(
                            WidgetCommands.createLaunchIntent(context, WidgetCommands.CMD_QUICK_NOTE)
                        ),
                        backgroundColor = GlanceTheme.colors.surfaceVariant,
                        contentColor = GlanceTheme.colors.onSurface
                    )
                }
            }

            // Only show instant 1-tap approval bar if a chunk actually produced a pending task
            if (topPending != null) {
                Spacer(GlanceModifier.height(8.dp))
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(ImageProvider(R.drawable.widget_inner_glass_bg))
                        .cornerRadius(16.dp)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WidgetPriorityBadge(topPending.priority)
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        text = topPending.title,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(GlanceModifier.width(6.dp))
                    WidgetPillButton(
                        text = "Approve",
                        onClick = actionRunCallback<ApproveWidgetActionCallback>(
                            actionParametersOf(WidgetActionParams.KEY_ACTION_ID to topPending.id)
                        ),
                        backgroundColor = GlanceTheme.colors.secondary,
                        contentColor = GlanceTheme.colors.onSecondary
                    )
                }
            }
        }
    }
}

class QuickAccessWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAccessWidget()
}
