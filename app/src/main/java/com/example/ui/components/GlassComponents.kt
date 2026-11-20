package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.local.ActionPriority
import com.example.ui.theme.LocalDayPulseHaze
import com.example.ui.theme.LocalMidnightGlass
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class GlassTier {
    LEVEL_1,
    LEVEL_2,
    LEVEL_3
}

@Composable
fun AnimatedMeshBackdrop(
    isRecording: Boolean,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val glass = LocalMidnightGlass.current
    val bgColor = MaterialTheme.colorScheme.background

    if (!glass.glassEnabled) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(bgColor)
        )
        return
    }

    val infinite = rememberInfiniteTransition(label = "mesh_backdrop")
    val phaseA by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blob_a"
    )
    val phaseB by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blob_b"
    )
    val phaseC by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blob_c"
    )

    val recordingWeight by animateFloatAsState(
        targetValue = if (isRecording) 1f else 0f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "recording_warmth"
    )
    val smoothAmplitude by animateFloatAsState(
        targetValue = if (isRecording) amplitude.coerceIn(0.05f, 1f) else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "backdrop_amp"
    )

    val violetColor = glass.violetAccent
    val coralColor = glass.coralAccent
    val mintColor = glass.mintAccent
    val isDark = glass.isDark

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        val w = size.width
        val h = size.height
        val minDim = size.minDimension

        // Primary Electric Violet blob (top-left to upper-mid drift)
        val violetCenter = Offset(
            x = w * (0.28f + 0.14f * cos(phaseA)),
            y = h * (0.20f + 0.10f * sin(phaseA))
        )
        val violetRadius = minDim * 0.85f
        val violetAlpha = if (isDark) 0.34f - (0.08f * recordingWeight) else 0.24f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    violetColor.copy(alpha = violetAlpha),
                    violetColor.copy(alpha = violetAlpha * 0.38f),
                    Color.Transparent
                ),
                center = violetCenter,
                radius = violetRadius
            ),
            radius = violetRadius,
            center = violetCenter
        )

        // Coral blob (warms up and pulses with microphone amplitude while recording)
        val coralCenter = Offset(
            x = w * (0.76f - 0.16f * sin(phaseB) - 0.18f * recordingWeight),
            y = h * (0.42f + 0.12f * cos(phaseB) - 0.14f * recordingWeight)
        )
        val coralRadius = minDim * (0.72f + 0.16f * recordingWeight + 0.12f * smoothAmplitude)
        val baseCoralAlpha = if (isDark) 0.25f else 0.18f
        val activeCoralAlpha = (baseCoralAlpha + 0.15f * recordingWeight + 0.06f * smoothAmplitude)
            .coerceIn(0.15f, 0.42f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    coralColor.copy(alpha = activeCoralAlpha),
                    coralColor.copy(alpha = activeCoralAlpha * 0.35f),
                    Color.Transparent
                ),
                center = coralCenter,
                radius = coralRadius
            ),
            radius = coralRadius,
            center = coralCenter
        )

        // Mint blob (lower-left / lower-right serene counter-balance)
        val mintCenter = Offset(
            x = w * (0.35f + 0.18f * sin(phaseC)),
            y = h * (0.78f + 0.09f * cos(phaseC))
        )
        val mintRadius = minDim * 0.78f
        val mintAlpha = if (isDark) 0.26f - (0.06f * recordingWeight) else 0.20f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    mintColor.copy(alpha = mintAlpha),
                    mintColor.copy(alpha = mintAlpha * 0.35f),
                    Color.Transparent
                ),
                center = mintCenter,
                radius = mintRadius
            ),
            radius = mintRadius,
            center = mintCenter
        )
    }
}

