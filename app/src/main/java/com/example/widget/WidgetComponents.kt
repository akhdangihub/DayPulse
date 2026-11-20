package com.example.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.R
import com.example.data.local.ActionCategory
import com.example.data.local.ActionPriority
import com.example.data.local.DateKeyUtil
import com.example.data.local.SmartActionEntity
import com.example.ui.theme.LavenderAmberContainer
import com.example.ui.theme.LavenderAmberOnContainer
import com.example.ui.theme.MidnightAmberContainer
import com.example.ui.theme.MidnightAmberOnContainer

private val WidgetAmberContainer = DayNightColorProvider(
    day = LavenderAmberContainer,
    night = MidnightAmberContainer
)

private val WidgetAmberOnContainer = DayNightColorProvider(
    day = LavenderAmberOnContainer,
    night = MidnightAmberOnContainer
)

@Composable
internal fun WidgetPillButton(
    text: String,
    onClick: Action,
    backgroundColor: ColorProvider,
    contentColor: ColorProvider,
    modifier: GlanceModifier = GlanceModifier
) {
    Box(
        modifier = modifier
            .background(backgroundColor)
            .cornerRadius(24.dp)
            .clickable(onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

@Composable
internal fun WidgetPriorityBadge(priority: String) {
    val (label, bgColor, fgColor) = when (priority) {
        ActionPriority.HIGH.name -> Triple(
            "HIGH",
            GlanceTheme.colors.tertiaryContainer,
            GlanceTheme.colors.onTertiaryContainer
        )
        ActionPriority.LOW.name -> Triple(
            "LOW",
            GlanceTheme.colors.secondaryContainer,
            GlanceTheme.colors.onSecondaryContainer
        )
        else -> Triple(
            "MED",
            WidgetAmberContainer,
            WidgetAmberOnContainer
        )
    }

    Box(
        modifier = GlanceModifier
            .background(bgColor)
            .cornerRadius(12.dp)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = fgColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

internal fun categoryShortLabel(category: String): String {
    return when (category) {
        ActionCategory.CALENDAR_EVENT.name -> "Calendar"
        ActionCategory.REMINDER.name -> "Reminder"
        else -> "Task"
    }
}

@Composable
internal fun PendingApprovalWidgetRow(
    action: SmartActionEntity,
    onApprove: Action,
    onDismiss: Action,
    onScheduleCalendar: Action
) {
    val isCalendar = action.category == ActionCategory.CALENDAR_EVENT.name
    val timeLabel = action.suggestedTimeMillis?.let { DateKeyUtil.formatDateTimeShort(it) } ?: "Today"

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(R.drawable.widget_inner_glass_bg))
            .cornerRadius(16.dp)
            .padding(11.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetPriorityBadge(action.priority)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = "${categoryShortLabel(action.category)} • $timeLabel",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
        }

        Spacer(GlanceModifier.height(5.dp))

        Text(
            text = action.title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 2
        )

        Spacer(GlanceModifier.height(7.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetPillButton(
                text = "Approve",
                onClick = onApprove,
                backgroundColor = GlanceTheme.colors.secondary,
                contentColor = GlanceTheme.colors.onSecondary,
                modifier = GlanceModifier.defaultWeight()
            )
            if (isCalendar) {
                Spacer(GlanceModifier.width(6.dp))
                WidgetPillButton(
                    text = "+Cal",
                    onClick = onScheduleCalendar,
                    backgroundColor = GlanceTheme.colors.primary,
                    contentColor = GlanceTheme.colors.onPrimary
                )
            }
            Spacer(GlanceModifier.width(6.dp))
            WidgetPillButton(
                text = "Dismiss",
                onClick = onDismiss,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
                contentColor = GlanceTheme.colors.onSurfaceVariant
            )
        }
    }
}
