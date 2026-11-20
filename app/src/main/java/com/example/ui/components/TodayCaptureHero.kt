package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.DateKeyUtil
import com.example.service.CaptureServiceStatus
import com.example.service.RecordingServiceState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalMidnightGlass
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun TodayCaptureHeroPanel(
    recordingState: RecordingServiceState,
    isOnline: Boolean,
    autoDeleteAudio: Boolean,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSplitSegment: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalMidnightGlass.current
    val haptics = LocalHapticFeedback.current
    val isRecording = recordingState.status == CaptureServiceStatus.RECORDING
    val isPaused = recordingState.status == CaptureServiceStatus.PAUSED
    val isActive = isRecording || isPaused
    val isSaving = recordingState.isSavingChunk

    val orbColor by animateColorAsState(
        targetValue = when {
            isRecording -> glass.coralAccent
            isPaused -> glass.amberAccent
            else -> glass.violetAccent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "hero_orb_color"
    )
    val orbSecondary by animateColorAsState(
        targetValue = when {
            isRecording -> glass.coralAccent.copy(alpha = 0.78f)
            isPaused -> glass.amberAccent.copy(alpha = 0.78f)
            else -> glass.violetAccent.copy(alpha = 0.78f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "hero_orb_secondary"
    )

    val infinite = rememberInfiniteTransition(label = "hero_wave_pulse")
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hero_phase"
    )
    val idleBreath by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_breath"
    )
    val reactiveAmplitude by animateFloatAsState(
        targetValue = if (isRecording) recordingState.amplitude.coerceIn(0.14f, 1f) else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "reactive_amp"
    )
    val haloScale = if (isRecording) {
        1.06f + reactiveAmplitude * 0.24f
    } else {
        idleBreath
    }

    val panelGlow = when {
        isRecording -> glass.coralAccent
        isPaused -> glass.amberAccent
        else -> glass.violetAccent
    }

    GlassContainer(
        tier = GlassTier.LEVEL_2,
        shape = RoundedCornerShape(28.dp),
        accentGlow = panelGlow,
        useBackdropBlur = true,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!hasMicPermission) {
                // Friendly inline permission card
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = glass.violetAccent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, glass.violetAccent.copy(alpha = 0.45f)),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = null,
                                tint = glass.violetAccent,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Text(
                        text = "Microphone access for voice capture",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.capture_mic_permission_rationale),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onRequestMicPermission,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("grant_mic_button")
                    ) {
                        Icon(Icons.Outlined.Mic, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.capture_mic_permission_grant))
                    }
                }
            } else {
                // Hero Record Orb (~96dp) with outer glow ring & inner gradient
                val heroTag = when {
                    isRecording -> "pause_resume_capture_button"
                    isPaused -> "pause_resume_capture_button"
                    else -> "start_capture_button"
                }
                val heroLabel = when {
                    isSaving -> "Saving audio chunk"
                    isRecording -> "Pause recording"
                    isPaused -> "Resume recording"
                    else -> stringResource(R.string.capture_start_all_day)
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(132.dp)
                ) {
                    // Outer pulsing halo ring
                    Canvas(
                        modifier = Modifier
                            .size(128.dp)
                            .scale(haloScale)
                    ) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    orbColor.copy(alpha = if (isRecording) 0.42f else 0.28f),
                                    orbColor.copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                        drawCircle(
                            color = orbColor.copy(alpha = if (isRecording) 0.55f else 0.32f),
                            radius = size.minDimension / 2f - 6.dp.toPx(),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    // Solid ~96dp Hero Orb CTA (stays solid over glass!)
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(orbColor, orbSecondary)
                                )
                            )
                            .clickable(
                                enabled = !isSaving,
                                role = Role.Button,
                                onClickLabel = heroLabel
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                when {
                                    isRecording -> onPause()
                                    isPaused -> onResume()
                                    else -> onStart()
                                }
                            }
                            .semantics { contentDescription = heroLabel }
                            .testTag(heroTag),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = Triple(isSaving, isRecording, isPaused),
                            transitionSpec = {
                                (scaleIn(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn()) togetherWith
                                    (scaleOut() + fadeOut())
                            },
                            label = "hero_orb_icon"
                        ) { (saving, rec, paused) ->
                            val iconTint = when {
                                rec -> MaterialTheme.colorScheme.onTertiary
                                paused -> glass.amberAccent.let { Color(0xFF1F1200) }
                                else -> MaterialTheme.colorScheme.onPrimary
                            }
                            when {
                                saving -> CircularProgressIndicator(
                                    color = iconTint,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(34.dp)
                                )
                                rec -> Icon(
                                    imageVector = Icons.Rounded.Pause,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(40.dp)
                                )
                                paused -> Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(42.dp)
                                )
                                else -> Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                }

                // Big Mono Timer & Single-Line Status
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = DateKeyUtil.formatDuration(recordingState.segmentElapsedSeconds),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        ),
                        color = if (isRecording) glass.coralAccent else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            isSaving -> "Saving audio chunk locally…"
                            isRecording -> "Listening locally • Auto-chunk ${recordingState.segmentDurationMinutes}m"
                            isPaused -> "Capture paused • Tap orb to resume"
                            else -> "Tap the orb to start capturing your day"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }

                // Live Amplitude Waveform when active
                AnimatedVisibility(
                    visible = isActive,
                    enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val activeBarColor = if (isRecording) glass.coralAccent else glass.amberAccent
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        val barCount = 34
                        val barW = size.width / (barCount * 1.9f)
                        val gap = (size.width - barCount * barW) / (barCount - 1).coerceAtLeast(1)
                        val centerY = size.height / 2f
                        val amp = if (isRecording) reactiveAmplitude else 0.14f

                        for (i in 0 until barCount) {
                            val x = i * (barW + gap)
                            val factor = if (isRecording) {
                                (0.35f + 0.65f * abs(sin(wavePhase + i * 0.45f))) * amp
                            } else {
                                0.18f
                            }
                            val h = (size.height * factor).coerceAtLeast(5.dp.toPx())
                            drawRoundRect(
                                color = activeBarColor,
                                topLeft = Offset(x, centerY - h / 2f),
                                size = Size(barW, h),
                                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                            )
                        }
                    }
                }

                // Secondary Controls revealed during active capture
                AnimatedVisibility(
                    visible = isActive,
                    enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSplitSegment()
                            },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("split_segment_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Splitscreen,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.capture_save_segment), maxLines = 1)
                        }

                        FilledTonalButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isPaused) onResume() else onPause()
                            },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                                contentDescription = stringResource(
                                    if (isPaused) R.string.capture_resume else R.string.capture_pause
                                ),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStop()
                            },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = glass.coralAccent,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("stop_capture_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Stop,
                                contentDescription = stringResource(R.string.capture_stop),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.capture_stop), maxLines = 1)
                        }
                    }
                }

                // Single Subtle Privacy / Connectivity Pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = if (isOnline) glass.mintAccent else glass.amberAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = buildString {
                                append("Local SQLite")
                                if (autoDeleteAudio) append(" • Auto-purge")
                                append(if (isOnline) " • Online" else " • Offline")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                if (recordingState.errorMessage != null) {
                    Text(
                        text = recordingState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = glass.coralAccent,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun TodayEmptyStateIllustration(
    hasMicPermission: Boolean,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalMidnightGlass.current
    val infinite = rememberInfiniteTransition(label = "empty_sunrise")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sunrise_wave"
    )

    val violet = glass.violetAccent
    val coral = glass.coralAccent
    val mint = glass.mintAccent
    val amber = glass.amberAccent

    GlassContainer(
        tier = GlassTier.LEVEL_1,
        shape = RoundedCornerShape(26.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Animated Compose Canvas Sunrise + Abstract Waveform Illustration
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
            ) {
                val cx = size.width / 2f
                val horizonY = size.height * 0.72f
                val sunR = 26.dp.toPx()

                // Soft glowing sunrise aura
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            amber.copy(alpha = 0.42f),
                            coral.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        center = Offset(cx, horizonY - 6.dp.toPx()),
                        radius = sunR * 2.2f
                    ),
                    radius = sunR * 2.2f,
                    center = Offset(cx, horizonY - 6.dp.toPx())
                )

                // Sun arc rising above horizon
                drawArc(
                    brush = Brush.verticalGradient(listOf(amber, coral)),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(cx - sunR, horizonY - sunR),
                    size = Size(sunR * 2f, sunR * 2f)
                )

                // Delicate horizon ring line
                drawLine(
                    color = violet.copy(alpha = 0.45f),
                    start = Offset(size.width * 0.12f, horizonY),
                    end = Offset(size.width * 0.88f, horizonY),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Drifting abstract audio waveform bars across the sunrise horizon
                val bars = 19
                val span = size.width * 0.64f
                val startX = (size.width - span) / 2f
                val step = span / (bars - 1)
                for (i in 0 until bars) {
                    val distFromCenter = abs(i - (bars - 1) / 2f) / ((bars - 1) / 2f)
                    val envelope = 1f - distFromCenter * 0.72f
                    val wave = 0.45f + 0.55f * abs(sin(drift + i * 0.55f))
                    val barH = (32.dp.toPx() * envelope * wave).coerceAtLeast(6.dp.toPx())
                    val x = startX + i * step
                    val color = when {
                        i % 3 == 0 -> violet
                        i % 3 == 1 -> mint
                        else -> coral
                    }.copy(alpha = 0.85f)
                    drawLine(
                        color = color,
                        start = Offset(x, horizonY - barH * 0.5f),
                        end = Offset(x, horizonY + barH * 0.5f),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Text(
                text = "Your Midnight Ledger is open",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Capture a calm voice memo or note—DayPulse transcribes and organizes your day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Button(
                onClick = onPrimaryAction,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("empty_state_start_cta")
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (hasMicPermission) "Start First Capture" else "Allow Microphone")
            }
        }
    }
}
