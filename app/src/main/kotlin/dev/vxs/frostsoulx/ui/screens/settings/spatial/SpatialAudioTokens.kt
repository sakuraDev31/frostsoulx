package dev.vxs.frostsoulx.ui.screens.settings.spatial

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import dev.vxs.frostsoulx.ui.player.frostsoul.rememberFrostSoulPalette
import kotlinx.coroutines.launch

/**
 * Design Tokens for Spatial Audio Effects Library.
 * Strict crispness rules: 4dp grid, 1dp/1.5dp strokes, 12/20/28/pill radii, semantic M3 colors.
 */
object SpatialAudioTokens {
    // ── Spacing Tokens (4dp grid) ──
    val GridUnit: Dp = 4.dp
    val ScreenPadding: Dp = 20.dp
    val CardPadding: Dp = 16.dp
    val RowGap: Dp = 12.dp
    val SectionGap: Dp = 24.dp
    val ChipGap: Dp = 8.dp

    // ── Shapes Scale: 12 / 20 / 28 / pill ──
    val InputRadius: Dp = 12.dp
    val CardRadius: Dp = 20.dp
    val BottomSheetRadius: Dp = 28.dp

    val InputShape: Shape = RoundedCornerShape(InputRadius)
    val CardShape: Shape = RoundedCornerShape(CardRadius)
    val BottomSheetShape: Shape = RoundedCornerShape(topStart = BottomSheetRadius, topEnd = BottomSheetRadius)
    val PillShape: Shape = CircleShape

    // ── Strokes: 1dp or 1.5dp only ──
    val StrokeHairline: Dp = 1.dp
    val StrokeOutline: Dp = 1.5.dp

    // ── Semantic Base Fallback Colors ──
    val BaseNearBlackSurface = Color(0xFF0E0E10) // Tone ~6 near-black
    val BaseSurfaceContainer = Color(0xFF161619)
    val BaseSurfaceContainerHigh = Color(0xFF202024)
    val BaseSurfaceContainerHighest = Color(0xFF2B2B30)

    // ── Dynamic Semantic Colors (Material 3 & Artwork Derived) ──
    val NearBlackSurface: Color
        @Composable get() = MaterialTheme.colorScheme.surface
    val SurfaceContainer: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainer
    val SurfaceContainerHigh: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val SurfaceContainerHighest: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest

    val UnselectedTrackAlpha = 0.24f
    val ConcentricRingAlpha = 0.24f
    val MasterOffAlpha = 0.38f
    val DisabledAlpha = 0.38f

    // ── Motion Specs (Springs Only) ──
    val BouncySpringSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    val SlideSpringSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    val ColorCrossfadeSpec = tween<Color>(durationMillis = 500)
}

/**
 * WCAG contrast calculator and guard ensuring text >= 4.5:1 and graphics >= 3.0:1.
 */
object ContrastGuard {
    fun calculateContrast(foreground: Color, background: Color): Float {
        return ColorUtils.calculateContrast(foreground.toArgb(), background.toArgb()).toFloat()
    }

    fun ensureContrast(
        foreground: Color,
        background: Color,
        minContrastRatio: Float,
    ): Color {
        val currentContrast = calculateContrast(foreground, background)
        if (currentContrast >= minContrastRatio) return foreground

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(foreground.toArgb(), hsl)
        val bgLuminance = ColorUtils.calculateLuminance(background.toArgb())

        // If background is dark (standard for near-black surface), step lightness up
        val stepUp = bgLuminance < 0.5
        var iterations = 0
        while (iterations < 20) {
            if (stepUp) {
                hsl[2] = (hsl[2] + 0.05f).coerceAtMost(0.98f)
            } else {
                hsl[2] = (hsl[2] - 0.05f).coerceAtLeast(0.02f)
            }
            val candidate = Color(ColorUtils.HSLToColor(hsl))
            if (calculateContrast(candidate, background) >= minContrastRatio) {
                return candidate
            }
            iterations++
        }
        return if (stepUp) Color.White else Color.Black
    }

