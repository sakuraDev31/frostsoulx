/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.frostsoul

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class FrostSoulColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceGlass: Color,
    val surfaceGlassStrong: Color,
    val accent: Color,
    val accentBright: Color,
    val accentMuted: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val outline: Color,
    val error: Color,
    val scrim: Color,
)

@Immutable
data class FrostSoulTypography(
    val display: TextStyle,
    val title: TextStyle,
    val sectionTitle: TextStyle,
    val body: TextStyle,
    val bodyMuted: TextStyle,
    val label: TextStyle,
    val overline: TextStyle,
)

@Immutable
data class FrostSoulShapes(
    val tiny: Shape,
    val small: Shape,
    val medium: Shape,
    val large: Shape,
    val extraLarge: Shape,
    val pill: Shape,
)

@Immutable
data class FrostSoulElevation(
    val none: Dp = 0.dp,
    val low: Dp = 2.dp,
    val medium: Dp = 8.dp,
    val high: Dp = 18.dp,
)

@Immutable
data class FrostSoulEffects(
    val glassBlurRadius: Dp = 24.dp,
    val backdropBlurRadius: Dp = 48.dp,
    val activeGlowAlpha: Float = 0.24f,
    val ambientGlowAlpha: Float = 0.10f,
)

@Immutable
data class FrostSoulSpacing(
    val micro: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val section: Dp = 20.dp,
    val page: Dp = 16.dp,
    val hero: Dp = 24.dp,
)

@Immutable
data class FrostSoulMotion(
    val quick: Int = 120,
    val standard: Int = 220,
    val expressive: Int = 420,
    val slow: Int = 650,
    val contentSpring: AnimationSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMediumLow,
    ),
    val controlSpring: AnimationSpec<Float> = spring(
        dampingRatio = 0.74f,
        stiffness = Spring.StiffnessMedium,
    ),
) {
    fun <T> quickTween(): AnimationSpec<T> = tween(durationMillis = quick)

    fun <T> standardTween(): AnimationSpec<T> = tween(durationMillis = standard)

    fun <T> expressiveTween(): AnimationSpec<T> = tween(durationMillis = expressive)
}

@Immutable
data class FrostSoulDesignTokens(
    val colors: FrostSoulColors,
    val typography: FrostSoulTypography,
    val shapes: FrostSoulShapes,
    val elevation: FrostSoulElevation,
    val effects: FrostSoulEffects,
    val spacing: FrostSoulSpacing,
    val motion: FrostSoulMotion,
)

private val DefaultFrostSoulTokens = FrostSoulDesignTokens(
    colors = FrostSoulColors(
        background = Color(0xFF070B10),
        surface = Color(0xFF0D131B),
        surfaceRaised = Color(0xFF18212A),
        surfaceGlass = Color(0xCC151D27),
        surfaceGlassStrong = Color(0xEB1B2430),
        accent = Color(0xFFEADCC5),
        accentBright = Color(0xFFFFE4AD),
        accentMuted = Color(0xFFBEB5A7),
        onBackground = Color(0xFFF4F2EF),
        onSurface = Color(0xFFF4F2EF),
        onSurfaceMuted = Color(0xFFA7ADB8),
        outline = Color(0xFF3A4552),
        error = Color(0xFFFF6B6B),
        scrim = Color.Black.copy(alpha = 0.72f),
    ),
    typography = FrostSoulTypography(
        display = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal),
        title = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal),
        sectionTitle = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal),
        body = TextStyle(fontWeight = FontWeight.Normal),
        bodyMuted = TextStyle(fontWeight = FontWeight.Normal),
        label = TextStyle(fontWeight = FontWeight.Medium),
        overline = TextStyle(fontWeight = FontWeight.Medium, letterSpacing = 1.4.sp),
    ),
    shapes = FrostSoulShapes(
        tiny = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(24.dp),
        pill = RoundedCornerShape(50),
    ),
    elevation = FrostSoulElevation(),
    effects = FrostSoulEffects(),
    spacing = FrostSoulSpacing(),
    motion = FrostSoulMotion(),
)

val LocalFrostSoulTokens: ProvidableCompositionLocal<FrostSoulDesignTokens> =
    compositionLocalOf { DefaultFrostSoulTokens }

object FrostSoulTheme {
    val colors: FrostSoulColors
        @Composable get() = LocalFrostSoulTokens.current.colors

    val typography: FrostSoulTypography
        @Composable get() = LocalFrostSoulTokens.current.typography

    val shapes: FrostSoulShapes
        @Composable get() = LocalFrostSoulTokens.current.shapes

    val elevation: FrostSoulElevation
        @Composable get() = LocalFrostSoulTokens.current.elevation

    val effects: FrostSoulEffects
        @Composable get() = LocalFrostSoulTokens.current.effects

    val spacing: FrostSoulSpacing
        @Composable get() = LocalFrostSoulTokens.current.spacing

    val motion: FrostSoulMotion
        @Composable get() = LocalFrostSoulTokens.current.motion
}

@Composable
fun FrostSoulDesignSystem(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val tokens = remember(darkTheme) {
        if (darkTheme) {
            DefaultFrostSoulTokens
        } else {
            DefaultFrostSoulTokens.copy(
                colors = DefaultFrostSoulTokens.colors.copy(
                    background = Color.White,
                    surface = Color.White,
                    surfaceRaised = Color(0xFFF5F5F5),
                    surfaceGlass = Color.White.copy(alpha = 0.96f),
                    surfaceGlassStrong = Color.White,
                    accent = Color.Black,
                    accentBright = Color.Black,
                    accentMuted = Color(0xFF555555),
                    onBackground = Color.Black,
                    onSurface = Color.Black,
                    onSurfaceMuted = Color(0xFF666666),
                    outline = Color(0x1A000000),
                    scrim = Color.Black.copy(alpha = 0.48f),
                ),
            )
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalFrostSoulTokens provides tokens, content = content)
}

@Composable
fun Modifier.frostSoulGlass(shape: Shape = FrostSoulTheme.shapes.large): Modifier {
    val colors = FrostSoulTheme.colors
    return this
        .background(
            Brush.verticalGradient(listOf(colors.surfaceGlassStrong, colors.surfaceGlass)),
            shape,
        )
        .border(0.5.dp, colors.onSurface.copy(alpha = 0.12f), shape)
}

@Composable
fun Modifier.frostSoulGlow(
    color: Color = FrostSoulTheme.colors.accent,
    alpha: Float = FrostSoulTheme.effects.activeGlowAlpha,
): Modifier =
    drawBehind {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                center = center,
                radius = size.maxDimension * 0.72f,
            ),
            radius = size.maxDimension * 0.72f,
        )
    }

@Composable
fun Modifier.frostSoulScreenBackground(ambient: Color = Color(0xFF334760)): Modifier {
    val base = FrostSoulTheme.colors.background
    // Static tonal atmosphere; no full-screen blur texture or animation loop.
    return background(
        Brush.verticalGradient(
            0f to lerp(base, ambient, 0.20f),
            0.48f to lerp(base, ambient, 0.06f),
            1f to base,
        ),
    )
}
