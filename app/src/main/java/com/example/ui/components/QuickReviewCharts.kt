package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ActivityBucket
import com.example.data.ActivityTimeSlice
import com.example.data.DayAnalyticsCalculator
import com.example.data.DayAnalyticsUiState
import com.example.data.local.ActionPriority
import com.example.ui.theme.LocalMidnightGlass
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

private enum class BarChartMode {
    BY_STATUS,
    BY_PRIORITY
}

private data class ArcRange(
    val slice: ActivityTimeSlice,
    val startAngle: Float,
    val sweepAngle: Float
)

@Composable
fun QuickReviewDualSummaryCard(
    analytics: DayAnalyticsUiState,
    selectedDateKey: String,
    modifier: Modifier = Modifier
) {
    var selectedBucket by remember(selectedDateKey) { mutableStateOf<ActivityBucket?>(null) }
    var barMode by remember { mutableStateOf(BarChartMode.BY_STATUS) }
    var selectedBarIndex by remember(selectedDateKey, barMode) { mutableIntStateOf(-1) }

    val glass = LocalMidnightGlass.current
    val bucketColors = remember(glass) {
        mapOf(
            ActivityBucket.MEETINGS_CALENDAR to glass.violetAccent,
            ActivityBucket.DEEP_WORK to glass.amberAccent,
            ActivityBucket.VOICE_REFLECTION to glass.mintAccent,
            ActivityBucket.PERSONAL_WELLNESS to glass.coralAccent
        )
    }

    GlassContainer(
        tier = GlassTier.LEVEL_2,
        shape = RoundedCornerShape(26.dp),
        accentGlow = glass.violetAccent,
        useBackdropBlur = true,
        modifier = modifier
            .fillMaxWidth()
            .testTag("quick_review_analytics_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card Headline & Tabular KPI Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Daily Activity & Tasks",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = buildSubtitle(analytics),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KpiPillBadge(
                        icon = Icons.Outlined.TaskAlt,
                        text = "${analytics.completedTasks}/${analytics.totalTasks}",
                        containerColor = if (analytics.completedTasks > 0) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (analytics.completedTasks > 0) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        contentDescription = "${analytics.completedTasks} of ${analytics.totalTasks} tasks completed",
                        modifier = Modifier.testTag("kpi_tasks_completed")
                    )

                    KpiPillBadge(
                        icon = Icons.Outlined.Schedule,
                        text = DayAnalyticsCalculator.formatMinutesCompact(analytics.totalTrackedMinutes),
                        containerColor = if (analytics.totalTrackedMinutes > 0) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (analytics.totalTrackedMinutes > 0) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        contentDescription = "Total tracked time ${DayAnalyticsCalculator.formatMinutesCompact(analytics.totalTrackedMinutes)}",
                        modifier = Modifier.testTag("kpi_tracked_time")
                    )
                }
            }

            if (!analytics.hasAnyActivityOrTasks) {
                EmptyAnalyticsSection()
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val sideBySide = maxWidth >= 520.dp
                    if (sideBySide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            ActivityDonutSection(
                                analytics = analytics,
                                selectedDateKey = selectedDateKey,
                                selectedBucket = selectedBucket,
                                bucketColors = bucketColors,
                                onToggleBucket = { bucket ->
                                    selectedBucket = if (selectedBucket == bucket) null else bucket
                                },
                                modifier = Modifier.weight(1f)
                            )

                            VerticalDivider(
                                modifier = Modifier.height(230.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )

                            TasksBarSection(
                                analytics = analytics,
                                selectedDateKey = selectedDateKey,
                                barMode = barMode,
                                selectedBarIndex = selectedBarIndex,
                                onSelectMode = { barMode = it },
                                onToggleBar = { index ->
                                    selectedBarIndex = if (selectedBarIndex == index) -1 else index
                                },
                                modifier = Modifier.weight(1.08f)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ActivityDonutSection(
                                analytics = analytics,
                                selectedDateKey = selectedDateKey,
                                selectedBucket = selectedBucket,
                                bucketColors = bucketColors,
                                onToggleBucket = { bucket ->
                                    selectedBucket = if (selectedBucket == bucket) null else bucket
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )

                            TasksBarSection(
                                analytics = analytics,
                                selectedDateKey = selectedDateKey,
                                barMode = barMode,
                                selectedBarIndex = selectedBarIndex,
                                onSelectMode = { barMode = it },
                                onToggleBar = { index ->
                                    selectedBarIndex = if (selectedBarIndex == index) -1 else index
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiPillBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    containerColor: Color,
    contentColor: Color,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        modifier = modifier.semantics { this.contentDescription = contentDescription }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EmptyAnalyticsSection() {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analytics_empty_state")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 6.dp.toPx()
                    drawCircle(
                        color = trackColor,
                        radius = (size.minDimension - strokePx) / 2f,
                        style = Stroke(width = strokePx)
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.DonutLarge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "No activity or tasks logged yet",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Record an audio chunk or approve a calendar schedule to see today’s time and task charts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActivityDonutSection(
    analytics: DayAnalyticsUiState,
    selectedDateKey: String,
    selectedBucket: ActivityBucket?,
    bucketColors: Map<ActivityBucket, Color>,
    onToggleBucket: (ActivityBucket) -> Unit,
    modifier: Modifier = Modifier
) {
    val slices = analytics.activitySlices
    val selectedSlice = remember(slices, selectedBucket) {
        slices.firstOrNull { it.bucket == selectedBucket }
    }

    var revealTarget by remember(selectedDateKey, slices.size, analytics.totalTrackedMinutes) {
        mutableStateOf(false)
    }
    LaunchedEffect(selectedDateKey, slices.size, analytics.totalTrackedMinutes) {
        revealTarget = true
    }
    val sweepProgress by animateFloatAsState(
        targetValue = if (revealTarget) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "donut_sweep"
    )

    val arcRanges = remember(slices) { computeArcRanges(slices) }
    val ringTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val defaultAccent = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Time by Activity",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Audio + Calendar",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (slices.isEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Canvas(modifier = Modifier.size(44.dp)) {
                        drawCircle(
                            color = ringTrackColor,
                            radius = (size.minDimension - 8.dp.toPx()) / 2f,
                            style = Stroke(width = 8.dp.toPx())
                        )
                    }
                    Text(
                        text = "0m of audio chunks or calendar events recorded for this day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(164.dp)
                        .testTag("activity_donut_canvas")
                        .semantics {
                            contentDescription = "Activity time donut chart totaling ${DayAnalyticsCalculator.formatMinutesCompact(analytics.totalTrackedMinutes)}"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(arcRanges) {
                                detectTapGestures { offset ->
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val dx = offset.x - cx
                                    val dy = offset.y - cy
                                    val dist = hypot(dx, dy)
                                    val outerR = minOf(size.width, size.height) / 2f
                                    val innerR = outerR * 0.42f
                                    if (dist in innerR..outerR && arcRanges.isNotEmpty()) {
                                        var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                        if (deg < -90f) deg += 360f
                                        val normalizedFromTop = (deg + 90f).let {
                                            if (it >= 360f) it - 360f else if (it < 0f) it + 360f else it
                                        }
                                        val tapped = arcRanges.firstOrNull { range ->
                                            val start = (range.startAngle + 90f).let { if (it < 0f) it + 360f else it % 360f }
                                            val end = start + range.sweepAngle
                                            normalizedFromTop in start..end
                                        }
                                        tapped?.let { onToggleBucket(it.slice.bucket) }
                                    }
                                }
                            }
                    ) {
                        val baseStroke = 18.dp.toPx()
                        val expandedStroke = 23.dp.toPx()
                        val padding = expandedStroke / 2f + 2.dp.toPx()
                        val diameter = size.minDimension - padding * 2f
                        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                        val arcSize = Size(diameter, diameter)

                        drawArc(
                            color = ringTrackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = baseStroke)
                        )

                        arcRanges.forEach { range ->
                            val isSelected = selectedBucket == range.slice.bucket
                            val dimmed = selectedBucket != null && !isSelected
                            val sliceColor = (bucketColors[range.slice.bucket] ?: defaultAccent)
                                .copy(alpha = if (dimmed) 0.35f else 1f)
                            val strokeW = if (isSelected) expandedStroke else baseStroke
                            val animatedSweep = (range.sweepAngle * sweepProgress).coerceAtLeast(0.5f)
                            drawArc(
                                color = sliceColor,
                                startAngle = range.startAngle,
                                sweepAngle = animatedSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(
                                    width = strokeW,
                                    cap = if (arcRanges.size > 1) StrokeCap.Round else StrokeCap.Butt
                                )
                            )
                        }
                    }

                    // Center Callout
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        if (selectedSlice != null) {
                            Text(
                                text = DayAnalyticsCalculator.formatMinutesCompact(selectedSlice.minutes),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = bucketColors[selectedSlice.bucket] ?: MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${selectedSlice.sharePercent}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = DayAnalyticsCalculator.formatMinutesCompact(analytics.totalTrackedMinutes),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Total Time",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Interactive Activity Legend Rows
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                slices.forEach { slice ->
                    val isSelected = selectedBucket == slice.bucket
                    val color = bucketColors[slice.bucket] ?: defaultAccent
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            color.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
                        },
                        border = if (isSelected) BorderStroke(1.dp, color.copy(alpha = 0.65f)) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleBucket(slice.bucket) }
                            .testTag("donut_legend_${slice.bucket.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Text(
                                    text = slice.bucket.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = "${DayAnalyticsCalculator.formatMinutesCompact(slice.minutes)} • ${slice.sharePercent}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksBarSection(
    analytics: DayAnalyticsUiState,
    selectedDateKey: String,
    barMode: BarChartMode,
    selectedBarIndex: Int,
    onSelectMode: (BarChartMode) -> Unit,
    onToggleBar: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalMidnightGlass.current
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val primaryColor = glass.violetAccent
    val secondaryColor = glass.mintAccent
    val tertiaryColor = glass.coralAccent
    val amberColor = glass.amberAccent

    var animateBars by remember(selectedDateKey, barMode, analytics.totalTasks, analytics.completedTasks) {
        mutableStateOf(false)
    }
    LaunchedEffect(selectedDateKey, barMode, analytics.totalTasks, analytics.completedTasks) {
        animateBars = true
    }
    val barProgress by animateFloatAsState(
        targetValue = if (animateBars) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "bar_height_anim"
    )

    val columns = remember(analytics, barMode, primaryColor, secondaryColor, tertiaryColor) {
        when (barMode) {
            BarChartMode.BY_STATUS -> {
                val stats = analytics.statusBars.ifEmpty {
                    listOf(
                        com.example.data.StatusBarStat("completed", "Done", 0),
                        com.example.data.StatusBarStat("approved", "Approved", 0),
                        com.example.data.StatusBarStat("pending", "Pending", 0)
                    )
                }
                val colors = listOf(secondaryColor, primaryColor, tertiaryColor)
                stats.mapIndexed { idx, s ->
                    BarColumnVisual(
                        label = s.label,
                        primaryValue = s.count,
                        totalValue = s.count,
                        valueText = "${s.count}",
                        detailText = "${s.label}: ${s.count} task(s)",
                        color = colors.getOrElse(idx) { primaryColor }
                    )
                }
            }

            BarChartMode.BY_PRIORITY -> {
                val stats = analytics.priorityStats
                stats.map { p ->
                    val color = when (p.priority) {
                        ActionPriority.HIGH -> tertiaryColor
                        ActionPriority.MEDIUM -> amberColor
                        ActionPriority.LOW -> secondaryColor
                    }
                    BarColumnVisual(
                        label = p.label,
                        primaryValue = p.completed,
                        totalValue = p.total,
                        valueText = "${p.completed}/${p.total}",
                        detailText = "${p.label} priority: ${p.completed} of ${p.total} completed",
                        color = color
                    )
                }
            }
        }
    }

    val maxScale = remember(columns, barMode) {
        val rawMax = when (barMode) {
            BarChartMode.BY_STATUS -> columns.maxOfOrNull { it.primaryValue } ?: 0
            BarChartMode.BY_PRIORITY -> columns.maxOfOrNull { it.totalValue } ?: 0
        }
        max(1, rawMax)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Tasks Completed",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${analytics.completionRatePercent}% completion rate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Segmented Bar Mode Toggle (48dp compliant touch targets)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeFilterChip(
                    label = "Status",
                    selected = barMode == BarChartMode.BY_STATUS,
                    onClick = { onSelectMode(BarChartMode.BY_STATUS) },
                    modifier = Modifier.testTag("bar_mode_status")
                )
                ModeFilterChip(
                    label = "Priority",
                    selected = barMode == BarChartMode.BY_PRIORITY,
                    onClick = { onSelectMode(BarChartMode.BY_PRIORITY) },
                    modifier = Modifier.testTag("bar_mode_priority")
                )
            }
        }

        AnimatedVisibility(visible = selectedBarIndex in columns.indices) {
            val detail = columns.getOrNull(selectedBarIndex)?.detailText.orEmpty()
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bar_selection_detail")
            ) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Value readouts aligned above each bar slot
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            columns.forEachIndexed { index, col ->
                val isSelected = selectedBarIndex == index
                Text(
                    text = col.valueText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) col.color else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Compose Canvas Bar Chart
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
                .testTag("tasks_bar_canvas")
                .semantics {
                    contentDescription = "Tasks completion bar chart: ${analytics.completedTasks} of ${analytics.totalTasks} tasks completed"
                }
                .pointerInput(columns.size) {
                    detectTapGestures { offset ->
                        if (columns.isNotEmpty() && size.width > 0) {
                            val slotWidth = size.width.toFloat() / columns.size
                            val idx = (offset.x / slotWidth).toInt().coerceIn(0, columns.lastIndex)
                            onToggleBar(idx)
                        }
                    }
                }
        ) {
            val chartHeight = size.height
            val chartWidth = size.width
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

            // Horizontal guide lines (top, mid, baseline)
            listOf(0.08f, 0.54f).forEach { fraction ->
                val y = chartHeight * fraction
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(chartWidth, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )
            }
            drawLine(
                color = gridColor.copy(alpha = 0.5f),
                start = Offset(0f, chartHeight - 1.dp.toPx()),
                end = Offset(chartWidth, chartHeight - 1.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )

            if (columns.isEmpty()) return@Canvas

            val slotWidth = chartWidth / columns.size
            val barWidth = (slotWidth * 0.44f).coerceAtMost(44.dp.toPx())
            val cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
            val usableHeight = chartHeight - 8.dp.toPx()

            columns.forEachIndexed { idx, col ->
                val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                val isSelected = selectedBarIndex == idx
                val dimmed = selectedBarIndex != -1 && !isSelected

                // Full-height subtle background column track
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, 4.dp.toPx()),
                    size = Size(barWidth, usableHeight),
                    cornerRadius = cornerRadius
                )

                if (barMode == BarChartMode.BY_PRIORITY && col.totalValue > 0) {
                    val totalH = (col.totalValue.toFloat() / maxScale) * usableHeight * barProgress
                    val totalTop = (chartHeight - totalH).coerceAtLeast(4.dp.toPx())
                    // Total workload in priority tier (translucent)
                    drawRoundRect(
                        color = col.color.copy(alpha = if (dimmed) 0.16f else 0.30f),
                        topLeft = Offset(left, totalTop),
                        size = Size(barWidth, totalH),
                        cornerRadius = cornerRadius
                    )
                }

                if (col.primaryValue > 0) {
                    val fillH = (col.primaryValue.toFloat() / maxScale) * usableHeight * barProgress
                    val fillTop = (chartHeight - fillH).coerceAtLeast(4.dp.toPx())
                    drawRoundRect(
                        color = col.color.copy(alpha = if (dimmed) 0.40f else 1f),
                        topLeft = Offset(left, fillTop),
                        size = Size(barWidth, fillH),
                        cornerRadius = cornerRadius
                    )
                }

                if (isSelected) {
                    drawRoundRect(
                        color = col.color,
                        topLeft = Offset(left - 2.dp.toPx(), 2.dp.toPx()),
                        size = Size(barWidth + 4.dp.toPx(), usableHeight + 4.dp.toPx()),
                        cornerRadius = cornerRadius,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }

        // Tappable column labels underneath (48dp touch target)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            columns.forEachIndexed { index, col ->
                val isSelected = selectedBarIndex == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onToggleBar(index) }
                        .padding(vertical = 4.dp)
                        .testTag("bar_col_$index"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = col.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) col.color else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (barMode == BarChartMode.BY_PRIORITY) {
            Text(
                text = "Solid fill = completed • Tinted track = total tasks in tier",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModeFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

private data class BarColumnVisual(
    val label: String,
    val primaryValue: Int,
    val totalValue: Int,
    val valueText: String,
    val detailText: String,
    val color: Color
)

private fun buildSubtitle(analytics: DayAnalyticsUiState): String {
    val parts = buildList {
        if (analytics.audioCaptureMinutes > 0) {
            add("${DayAnalyticsCalculator.formatMinutesCompact(analytics.audioCaptureMinutes)} audio")
        }
        if (analytics.calendarScheduleMinutes > 0) {
            add("${DayAnalyticsCalculator.formatMinutesCompact(analytics.calendarScheduleMinutes)} calendar")
        }
    }
    return if (parts.isEmpty()) {
        "Tasks completed & activity time"
    } else {
        parts.joinToString(" • ")
    }
}

private fun computeArcRanges(slices: List<ActivityTimeSlice>): List<ArcRange> {
    if (slices.isEmpty()) return emptyList()
    if (slices.size == 1) {
        return listOf(ArcRange(slice = slices.first(), startAngle = -90f, sweepAngle = 360f))
    }

    val totalMinutes = slices.sumOf { it.minutes }.coerceAtLeast(1)
    val gapDeg = 5f
    val availableDeg = 360f - (gapDeg * slices.size)
    val minSweep = 10f

    // Reserve minimum visible arc angle for tiny slices while preserving proportions
    val rawSweeps = slices.map { slice ->
        max(minSweep, (slice.minutes.toFloat() / totalMinutes) * availableDeg)
    }
    val sweepSum = rawSweeps.sum()
    val scale = availableDeg / sweepSum

    var cursor = -90f
    return slices.mapIndexed { idx, slice ->
        val sweep = rawSweeps[idx] * scale
        val range = ArcRange(slice = slice, startAngle = cursor, sweepAngle = sweep)
        cursor += sweep + gapDeg
        range
    }
}