    fun ensureTextContrast(foreground: Color, background: Color): Color =
        ensureContrast(foreground, background, 4.5f)

    fun ensureGraphicContrast(foreground: Color, background: Color): Color =
        ensureContrast(foreground, background, 3.0f)
}

/**
 * Shared bouncyClick modifier: scale 0.96 on press, medium bouncy spring, light haptic feedback.
 */
fun Modifier.bouncyClick(
    enabled: Boolean = true,
    hapticFeedbackType: HapticFeedbackType? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val reduceMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1.0f,
            ) == 0f ||
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1.0f,
                ) == 0f
        }.getOrDefault(false)
    }

    if (!enabled) return@composed this

    val scaleAnim = remember { Animatable(1.0f) }

    val resolvedHaptic = hapticFeedbackType ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        HapticFeedbackType.ContextClick
    } else {
        HapticFeedbackType.TextHandleMove
    }

    this
        .scale(scaleAnim.value)
        .pointerInput(enabled, reduceMotion) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    if (!reduceMotion) {
                        coroutineScope.launch {
                            scaleAnim.animateTo(
                                targetValue = 0.96f,
                                animationSpec = SpatialAudioTokens.BouncySpringSpec,
                            )
                        }
                    }
                    val upOrCancel = waitForUpOrCancellation()
                    if (upOrCancel != null) {
                        haptic.performHapticFeedback(resolvedHaptic)
                        onClick()
                    }
                    if (!reduceMotion) {
                        coroutineScope.launch {
                            scaleAnim.animateTo(
                                targetValue = 1.0f,
                                animationSpec = SpatialAudioTokens.BouncySpringSpec,
                            )
                        }
                    }
                }
            }
        }
}

/**
 * Dynamic Material 3 dark color scheme built from album artwork with contrast guard
 * and 500ms smooth crossfade.
 */
