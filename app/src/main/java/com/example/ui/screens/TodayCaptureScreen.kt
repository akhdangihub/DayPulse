package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.CaptureEntryEntity
import com.example.data.local.DateKeyUtil
import com.example.data.local.ReviewStatus
import com.example.data.local.SmartActionEntity
import com.example.data.prefs.PrivacyPreferences
import com.example.service.RecordingServiceState
import com.example.ui.components.GlassContainer
import com.example.ui.components.GlassTier
import com.example.ui.components.TimelineCaptureCard
import com.example.ui.components.TodayCaptureHeroPanel
import com.example.ui.components.TodayEmptyStateIllustration
import com.example.ui.theme.DayPulseSpacing
import com.example.ui.theme.LocalMidnightGlass

@Composable
fun TodayCaptureScreen(
    captures: List<CaptureEntryEntity>,
    todayActions: List<SmartActionEntity>,
    recordingState: RecordingServiceState,
    queuedCount: Int,
    isOnline: Boolean,
    preferences: PrivacyPreferences,
    autoStartCaptureTrigger: Long = 0L,
    autoOpenNoteTrigger: Long = 0L,
    onStartCapture: () -> Unit,
    onPauseCapture: () -> Unit,
    onResumeCapture: () -> Unit,
    onSplitSegment: () -> Unit,
    onStopCapture: () -> Unit,
    onAddQuickNote: (String) -> Unit,
    onImportPhoto: (android.net.Uri) -> Unit,
    onSyncQueue: () -> Unit,
    onRetryCapture: (CaptureEntryEntity) -> Unit,
    onEditTranscript: (CaptureEntryEntity, String) -> Unit,
    onToggleBookmark: (CaptureEntryEntity) -> Unit,
    onDeleteCapture: (CaptureEntryEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val glass = LocalMidnightGlass.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasMicPermission = granted
            if (granted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                onStartCapture()
            }
        }
    )

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) onImportPhoto(uri)
        }
    )

    var showQuickNoteDialog by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }

    LaunchedEffect(autoStartCaptureTrigger) {
        if (autoStartCaptureTrigger > 0L) {
            if (hasMicPermission) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                onStartCapture()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LaunchedEffect(autoOpenNoteTrigger) {
        if (autoOpenNoteTrigger > 0L) showQuickNoteDialog = true
    }

    val activeActions = remember(todayActions) {
        todayActions.filter { it.reviewStatus != ReviewStatus.DISMISSED.name }
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
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = DateKeyUtil.formatDateKeyLabel(DateKeyUtil.todayDateKey()),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Calm voice capture, visual thoughts, and daily stream.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (queuedCount > 0 || !isOnline) {
                item {
                    GlassContainer(
                        tier = GlassTier.LEVEL_1,
                        shape = RoundedCornerShape(22.dp),
                        accentGlow = if (isOnline) glass.violetAccent else glass.amberAccent,
                        useBackdropBlur = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isOnline) Icons.Outlined.Sync else Icons.Outlined.CloudOff,
                                    contentDescription = null,
                                    tint = if (isOnline) glass.violetAccent else glass.amberAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (!isOnline) {
                                        "Offline • $queuedCount item(s) saved safely"
                                    } else {
                                        "$queuedCount queued capture(s) ready"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (queuedCount > 0 && isOnline) {
                                TextButton(
                                    onClick = onSyncQueue,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("sync_queue_button")
                                ) {
                                    Text(stringResource(R.string.capture_process_queue))
                                }
                            }
                        }
                    }
                }
            }

            item {
                TodayCaptureHeroPanel(
                    recordingState = recordingState,
                    isOnline = isOnline,
                    autoDeleteAudio = preferences.autoDeleteAudioAfterTranscribe,
                    hasMicPermission = hasMicPermission,
                    onRequestMicPermission = {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStart = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        onStartCapture()
                    },
                    onPause = onPauseCapture,
                    onResume = onResumeCapture,
                    onSplitSegment = onSplitSegment,
                    onStop = onStopCapture
                )
            }

            // Compact Outlined / Tonal Secondary Capture Pills
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showQuickNoteDialog = true },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("quick_note_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.capture_add_note))
                    }

                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("scan_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.capture_add_photo))
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Day Stream",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${captures.size} item(s)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (captures.isEmpty()) {
                item {
                    TodayEmptyStateIllustration(
                        hasMicPermission = hasMicPermission,
                        onPrimaryAction = {
                            if (hasMicPermission) {
                                onStartCapture()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                }
            } else {
                itemsIndexed(captures, key = { _, item -> item.id }) { index, entry ->
                    val detectedCount = remember(entry.transcriptText, activeActions) {
                        if (entry.transcriptText.isBlank()) {
                            0
                        } else {
                            activeActions.count { act ->
                                act.sourceExcerpt.length >= 6 &&
                                    entry.transcriptText.contains(act.sourceExcerpt, ignoreCase = true)
                            }
                        }
                    }

                    TimelineCaptureCard(
                        entry = entry,
                        detectedTaskCount = detectedCount,
                        showTimelineRail = true,
                        isLastItem = index == captures.lastIndex,
                        onToggleBookmark = { onToggleBookmark(entry) },
                        onDelete = { onDeleteCapture(entry) },
                        onRetryTranscribe = { onRetryCapture(entry) },
                        onEditTranscript = { newText -> onEditTranscript(entry, newText) }
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showQuickNoteDialog) {
        AlertDialog(
            onDismissRequest = { showQuickNoteDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Add Offline Note") },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    label = { Text("What happened or what do you need to remember?") },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_note_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddQuickNote(noteDraft)
                        noteDraft = ""
                        showQuickNoteDialog = false
                    },
                    enabled = noteDraft.trim().isNotEmpty(),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("save_quick_note_button")
                ) {
                    Text("Save to Vault")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickNoteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
