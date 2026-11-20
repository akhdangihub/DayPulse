package com.example.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.CaptureEntryEntity
import com.example.data.local.CaptureType
import com.example.data.local.DateKeyUtil
import com.example.data.local.ProcessingStatus
import com.example.ui.theme.LocalMidnightGlass
import java.io.File
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun TimelineCaptureCard(
    entry: CaptureEntryEntity,
    onToggleBookmark: () -> Unit,
    onDelete: () -> Unit,
    onRetryTranscribe: () -> Unit = {},
    onEditTranscript: (String) -> Unit = {},
    detectedTaskCount: Int = 0,
    showTimelineRail: Boolean = true,
    isLastItem: Boolean = false,
    modifier: Modifier = Modifier
) {
    val glass = LocalMidnightGlass.current
    var expanded by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var draftTranscript by remember(entry.transcriptText) { mutableStateOf(entry.transcriptText) }

    val hasLocalAudio = entry.type == CaptureType.AUDIO.name &&
        !entry.audioDeleted &&
        entry.localMediaPath != null &&
        File(entry.localMediaPath).exists()

    val mediaPlayer = remember(entry.localMediaPath) {
        if (hasLocalAudio) MediaPlayer() else null
    }
    DisposableEffect(entry.localMediaPath) {
        onDispose {
            runCatching {
                if (mediaPlayer?.isPlaying == true) mediaPlayer.stop()
                mediaPlayer?.release()
            }
        }
    }

    val typeIcon = when (entry.type) {
        CaptureType.AUDIO.name -> Icons.Outlined.Mic
        CaptureType.PHOTO.name -> Icons.Outlined.PhotoCamera
        else -> Icons.Outlined.Notes
    }

    val statusText = when (entry.processingStatus) {
        ProcessingStatus.QUEUED_OFFLINE.name -> "Queued"
        ProcessingStatus.TRANSCRIBING.name -> "Transcribing"
        ProcessingStatus.FAILED.name -> "Failed"
        else -> "Done"
    }

    val statusColor = when (entry.processingStatus) {
        ProcessingStatus.QUEUED_OFFLINE.name -> glass.amberAccent
        ProcessingStatus.TRANSCRIBING.name -> glass.violetAccent
        ProcessingStatus.FAILED.name -> glass.coralAccent
        else -> glass.mintAccent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        if (showTimelineRail) {
            // Timeline Clock Marker & Vertical Line
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .padding(top = 6.dp)
            ) {
                Text(
                    text = DateKeyUtil.formatClockTime(entry.timestamp)
                        .replace(" AM", "a")
                        .replace(" PM", "p"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                if (!isLastItem) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .weight(1f)
                            .padding(top = 6.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        statusColor.copy(alpha = 0.45f),
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                    )
                                )
                            )
                    )
                }
            }
        }

        GlassContainer(
            tier = GlassTier.LEVEL_1,
            shape = RoundedCornerShape(24.dp),
            useBackdropBlur = false, // Protect 60fps list scrolling
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (showTimelineRail && !isLastItem) 14.dp else 0.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable { expanded = !expanded }
                .testTag("capture_card_${entry.id}")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = glass.violetAccent.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, glass.violetAccent.copy(alpha = 0.35f)),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = typeIcon,
                                    contentDescription = entry.type,
                                    modifier = Modifier.size(17.dp),
                                    tint = glass.violetAccent
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.aiCaption.ifBlank { "Captured Moment" },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    if (!showTimelineRail) {
                                        append(DateKeyUtil.formatDateTimeShort(entry.timestamp))
                                    } else if (entry.durationSeconds > 0) {
                                        append("Duration ${DateKeyUtil.formatDuration(entry.durationSeconds)}")
                                    } else {
                                        append(entry.type.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() })
                                    }
                                    if (entry.audioDeleted) append(" • Audio purged")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleBookmark,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("bookmark_capture_${entry.id}")
                        ) {
                            Icon(
                                imageVector = if (entry.isBookmarked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark capture",
                                tint = if (entry.isBookmarked) glass.violetAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_capture_${entry.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete capture",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Audio Mini-Waveform Preview
                if (entry.type == CaptureType.AUDIO.name && entry.durationSeconds > 0) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MiniAudioWaveformStrip(
                                seed = entry.id.toInt() + entry.durationSeconds,
                                tint = glass.violetAccent,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(22.dp)
                            )
                            Text(
                                text = DateKeyUtil.formatDuration(entry.durationSeconds),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Photo Thumbnail with Frosted Caption Bar
                if (entry.type == CaptureType.PHOTO.name && entry.localMediaPath != null && File(entry.localMediaPath).exists()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(18.dp))
                    ) {
                        AsyncImage(
                            model = File(entry.localMediaPath),
                            contentDescription = "Captured photo note",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0x990E1020),
                                            Color(0xDD0E1020)
                                        )
                                    )
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = entry.aiCaption.ifBlank { "Visual capture" },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFF2F3FA),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Transcript / Skeleton Shimmer / Error
                if (entry.transcriptText.isNotBlank()) {
                    Text(
                        text = entry.transcriptText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (expanded) 30 else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (entry.processingStatus == ProcessingStatus.TRANSCRIBING.name) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonShimmerBlock()
                        Text(
                            text = "Transcribing locally queued audio with Gemini…",
                            style = MaterialTheme.typography.labelSmall,
                            color = glass.violetAccent
                        )
                    }
                } else if (!entry.errorMessage.isNullOrBlank()) {
                    Text(
                        text = entry.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = glass.coralAccent
                    )
                } else {
                    Text(
                        text = "Saved locally in SQLite. Ready for transcription.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Bottom Status & Compact Action Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TintedGlassChip(
                            text = statusText,
                            hue = statusColor
                        )
                        if (detectedTaskCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = glass.violetAccent.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, glass.violetAccent.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = glass.violetAccent,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "$detectedTaskCount task",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = glass.violetAccent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (hasLocalAudio && mediaPlayer != null) {
                            TextButton(
                                onClick = {
                                    if (isPlaying) {
                                        runCatching { mediaPlayer.stop(); mediaPlayer.reset() }
                                        isPlaying = false
                                    } else {
                                        runCatching {
                                            mediaPlayer.reset()
                                            mediaPlayer.setDataSource(entry.localMediaPath)
                                            mediaPlayer.prepare()
                                            mediaPlayer.setOnCompletionListener { isPlaying = false }
                                            mediaPlayer.start()
                                            isPlaying = true
                                        }.onFailure { isPlaying = false }
                                    }
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                                    contentDescription = if (isPlaying) "Stop playback" else "Play recording",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (isPlaying) "Stop" else "Play", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        if (entry.processingStatus == ProcessingStatus.QUEUED_OFFLINE.name ||
                            entry.processingStatus == ProcessingStatus.FAILED.name
                        ) {
                            TextButton(
                                onClick = onRetryTranscribe,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("retry_capture_${entry.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = "Retry transcription",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (entry.processingStatus == ProcessingStatus.FAILED.name) "Retry" else "Sync",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                draftTranscript = entry.transcriptText
                                showEditDialog = true
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Edit transcript",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Edit", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Edit Capture Text") },
            text = {
                OutlinedTextField(
                    value = draftTranscript,
                    onValueChange = { draftTranscript = it },
                    label = { Text("Transcript / Note") },
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEditTranscript(draftTranscript)
                        showEditDialog = false
                    },
                    enabled = draftTranscript.trim().isNotEmpty(),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MiniAudioWaveformStrip(
    seed: Int,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val count = 24
        val barW = size.width / (count * 2.1f)
        val gap = (size.width - count * barW) / (count - 1).coerceAtLeast(1)
        val midY = size.height / 2f
        for (i in 0 until count) {
            val factor = 0.25f + 0.72f * abs(sin((seed * 0.37f) + i * 0.65f))
            val h = (size.height * factor).coerceAtLeast(4.dp.toPx())
            val x = i * (barW + gap)
            drawRoundRect(
                color = tint.copy(alpha = 0.55f + 0.35f * factor),
                topLeft = Offset(x, midY - h / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }
    }
}
