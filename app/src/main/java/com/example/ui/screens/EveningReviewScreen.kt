package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DayAnalyticsUiState
import com.example.data.GoogleWorkspaceSyncService
import com.example.data.local.DailySummaryEntity
import com.example.data.local.DateKeyUtil
import com.example.data.local.SmartActionEntity
import com.example.ui.components.ActionEditSheet
import com.example.ui.components.DailyNarrativeGlassCard
import com.example.ui.components.GlassContainer
import com.example.ui.components.GlassTier
import com.example.ui.components.KeyMomentsCarousel
import com.example.ui.components.OpenLoopsChecklistCard
import com.example.ui.components.QuickReviewDualSummaryCard
import com.example.ui.components.SmartActionTriageCard
import com.example.ui.components.SwipeableSmartActionCard
import com.example.ui.components.TintedGlassChip
import com.example.ui.theme.DayPulseSpacing
import com.example.ui.theme.LocalMidnightGlass
import com.example.ui.viewmodel.EveningSynthesisUiState
import com.google.android.gms.auth.api.identity.Identity

@Composable
fun EveningReviewScreen(
    selectedDateKey: String,
    availableDateKeys: List<String>,
    newCapturesCount: Int,
    summary: DailySummaryEntity?,
    dayAnalytics: DayAnalyticsUiState,
    pendingActions: List<SmartActionEntity>,
    acceptedActions: List<SmartActionEntity>,
    dismissedActions: List<SmartActionEntity>,
    isGoogleConnected: Boolean,
    synthesisState: EveningSynthesisUiState,
    editingAction: SmartActionEntity?,
    onSelectDate: (String) -> Unit,
    onSynthesizeReview: (Boolean) -> Unit,
    onAcceptAction: (SmartActionEntity) -> Unit,
    onEditAction: (SmartActionEntity) -> Unit,
    onAddManualAction: () -> Unit,
    onDismissAction: (SmartActionEntity) -> Unit,
    onRestoreAction: (SmartActionEntity) -> Unit,
    onToggleCompleted: (SmartActionEntity) -> Unit,
    onSyncActionToGoogle: (SmartActionEntity) -> Unit,
    onSyncAllApprovedToGoogle: () -> Unit,
    onNotifyMessage: (String) -> Unit,
    onCloseEditSheet: () -> Unit,
    onSaveEditedAction: (SmartActionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val glass = LocalMidnightGlass.current
    var showDismissed by remember { mutableStateOf(false) }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && activity != null) {
            val ok = GoogleWorkspaceSyncService.handleActivityResult(activity, res.data)
            onNotifyMessage(
                if (ok) "Connected to Google Calendar & Tasks." else "Google authorization did not return an access token."
            )
        } else {
            onNotifyMessage("Google authorization canceled.")
        }
    }

    fun startGoogleConnect() {
        if (activity == null) return
        Identity.getAuthorizationClient(activity)
            .authorize(GoogleWorkspaceSyncService.buildAuthorizationRequest())
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    result.pendingIntent?.let { pi ->
                        authLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    }
                } else {
                    GoogleWorkspaceSyncService.handleAuthorizationResult(result)
                    onNotifyMessage("Connected to Google Calendar & Tasks.")
                }
            }
            .addOnFailureListener { err ->
                onNotifyMessage(
                    "Google OAuth unavailable (${err.localizedMessage ?: "check Play Services"}). Use Calendar App button."
                )
            }
    }

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
            // Header + Delta Badge + Date Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.evening_title),
                            style = MaterialTheme.typography.displaySmall
                        )
                        TintedGlassChip(
                            text = if (newCapturesCount > 0) "$newCapturesCount new ready" else "Up to date",
                            hue = if (newCapturesCount > 0) glass.violetAccent else glass.mintAccent
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableDateKeys.forEach { key ->
                            FilterChip(
                                selected = key == selectedDateKey,
                                onClick = { onSelectDate(key) },
                                shape = RoundedCornerShape(50),
                                label = { Text(DateKeyUtil.formatDateKeyLabel(key)) }
                            )
                        }
                    }
                }
            }

            // Primary Solid Quick Review CTA + New Manual Action
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onSynthesizeReview(false) },
                        enabled = synthesisState !is EveningSynthesisUiState.Loading,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("synthesize_evening_button")
                    ) {
                        if (synthesisState is EveningSynthesisUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Summarizing…")
                        } else {
                            Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when {
                                    summary == null -> stringResource(R.string.evening_generate_cta)
                                    newCapturesCount > 0 -> "Summarize $newCapturesCount New"
                                    else -> stringResource(R.string.evening_regenerate_cta)
                                }
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onAddManualAction,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("add_manual_action_button")
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New")
                    }
                }
            }

            // Google Calendar & Tasks Integration Card (Level 1 Glass)
            item {
                val unsyncedApprovedCount = acceptedActions.count { !it.syncedToGoogle }
                GlassContainer(
                    tier = GlassTier.LEVEL_1,
                    shape = RoundedCornerShape(24.dp),
                    useBackdropBlur = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isGoogleConnected) Icons.Rounded.CheckCircle else Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = if (isGoogleConnected) glass.mintAccent else glass.violetAccent,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = if (isGoogleConnected) "Google Calendar & Tasks Ready" else "Google Calendar & Tasks",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (isGoogleConnected) {
                                        if (unsyncedApprovedCount > 0) "$unsyncedApprovedCount item(s) waiting to sync" else "Approved items auto-sync"
                                    } else {
                                        "Connect to sync approved tasks & schedules"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!isGoogleConnected) {
                            FilledTonalButton(
                                onClick = { startGoogleConnect() },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("connect_google_workspace_button")
                            ) {
                                Text("Connect")
                            }
                        } else if (unsyncedApprovedCount > 0) {
                            FilledTonalButton(
                                onClick = onSyncAllApprovedToGoogle,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("sync_all_google_button")
                            ) {
                                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Sync ($unsyncedApprovedCount)")
                            }
                        }
                    }
                }
            }

            // Dual Summary Card: Activity Time Donut + Tasks Completed Bar Chart (Restyled in Midnight Indigo Glass)
            item {
                QuickReviewDualSummaryCard(
                    analytics = dayAnalytics,
                    selectedDateKey = selectedDateKey
                )
            }

            if (synthesisState is EveningSynthesisUiState.Error) {
                item {
                    GlassContainer(
                        tier = GlassTier.LEVEL_1,
                        shape = RoundedCornerShape(20.dp),
                        accentGlow = glass.coralAccent,
                        useBackdropBlur = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = glass.coralAccent
                            )
                            Text(
                                text = synthesisState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (summary != null) {
                item {
                    DailyNarrativeGlassCard(
                        summary = summary,
                        onForceFullRebuild = { onSynthesizeReview(true) }
                    )
                }

                val highlights = summary.highlightsSerialized.lines().filter { it.isNotBlank() }
                if (highlights.isNotEmpty()) {
                    item {
                        KeyMomentsCarousel(highlights = highlights)
                    }
                }

                val openLoops = summary.openLoopsSerialized.lines().filter { it.isNotBlank() }
                if (openLoops.isNotEmpty()) {
                    item {
                        OpenLoopsChecklistCard(
                            dateKey = selectedDateKey,
                            openLoops = openLoops
                        )
                    }
                }
            } else {
                item {
                    GlassContainer(
                        tier = GlassTier.LEVEL_1,
                        shape = RoundedCornerShape(26.dp),
                        useBackdropBlur = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = glass.violetAccent
                            )
                            Text(
                                text = stringResource(R.string.evening_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.evening_empty_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (pendingActions.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.evening_pending_section),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Swipe to triage • ${pendingActions.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = glass.violetAccent
                        )
                    }
                }

                items(pendingActions, key = { "pending_${it.id}" }) { action ->
                    SwipeableSmartActionCard(
                        action = action,
                        isGoogleConnected = isGoogleConnected,
                        onAccept = { onAcceptAction(action) },
                        onEdit = { onEditAction(action) },
                        onDismiss = { onDismissAction(action) },
                        onToggleComplete = { onToggleCompleted(action) },
                        onAddToCalendar = { launchCalendarInsert(context, action) },
                        onSyncToGoogle = { onSyncActionToGoogle(action) },
                        onRestore = { onRestoreAction(action) }
                    )
                }
            }

            if (acceptedActions.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.evening_accepted_section),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                items(acceptedActions, key = { "accepted_${it.id}" }) { action ->
                    SmartActionTriageCard(
                        action = action,
                        isGoogleConnected = isGoogleConnected,
                        onAccept = {},
                        onEdit = { onEditAction(action) },
                        onDismiss = { onDismissAction(action) },
                        onToggleComplete = { onToggleCompleted(action) },
                        onAddToCalendar = { launchCalendarInsert(context, action) },
                        onSyncToGoogle = { onSyncActionToGoogle(action) },
                        onRestore = { onRestoreAction(action) }
                    )
                }
            }

            if (dismissedActions.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stringResource(R.string.evening_dismissed_section)} (${dismissedActions.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { showDismissed = !showDismissed }) {
                            Text(if (showDismissed) "Hide" else "Show")
                        }
                    }
                }

                if (showDismissed) {
                    items(dismissedActions, key = { "dismissed_${it.id}" }) { action ->
                        AnimatedVisibility(visible = true) {
                            SmartActionTriageCard(
                                action = action,
                                isGoogleConnected = isGoogleConnected,
                                onAccept = {},
                                onEdit = {},
                                onDismiss = {},
                                onToggleComplete = {},
                                onAddToCalendar = {},
                                onSyncToGoogle = {},
                                onRestore = { onRestoreAction(action) }
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (editingAction != null) {
        ActionEditSheet(
            action = editingAction,
            onDismiss = onCloseEditSheet,
            onSave = onSaveEditedAction
        )
    }
}

private fun launchCalendarInsert(context: Context, action: SmartActionEntity) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
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
    runCatching { context.startActivity(intent) }
}
