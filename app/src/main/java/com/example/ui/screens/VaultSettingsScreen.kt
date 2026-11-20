package com.example.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CaptureEntryEntity
import com.example.data.local.DailySummaryEntity
import com.example.data.local.DateKeyUtil
import com.example.data.prefs.AppThemeMode
import com.example.data.prefs.PrivacyPreferences
import com.example.ui.components.GlassContainer
import com.example.ui.components.GlassTier
import com.example.ui.components.PriorityGlassPill
import com.example.ui.components.TimelineCaptureCard
import com.example.ui.components.TintedGlassChip
import com.example.ui.theme.DayPulseSpacing
import com.example.ui.theme.LocalMidnightGlass
import com.example.widget.DayHubWidgetReceiver
import com.example.widget.QuickAccessWidgetReceiver
import com.example.widget.TaskApprovalWidgetReceiver

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultSettingsScreen(
    searchQuery: String,
    searchResults: List<CaptureEntryEntity>,
    summaries: List<DailySummaryEntity>,
    preferences: PrivacyPreferences,
    onSearchQueryChange: (String) -> Unit,
    onToggleAutoDeleteAudio: (Boolean) -> Unit,
    onSegmentDurationChange: (Int) -> Unit,
    onToggleWifiOnly: (Boolean) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onToggleGlassEffects: (Boolean) -> Unit,
    onEveningReminderChange: (Boolean, Int, Int) -> Unit,
    onPurgeLocalAudio: () -> Unit,
    onOpenSummaryDate: (String) -> Unit,
    onToggleBookmark: (CaptureEntryEntity) -> Unit,
    onDeleteCapture: (CaptureEntryEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalMidnightGlass.current
    var showPurgeConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = DayPulseSpacing.md,
                end = DayPulseSpacing.md,
                top = 16.dp,
                bottom = DayPulseSpacing.bottomPillClearance
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.vault_title),
                    style = MaterialTheme.typography.displaySmall
                )
            }

            // Frosted Search Bar
            item {
                GlassContainer(
                    tier = GlassTier.LEVEL_2,
                    shape = RoundedCornerShape(26.dp),
                    useBackdropBlur = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(R.string.vault_search_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_search_input")
                    )
                }
            }

            if (searchQuery.isNotBlank()) {
                item {
                    Text(
                        text = "Matching Transcripts (${searchResults.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(searchResults, key = { "search_${it.id}" }) { entry ->
                    TimelineCaptureCard(
                        entry = entry,
                        showTimelineRail = false,
                        onToggleBookmark = { onToggleBookmark(entry) },
                        onDelete = { onDeleteCapture(entry) }
                    )
                }
            }

            // 1. Appearance & Glassmorphism Accessibility Card
            item {
                val activeThemeMode = preferences.resolvedThemeMode()
                SettingsGlassCard(
                    title = "Appearance & Glass",
                    icon = Icons.Outlined.AutoAwesome,
                    iconTint = glass.violetAccent
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Theme Mode",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = activeThemeMode == mode,
                                    onClick = { onThemeModeChange(mode) },
                                    shape = RoundedCornerShape(50),
                                    label = { Text(mode.label) },
                                    modifier = Modifier.testTag("theme_chip_${mode.name.lowercase()}")
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    SettingsSwitchRow(
                        title = "Glassmorphism & Backdrop Blur",
                        description = "Turn off for higher-contrast solid tonal surfaces and reduced motion.",
                        checked = preferences.glassEffectsEnabled,
                        onCheckedChange = onToggleGlassEffects,
                        testTag = "switch_glass_effects"
                    )
                }
            }

            // 2. Capture Settings Card
            item {
                SettingsGlassCard(
                    title = "Capture",
                    icon = Icons.Outlined.Mic,
                    iconTint = glass.coralAccent
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Audio Chunk Length",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Automatically splits long recordings for fast per-chunk transcription.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2, 5, 10, 15).forEach { mins ->
                                FilterChip(
                                    selected = preferences.segmentDurationMinutes == mins,
                                    onClick = { onSegmentDurationChange(mins) },
                                    shape = RoundedCornerShape(50),
                                    label = { Text("${mins}m") }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Privacy & Storage Card
            item {
                SettingsGlassCard(
                    title = stringResource(R.string.vault_privacy_section),
                    icon = Icons.Outlined.Shield,
                    iconTint = glass.mintAccent
                ) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.vault_auto_delete_title),
                        description = stringResource(R.string.vault_auto_delete_desc),
                        checked = preferences.autoDeleteAudioAfterTranscribe,
                        onCheckedChange = onToggleAutoDeleteAudio,
                        testTag = "switch_auto_delete_audio"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    SettingsSwitchRow(
                        title = stringResource(R.string.vault_wifi_only_title),
                        description = stringResource(R.string.vault_wifi_only_desc),
                        checked = preferences.wifiOnlyProcessing,
                        onCheckedChange = onToggleWifiOnly,
                        testTag = "switch_wifi_only"
                    )

                    OutlinedButton(
                        onClick = { showPurgeConfirm = true },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("purge_audio_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = glass.coralAccent
                        )
                    ) {
                        Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Purge Saved Local Audio Files")
                    }
                }
            }

            // 4. Review Time Card
            item {
                SettingsGlassCard(
                    title = "Review Time",
                    icon = Icons.Outlined.Schedule,
                    iconTint = glass.amberAccent
                ) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.vault_evening_reminder_title),
                        description = stringResource(R.string.vault_evening_reminder_desc),
                        checked = preferences.eveningReminderEnabled,
                        onCheckedChange = { enabled ->
                            onEveningReminderChange(
                                enabled,
                                preferences.eveningReviewHour,
                                preferences.eveningReviewMinute
                            )
                        },
                        testTag = "switch_evening_reminder"
                    )

                    if (preferences.eveningReminderEnabled) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(19 to "7:00 PM", 20 to "8:00 PM", 21 to "9:00 PM", 22 to "10:00 PM").forEach { (hr, label) ->
                                FilterChip(
                                    selected = preferences.eveningReviewHour == hr,
                                    onClick = { onEveningReminderChange(true, hr, 0) },
                                    shape = RoundedCornerShape(50),
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }

            // 5. Home Screen Widgets with Live Midnight Indigo Previews + Pin Buttons
            item {
                val context = LocalContext.current
                val pinWidget: (Class<*>, String) -> Unit = { receiverClass, name ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val mgr = AppWidgetManager.getInstance(context)
                        if (mgr.isRequestPinAppWidgetSupported) {
                            mgr.requestPinAppWidget(ComponentName(context, receiverClass), null, null)
                        } else {
                            Toast.makeText(context, "Long-press your Home screen to add $name.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Long-press your Home screen to add $name.", Toast.LENGTH_SHORT).show()
                    }
                }

                SettingsGlassCard(
                    title = "Home Screen Widgets",
                    icon = Icons.Outlined.Widgets,
                    iconTint = glass.violetAccent
                ) {
                    WidgetPreviewOptionRow(
                        title = "Quick Bar (4x1)",
                        description = "Record / Save Chunk / Stop, Review count & Note",
                        onPin = { pinWidget(QuickAccessWidgetReceiver::class.java, "Quick Bar (4x1)") },
                        pinTag = "pin_widget_0"
                    ) {
                        WidgetMiniPreviewQuickBar()
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    WidgetPreviewOptionRow(
                        title = "Task Approval (4x2)",
                        description = "Priority-sorted queue with 1-tap Approve, +Cal & Dismiss",
                        onPin = { pinWidget(TaskApprovalWidgetReceiver::class.java, "Task Approval (4x2)") },
                        pinTag = "pin_widget_1"
                    ) {
                        WidgetMiniPreviewTaskApproval()
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    WidgetPreviewOptionRow(
                        title = "Hub & Schedule (4x3)",
                        description = "Quick bar, approvals queue & today’s calendar schedule",
                        onPin = { pinWidget(DayHubWidgetReceiver::class.java, "Hub & Schedule (4x3)") },
                        pinTag = "pin_widget_2"
                    ) {
                        WidgetMiniPreviewHub()
                    }
                }
            }

            // Archived Daily Summaries
            item {
                Text(
                    text = "Archived Daily Summaries",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (summaries.isEmpty()) {
                item {
                    Text(
                        text = "Your Quick Review summaries will be archived here for offline lookup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(summaries, key = { "sum_${it.dateKey}" }) { summary ->
                    GlassContainer(
                        tier = GlassTier.LEVEL_1,
                        shape = RoundedCornerShape(24.dp),
                        useBackdropBlur = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onOpenSummaryDate(summary.dateKey) }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = DateKeyUtil.formatDateKeyLabel(summary.dateKey),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                TintedGlassChip(
                                    text = summary.moodTag,
                                    hue = glass.violetAccent
                                )
                            }
                            Text(
                                text = summary.narrativeSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showPurgeConfirm) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirm = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Delete raw audio files?") },
            text = { Text("Your SQLite transcripts, summaries, and tasks will remain intact. Only raw .m4a recordings on disk will be removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        onPurgeLocalAudio()
                        showPurgeConfirm = false
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Audio")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsGlassCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    content: @Composable () -> Unit
) {
    GlassContainer(
        tier = GlassTier.LEVEL_1,
        shape = RoundedCornerShape(24.dp),
        useBackdropBlur = false,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun WidgetPreviewOptionRow(
    title: String,
    description: String,
    onPin: () -> Unit,
    pinTag: String,
    preview: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(
                onClick = onPin,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(pinTag)
            ) {
                Text("Pin")
            }
        }
        preview()
    }
}

@Composable
private fun WidgetMiniPreviewQuickBar() {
    val glass = LocalMidnightGlass.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Record", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(glass.violetAccent.copy(alpha = 0.18f))
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Review (2)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Note", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun WidgetMiniPreviewTaskApproval() {
    val glass = LocalMidnightGlass.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PriorityGlassPill(priority = "HIGH")
                Text(
                    text = "Client sync • 3:00 PM",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text("Approve", style = MaterialTheme.typography.labelSmall, color = glass.mintAccent)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PriorityGlassPill(priority = "MED")
                Text(
                    text = "Send release notes",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text("+Cal", style = MaterialTheme.typography.labelSmall, color = glass.violetAccent)
            }
        }
    }
}

@Composable
private fun WidgetMiniPreviewHub() {
    val glass = LocalMidnightGlass.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("DayPulse Hub", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("2 captures • Synced", style = MaterialTheme.typography.labelSmall, color = glass.mintAccent)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PriorityGlassPill(priority = "LOW")
                Text(
                    text = "Review backlog items",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("Done", style = MaterialTheme.typography.labelSmall, color = glass.mintAccent)
            }
        }
    }
}
