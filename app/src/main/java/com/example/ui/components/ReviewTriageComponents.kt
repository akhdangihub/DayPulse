package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ActionCategory
import com.example.data.local.ActionPriority
import com.example.data.local.DailySummaryEntity
import com.example.data.local.DateKeyUtil
import com.example.data.local.ReviewStatus
import com.example.data.local.SmartActionEntity
import com.example.ui.theme.LocalMidnightGlass
import java.util.Calendar

@Composable
fun DailyNarrativeGlassCard(
    summary: DailySummaryEntity,
    onForceFullRebuild: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalMidnightGlass.current

    GlassContainer(
        tier = GlassTier.LEVEL_2,
        shape = RoundedCornerShape(26.dp),
        accentGlow = glass.violetAccent,
        useBackdropBlur = true,
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_summary_card")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TintedGlassChip(
                    text = summary.moodTag,
                    hue = glass.violetAccent
                )
                Text(
                    text = "${summary.sourceCaptureCount} captures • ${DateKeyUtil.formatClockTime(summary.generatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = summary.narrativeSummary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onForceFullRebuild,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Rebuild Full Day",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun KeyMomentsCarousel(
    highlights: List<String>,
    modifier: Modifier = Modifier
) {
    val glass = LocalMidnightGlass.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Key Moments",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${highlights.size} moments",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            itemsIndexed(highlights) { index, moment ->
                GlassContainer(
                    tier = GlassTier.LEVEL_1,
                    shape = RoundedCornerShape(22.dp),
                    useBackdropBlur = false,
                    modifier = Modifier.width(248.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = glass.mintAccent.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, glass.mintAccent.copy(alpha = 0.5f)),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = glass.mintAccent
                                )
                            }
                        }
                        Text(
                            text = moment,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OpenLoopsChecklistCard(
    dateKey: String,
    openLoops: List<String>,
    modifier: Modifier = Modifier
) {
    val glass = LocalMidnightGlass.current
    val haptics = LocalHapticFeedback.current
    val checkedStates = remember(dateKey, openLoops) { mutableStateMapOf<Int, Boolean>() }

    GlassContainer(
        tier = GlassTier.LEVEL_1,
        shape = RoundedCornerShape(24.dp),
        useBackdropBlur = false,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Open Loops",
                    style = MaterialTheme.typography.titleMedium
                )
                val doneCount = openLoops.indices.count { checkedStates[it] == true }
                Text(
                    text = "$doneCount/${openLoops.size} checked",
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.amberAccent
                )
            }

            openLoops.forEachIndexed { idx, loop ->
                val checked = checkedStates[idx] == true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            checkedStates[idx] = !checked
                        }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                        contentDescription = if (checked) "Mark open loop incomplete" else "Mark open loop checked",
                        tint = if (checked) glass.mintAccent else glass.amberAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = loop,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (checked) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableSmartActionCard(
    action: SmartActionEntity,
    isGoogleConnected: Boolean,
    onAccept: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    onToggleComplete: () -> Unit,
    onAddToCalendar: () -> Unit,
    onSyncToGoogle: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalMidnightGlass.current
    val haptics = LocalHapticFeedback.current
    val isPending = action.reviewStatus == ReviewStatus.PENDING_REVIEW.name

    if (!isPending) {
        SmartActionTriageCard(
            action = action,
            isGoogleConnected = isGoogleConnected,
            onAccept = onAccept,
            onEdit = onEdit,
            onDismiss = onDismiss,
            onToggleComplete = onToggleComplete,
            onAddToCalendar = onAddToCalendar,
            onSyncToGoogle = onSyncToGoogle,
            onRestore = onRestore,
            modifier = modifier
        )
        return
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAccept()
                    true
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val bgColor by animateColorAsState(
                targetValue = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> glass.mintAccent.copy(alpha = 0.22f)
                    SwipeToDismissBoxValue.EndToStart -> glass.coralAccent.copy(alpha = 0.22f)
                    else -> Color.Transparent
                },
                label = "swipe_bg"
            )
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.CenterEnd
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(bgColor)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = glass.mintAccent)
                        Text(
                            text = "Approve",
                            style = MaterialTheme.typography.titleSmall,
                            color = glass.mintAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Dismiss",
                            style = MaterialTheme.typography.titleSmall,
                            color = glass.coralAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(Icons.Outlined.Close, contentDescription = null, tint = glass.coralAccent)
                    }
                }
            }
        }
    ) {
        SmartActionTriageCard(
            action = action,
            isGoogleConnected = isGoogleConnected,
            onAccept = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onAccept()
            },
            onEdit = onEdit,
            onDismiss = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDismiss()
            },
            onToggleComplete = onToggleComplete,
            onAddToCalendar = onAddToCalendar,
            onSyncToGoogle = onSyncToGoogle,
            onRestore = onRestore
        )
    }
}

