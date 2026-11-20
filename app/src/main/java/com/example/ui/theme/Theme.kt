package com.example.ui.theme

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.chrisbanes.haze.HazeState

internal val DarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = ElectricVioletOnPrimary,
    primaryContainer = ElectricVioletContainer,
    onPrimaryContainer = ElectricVioletOnContainer,
    secondary = MidnightMint,
    onSecondary = MidnightMintOn,
    secondaryContainer = MidnightMintContainer,
    onSecondaryContainer = MidnightMintOnContainer,
    tertiary = MidnightCoral,
    onTertiary = MidnightCoralOn,
    tertiaryContainer = MidnightCoralContainer,
    onTertiaryContainer = MidnightCoralOnContainer,
    background = MidnightBackground,
    onBackground = MidnightTextPrimary,
    surface = MidnightSurface,
    onSurface = MidnightTextPrimary,
    surfaceVariant = MidnightSurfaceRaised,
    onSurfaceVariant = MidnightTextSecondary,
    outline = MidnightOutline,
    outlineVariant = MidnightSurfaceRaised,
    error = MidnightCoral,
    onError = MidnightCoralOn
)

internal val LightColorScheme = lightColorScheme(
    primary = LavenderViolet,
    onPrimary = LavenderVioletOnPrimary,
    primaryContainer = LavenderVioletContainer,
    onPrimaryContainer = LavenderVioletOnContainer,
    secondary = LavenderMint,
    onSecondary = LavenderMintOn,
    secondaryContainer = LavenderMintContainer,
    onSecondaryContainer = LavenderMintOnContainer,
    tertiary = LavenderCoral,
    onTertiary = LavenderCoralOn,
    tertiaryContainer = LavenderCoralContainer,
    onTertiaryContainer = LavenderCoralOnContainer,
    background = LavenderBackground,
    onBackground = LavenderTextPrimary,
    surface = LavenderSurface,
    onSurface = LavenderTextPrimary,
    surfaceVariant = LavenderSurfaceRaised,
    onSurfaceVariant = LavenderTextSecondary,
    outline = LavenderOutline,
    outlineVariant = LavenderSurfaceRaised,
    error = LavenderCoral,
    onError = LavenderCoralOn
)

@Immutable
data class MidnightGlassPalette(
    val isDark: Boolean = true,
    val glassEnabled: Boolean = true,
    val supportsRealBlur: Boolean = true,
    val violetAccent: Color = ElectricViolet,
    val coralAccent: Color = MidnightCoral,
    val mintAccent: Color = MidnightMint,
    val amberAccent: Color = MidnightAmber,
    val amberContainer: Color = MidnightAmberContainer,
    val amberOnContainer: Color = MidnightAmberOnContainer,
    val level1Fill: Color = Color.White.copy(alpha = 0.08f),
    val level2Fill: Color = Color.White.copy(alpha = 0.11f),
    val level3Fill: Color = Color.White.copy(alpha = 0.13f),
    val fallbackFill1: Color = MidnightSurface,
    val fallbackFill2: Color = MidnightSurfaceRaised,
    val borderTopLeft: Color = Color.White.copy(alpha = 0.28f),
    val borderBottomRight: Color = Color.White.copy(alpha = 0.04f),
    val innerTopHighlight: Color = Color.White.copy(alpha = 0.10f)
)

val LocalMidnightGlass = staticCompositionLocalOf { MidnightGlassPalette() }
val LocalDayPulseHaze = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun DayPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    glassEffectsEnabled: Boolean = true,
    hazeState: HazeState? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isPowerSave = remember(context) {
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isPowerSaveMode == true
        }.getOrDefault(false)
    }
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val effectiveGlass = glassEffectsEnabled && !isPowerSave

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val glassPalette = remember(darkTheme, effectiveGlass, supportsBlur) {
        if (darkTheme) {
            MidnightGlassPalette(
                isDark = true,
                glassEnabled = effectiveGlass,
                supportsRealBlur = supportsBlur && effectiveGlass,
                violetAccent = ElectricViolet,
                coralAccent = MidnightCoral,
                mintAccent = MidnightMint,
                amberAccent = MidnightAmber,
                amberContainer = MidnightAmberContainer,
                amberOnContainer = MidnightAmberOnContainer,
                level1Fill = Color.White.copy(alpha = 0.08f),
                level2Fill = Color.White.copy(alpha = 0.11f),
                level3Fill = Color.White.copy(alpha = 0.13f),
                fallbackFill1 = MidnightSurface,
                fallbackFill2 = MidnightSurfaceRaised,
                borderTopLeft = Color.White.copy(alpha = 0.28f),
                borderBottomRight = Color.White.copy(alpha = 0.04f),
                innerTopHighlight = Color.White.copy(alpha = 0.10f)
            )
        } else {
            MidnightGlassPalette(
                isDark = false,
                glassEnabled = effectiveGlass,
                supportsRealBlur = supportsBlur && effectiveGlass,
                violetAccent = LavenderViolet,
                coralAccent = LavenderCoral,
                mintAccent = LavenderMint,
                amberAccent = LavenderAmber,
                amberContainer = LavenderAmberContainer,
                amberOnContainer = LavenderAmberOnContainer,
                level1Fill = Color.White.copy(alpha = 0.58f),
                level2Fill = Color.White.copy(alpha = 0.68f),
                level3Fill = Color.White.copy(alpha = 0.74f),
                fallbackFill1 = LavenderSurface,
                fallbackFill2 = LavenderSurfaceRaised,
                borderTopLeft = Color.White.copy(alpha = 0.85f),
                borderBottomRight = LavenderOutline.copy(alpha = 0.45f),
                innerTopHighlight = Color.White.copy(alpha = 0.65f)
            )
        }
    }

    CompositionLocalProvider(
        LocalMidnightGlass provides glassPalette,
        LocalDayPulseHaze provides hazeState
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = DayPulseShapes,
            content = content
        )
    }
}