@Composable
fun GlassContainer(
    modifier: Modifier = Modifier,
    tier: GlassTier = GlassTier.LEVEL_1,
    shape: Shape = RoundedCornerShape(24.dp),
    accentGlow: Color? = null,
    useBackdropBlur: Boolean = tier != GlassTier.LEVEL_1,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalMidnightGlass.current
    val hazeState = LocalDayPulseHaze.current
    val colorScheme = MaterialTheme.colorScheme

    val fillColor = when {
        !glass.glassEnabled -> when (tier) {
            GlassTier.LEVEL_1 -> glass.fallbackFill1
            GlassTier.LEVEL_2, GlassTier.LEVEL_3 -> glass.fallbackFill2
        }
        tier == GlassTier.LEVEL_1 -> glass.level1Fill
        tier == GlassTier.LEVEL_2 -> glass.level2Fill
        else -> glass.level3Fill
    }

    val blurDp: Dp = when (tier) {
        GlassTier.LEVEL_1 -> 24.dp
        GlassTier.LEVEL_2 -> 28.dp
        GlassTier.LEVEL_3 -> 32.dp
    }

    val shadowElevation: Dp = when {
        accentGlow != null && glass.glassEnabled -> 14.dp
        tier == GlassTier.LEVEL_3 -> 16.dp
        tier == GlassTier.LEVEL_2 -> 10.dp
        else -> 0.dp
    }
    val glowTint = (accentGlow ?: glass.violetAccent).copy(
        alpha = if (glass.isDark) 0.34f else 0.18f
    )

    val borderBrush = Brush.linearGradient(
        colors = listOf(
            glass.borderTopLeft,
            glass.borderBottomRight
        )
    )

    val blurActive = useBackdropBlur && glass.supportsRealBlur && hazeState != null
    val baseModifier = modifier
        .then(
            if (shadowElevation > 0.dp) {
                Modifier.shadow(
                    elevation = shadowElevation,
                    shape = shape,
                    ambientColor = glowTint,
                    spotColor = glowTint
                )
            } else {
                Modifier
            }
        )
        .clip(shape)
        .then(
            if (blurActive) {
                Modifier.hazeChild(
                    state = hazeState!!,
                    style = HazeStyle(
                        backgroundColor = if (glass.isDark) colorScheme.background else colorScheme.surface,
                        tint = HazeTint(fillColor),
                        blurRadius = blurDp,
                        noiseFactor = 0.04f
                    )
                )
            } else {
                val nonBlurFill = if (glass.glassEnabled) {
                    // Without live RenderEffect blur, lift opacity slightly so text keeps >= 4.5:1 legibility
                    if (glass.isDark) {
                        colorScheme.surface.copy(alpha = if (tier == GlassTier.LEVEL_1) 0.80f else 0.88f)
                    } else {
                        colorScheme.surface.copy(alpha = if (tier == GlassTier.LEVEL_1) 0.84f else 0.92f)
                    }
                } else {
                    fillColor
                }
                Modifier.background(nonBlurFill)
            }
        )
        .border(
            border = if (glass.glassEnabled) {
                BorderStroke(1.dp, borderBrush)
            } else {
                BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.65f))
            },
            shape = shape
        )
        .drawBehind {
            if (glass.glassEnabled) {
                // Subtle 1dp top inner specular highlight
                drawLine(
                    color = glass.innerTopHighlight,
                    start = Offset(24.dp.toPx(), 1.dp.toPx()),
                    end = Offset((size.width - 24.dp.toPx()).coerceAtLeast(24.dp.toPx()), 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

    Box(modifier = baseModifier, content = content)
}

@Composable
fun PriorityGlassPill(
    priority: String,
    modifier: Modifier = Modifier
) {
    val glass = LocalMidnightGlass.current
    val (hue, label) = when (priority) {
        ActionPriority.HIGH.name -> glass.coralAccent to "HIGH"
        ActionPriority.LOW.name -> glass.mintAccent to "LOW"
        else -> glass.amberAccent to "MED"
    }

    val fill = if (glass.glassEnabled) {
        hue.copy(alpha = if (glass.isDark) 0.20f else 0.16f)
    } else {
        hue.copy(alpha = 0.18f)
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = fill,
        border = BorderStroke(1.dp, hue.copy(alpha = 0.72f)),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = hue,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun TintedGlassChip(
    text: String,
    hue: Color,
    modifier: Modifier = Modifier
) {
    val glass = LocalMidnightGlass.current
    Surface(
        shape = RoundedCornerShape(50),
        color = hue.copy(alpha = if (glass.isDark) 0.18f else 0.14f),
        border = BorderStroke(1.dp, hue.copy(alpha = 0.50f)),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = hue,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SkeletonShimmerBlock(
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "skeleton_shimmer")
    val shimmerX by infinite.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val brush = Brush.horizontalGradient(
        colors = listOf(base, highlight, base),
        startX = shimmerX * 300f,
        endX = (shimmerX + 0.7f) * 300f
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )
    }
}