@Composable
fun SmartActionTriageCard(
    action: SmartActionEntity,
    isGoogleConnected: Boolean = false,
    onAccept: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    onToggleComplete: () -> Unit,
    onAddToCalendar: () -> Unit,
    onSyncToGoogle: () -> Unit = {},
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalMidnightGlass.current
    val haptics = LocalHapticFeedback.current
    val isPending = action.reviewStatus == ReviewStatus.PENDING_REVIEW.name
    val isAccepted = action.reviewStatus == ReviewStatus.ACCEPTED.name
    val isDismissed = action.reviewStatus == ReviewStatus.DISMISSED.name

    val categoryIcon = when (action.category) {
        ActionCategory.REMINDER.name -> Icons.Outlined.NotificationsActive
        ActionCategory.CALENDAR_EVENT.name -> Icons.Outlined.CalendarMonth
        else -> Icons.Outlined.CheckCircle
    }
    val categoryLabel = when (action.category) {
        ActionCategory.REMINDER.name -> "Reminder"
        ActionCategory.CALENDAR_EVENT.name -> "Calendar"
        else -> "To-Do"
    }
    val categoryTint = when (action.category) {
        ActionCategory.REMINDER.name -> glass.violetAccent
        ActionCategory.CALENDAR_EVENT.name -> glass.coralAccent
        else -> glass.mintAccent
    }

    GlassContainer(
        tier = GlassTier.LEVEL_1,
        shape = RoundedCornerShape(24.dp),
        useBackdropBlur = false,
        modifier = modifier
            .fillMaxWidth()
            .testTag("smart_action_card_${action.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = categoryTint.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, categoryTint.copy(alpha = 0.40f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = categoryIcon,
                                contentDescription = null,
                                tint = categoryTint,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = categoryLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = categoryTint
                            )
                        }
                    }

                    PriorityGlassPill(priority = action.priority)
                }

                if (action.suggestedTimeMillis != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = DateKeyUtil.formatDateTimeShort(action.suggestedTimeMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isAccepted && action.category == ActionCategory.TODO.name) {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleComplete()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toggle_todo_${action.id}")
                    ) {
                        Icon(
                            imageVector = if (action.isCompleted) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.RadioButtonUnchecked
                            },
                            contentDescription = "Toggle task completion",
                            tint = if (action.isCompleted) glass.mintAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = action.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (action.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (action.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (action.notes.isNotBlank()) {
                Text(
                    text = action.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (action.sourceExcerpt.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = "“${action.sourceExcerpt}”",
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isPending) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onAccept,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("accept_action_${action.id}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_accept))
                    }

                    if (action.category == ActionCategory.CALENDAR_EVENT.name) {
                        OutlinedButton(
                            onClick = onAddToCalendar,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("calendar_intent_${action.id}")
                        ) {
                            Text("+Cal", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onEdit,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("edit_action_${action.id}")
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_edit))
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dismiss_action_${action.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.action_dismiss),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isAccepted) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (action.syncedToGoogle) {
                        TintedGlassChip(
                            text = action.externalSyncNote.ifBlank { "Synced to Google" },
                            hue = glass.mintAccent
                        )
                    } else if (isGoogleConnected) {
                        FilledTonalButton(
                            onClick = onSyncToGoogle,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sync_google_${action.id}")
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (action.category == ActionCategory.CALENDAR_EVENT.name) {
                                    "Sync Calendar"
                                } else {
                                    "Sync Tasks"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    if (action.category == ActionCategory.CALENDAR_EVENT.name) {
                        OutlinedButton(
                            onClick = onAddToCalendar,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("calendar_intent_${action.id}")
                        ) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("+Cal", style = MaterialTheme.typography.labelSmall)
                        }
                    } else if (!action.syncedToGoogle) {
                        TintedGlassChip(
                            text = when {
                                action.category == ActionCategory.REMINDER.name -> "Notification scheduled"
                                action.isCompleted -> "Completed"
                                else -> "Active To-Do"
                            },
                            hue = if (action.isCompleted) glass.mintAccent else glass.violetAccent
                        )
                    }

                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(stringResource(R.string.action_edit), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (isDismissed) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onRestore,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("Restore to Review")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActionEditSheet(
    action: SmartActionEntity,
    onDismiss: () -> Unit,
    onSave: (SmartActionEntity) -> Unit
) {
    var title by remember(action) { mutableStateOf(action.title) }
    var notes by remember(action) { mutableStateOf(action.notes) }
    var category by remember(action) {
        mutableStateOf(
            runCatching { ActionCategory.valueOf(action.category) }.getOrDefault(ActionCategory.TODO)
        )
    }
    var priority by remember(action) {
        mutableStateOf(
            runCatching { ActionPriority.valueOf(action.priority) }.getOrDefault(ActionPriority.MEDIUM)
        )
    }

    val initialCal = remember(action) {
        Calendar.getInstance().apply {
            timeInMillis = action.suggestedTimeMillis ?: DateKeyUtil.buildSuggestedMillis(1, 9, 0)
        }
    }
    var dayOffset by remember(action) {
        val todayCal = Calendar.getInstance()
        val diffDays = ((initialCal.timeInMillis - todayCal.timeInMillis) / (24L * 3600L * 1000L)).toInt()
        mutableIntStateOf(diffDays.coerceIn(0, 7))
    }
    var hour24 by remember(action) { mutableIntStateOf(initialCal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember(action) { mutableIntStateOf(initialCal.get(Calendar.MINUTE)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (action.id == 0L) "Add Action" else "Modify & Approve Action",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_action_title_input")
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes / Context") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Category", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionCategory.entries.forEach { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        label = {
                            Text(
                                when (item) {
                                    ActionCategory.REMINDER -> "Reminder"
                                    ActionCategory.TODO -> "To-Do"
                                    ActionCategory.CALENDAR_EVENT -> "Calendar"
                                }
                            )
                        }
                    )
                }
            }

            Text("Priority", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPriority.entries.forEach { p ->
                    FilterChip(
                        selected = priority == p,
                        onClick = { priority = p },
                        label = { Text(p.name) }
                    )
                }
            }

            Text("Schedule Day", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Today", 1 to "Tomorrow", 2 to "In 2 Days", 7 to "Next Week").forEach { (offset, label) ->
                    FilterChip(
                        selected = dayOffset == offset,
                        onClick = { dayOffset = offset },
                        label = { Text(label) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val previewMillis = DateKeyUtil.buildSuggestedMillis(dayOffset, hour24, minute)
                Column {
                    Text("Time", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = DateKeyUtil.formatClockTime(previewMillis),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { hour24 = (hour24 + 23) % 24 },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) { Text("-1h") }
                    OutlinedButton(
                        onClick = { hour24 = (hour24 + 1) % 24 },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) { Text("+1h") }
                    OutlinedButton(
                        onClick = { minute = (minute + 15) % 60 },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) { Text("+15m") }
                }
            }

            Button(
                onClick = {
                    val cleanTitle = title.trim()
                    if (cleanTitle.isNotEmpty()) {
                        val scheduled = DateKeyUtil.buildSuggestedMillis(dayOffset, hour24, minute)
                        onSave(
                            action.copy(
                                title = cleanTitle,
                                notes = notes.trim(),
                                category = category.name,
                                priority = priority.name,
                                suggestedTimeMillis = scheduled,
                                endTimeMillis = if (category == ActionCategory.CALENDAR_EVENT) {
                                    scheduled + 30 * 60_000L
                                } else null
                            )
                        )
                    }
                },
                enabled = title.trim().isNotEmpty(),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("save_edited_action_button")
            ) {
                Text("Save & Approve")
            }
        }
    }
}