@Composable
fun rememberSpatialColorScheme(artworkUrl: String?): ColorScheme {
    val context = LocalContext.current
    val palette = rememberFrostSoulPalette(artworkUrl)

    val rawPrimary = palette.artworkPrimary.takeIf { it != Color.White && it != Color.Transparent }
        ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context).primary
        } else {
            MaterialTheme.colorScheme.primary
        }

    val rawSecondary = palette.artworkSecondary.takeIf { it != Color.White && it != Color.Transparent }
        ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context).secondary
        } else {
            MaterialTheme.colorScheme.secondary
        }

    val guardedPrimary = remember(rawPrimary) {
        ContrastGuard.ensureGraphicContrast(rawPrimary, SpatialAudioTokens.BaseNearBlackSurface)
    }

    val guardedSecondary = remember(rawSecondary) {
        ContrastGuard.ensureGraphicContrast(rawSecondary, SpatialAudioTokens.BaseNearBlackSurface)
    }

    val animatedPrimary by animateColorAsState(
        targetValue = guardedPrimary,
        animationSpec = SpatialAudioTokens.ColorCrossfadeSpec,
        label = "SpatialPrimary",
    )

    val animatedSecondary by animateColorAsState(
        targetValue = guardedSecondary,
        animationSpec = SpatialAudioTokens.ColorCrossfadeSpec,
        label = "SpatialSecondary",
    )

    val animatedPrimaryContainer by animateColorAsState(
        targetValue = animatedPrimary.copy(alpha = 0.22f),
        animationSpec = SpatialAudioTokens.ColorCrossfadeSpec,
        label = "SpatialPrimaryContainer",
    )

    val animatedSecondaryContainer by animateColorAsState(
        targetValue = animatedSecondary.copy(alpha = 0.20f),
        animationSpec = SpatialAudioTokens.ColorCrossfadeSpec,
        label = "SpatialSecondaryContainer",
    )

    // Dynamically derive tonal dark surfaces and outlines from artwork primary
    val surfaceArgb = remember(guardedPrimary) {
        ColorUtils.blendARGB(SpatialAudioTokens.BaseNearBlackSurface.toArgb(), guardedPrimary.toArgb(), 0.05f)
    }
    val surfaceContainerArgb = remember(guardedPrimary) {
        ColorUtils.blendARGB(SpatialAudioTokens.BaseSurfaceContainer.toArgb(), guardedPrimary.toArgb(), 0.10f)
    }
    val surfaceContainerHighArgb = remember(guardedPrimary) {
        ColorUtils.blendARGB(SpatialAudioTokens.BaseSurfaceContainerHigh.toArgb(), guardedPrimary.toArgb(), 0.15f)
    }
    val surfaceContainerHighestArgb = remember(guardedPrimary) {
        ColorUtils.blendARGB(SpatialAudioTokens.BaseSurfaceContainerHighest.toArgb(), guardedPrimary.toArgb(), 0.20f)
    }
    val surfaceContainerLowArgb = remember(guardedPrimary) {
        ColorUtils.blendARGB(0xFF101115.toInt(), guardedPrimary.toArgb(), 0.07f)
    }
    val outlineArgb = remember(guardedPrimary) {
        ColorUtils.blendARGB(0xFF383840.toInt(), guardedPrimary.toArgb(), 0.22f)
    }
    val outlineVariantArgb = remember(guardedPrimary) {
        ColorUtils.blendARGB(0xFF26262C.toInt(), guardedPrimary.toArgb(), 0.16f)
    }

    val animatedSurface by animateColorAsState(Color(surfaceArgb), SpatialAudioTokens.ColorCrossfadeSpec, label = "SpatialSurface")
    val animatedSurfaceContainer by animateColorAsState(Color(surfaceContainerArgb), SpatialAudioTokens.ColorCrossfadeSpec, label = "SpatialSurfaceContainer")
    val animatedSurfaceContainerHigh by animateColorAsState(Color(surfaceContainerHighArgb), SpatialAudioTokens.ColorCrossfadeSpec, label = "SpatialSurfaceContainerHigh")
    val animatedSurfaceContainerHighest by animateColorAsState(Color(surfaceContainerHighestArgb), SpatialAudioTokens.ColorCrossfadeSpec, label = "SpatialSurfaceContainerHighest")
    val animatedSurfaceContainerLow by animateColorAsState(Color(surfaceContainerLowArgb), SpatialAudioTokens.ColorCrossfadeSpec, label = "SpatialSurfaceContainerLow")
    val animatedOutline by animateColorAsState(Color(outlineArgb), SpatialAudioTokens.ColorCrossfadeSpec, label = "SpatialOutline")
    val animatedOutlineVariant by animateColorAsState(Color(outlineVariantArgb), SpatialAudioTokens.ColorCrossfadeSpec, label = "SpatialOutlineVariant")

    return remember(
        animatedPrimary,
        animatedSecondary,
        animatedPrimaryContainer,
        animatedSecondaryContainer,
        animatedSurface,
        animatedSurfaceContainer,
        animatedSurfaceContainerHigh,
        animatedSurfaceContainerHighest,
        animatedSurfaceContainerLow,
        animatedOutline,
        animatedOutlineVariant,
    ) {
        darkColorScheme(
            primary = animatedPrimary,
            onPrimary = Color.Black,
            primaryContainer = animatedPrimaryContainer,
            onPrimaryContainer = Color.White,
            secondary = animatedSecondary,
            onSecondary = Color.Black,
            secondaryContainer = animatedSecondaryContainer,
            onSecondaryContainer = Color.White,
            surface = animatedSurface,
            onSurface = Color(0xFFF1F1F4),
            surfaceVariant = animatedSurfaceContainer,
            onSurfaceVariant = Color(0xFF9E9EA8),
            surfaceContainer = animatedSurfaceContainer,
            surfaceContainerHigh = animatedSurfaceContainerHigh,
            surfaceContainerHighest = animatedSurfaceContainerHighest,
            surfaceContainerLow = animatedSurfaceContainerLow,
            outline = animatedOutline,
            outlineVariant = animatedOutlineVariant,
            background = animatedSurface,
            onBackground = Color(0xFFF1F1F4),
        )
    }
}
