/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.component

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

val isGlassBackgroundSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun FloatingBarGlassBackground(
    hazeState: HazeState,
    shape: RoundedCornerShape,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    tintColor: Color? = null,
) {
    if (!isGlassBackgroundSupported) {
        FloatingBarBlurBackground(
            hazeState = hazeState,
            isAcrylic = false,
            pureBlack = pureBlack,
            modifier = modifier,
            tintColor = tintColor,
        )
        return
    }
    val colorScheme = MaterialTheme.colorScheme
    val baseSurfaceColor = if (pureBlack) Color.Black else colorScheme.surfaceContainerHigh
    val surfaceColor = tintColor ?: baseSurfaceColor
    val isDarkSurface = pureBlack || colorScheme.surface.luminance() < colorScheme.onSurface.luminance()

    FrostedGlassBackground(
        hazeState = hazeState,
        shape = shape,
        surfaceColor = surfaceColor,
        isDarkSurface = isDarkSurface,
        modifier = modifier,
    )
}

@Composable
fun FloatingBarBlurBackground(
    hazeState: HazeState,
    isAcrylic: Boolean,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    tintColor: Color? = null,
) {
    val surfaceColor = tintColor ?: if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
    val hazeInput = remember(hazeState) { HazeInput.Sources(hazeState) }
    val blurStyle = remember(isAcrylic, surfaceColor) {
        HazeBlurStyle {
            backgroundColor(surfaceColor)
            blurRadius(if (isAcrylic) 32.dp else 8.dp)
            noiseFactor(if (isAcrylic) 0.12f else 0f)
            colorEffects(
                listOf(
                    HazeColorEffect.tint(surfaceColor.copy(alpha = if (isAcrylic) 0.72f else 0.62f)),
                ),
            )
            fallbackColorEffect(HazeColorEffect.tint(surfaceColor))
        }
    }
    Box(
        modifier = modifier.hazeBlur(
            input = hazeInput,
            style = blurStyle,
            expandLayerBounds = true,
        ),
    )
}

@Composable
private fun FrostedGlassBackground(
    hazeState: HazeState,
    shape: RoundedCornerShape,
    surfaceColor: Color,
    isDarkSurface: Boolean,
    modifier: Modifier,
) {
    val hazeInput = remember(hazeState) { HazeInput.Sources(hazeState) }
    val blurStyle = remember(surfaceColor, isDarkSurface) {
        HazeBlurStyle {
            backgroundColor(surfaceColor)
            blurRadius(20.dp)
            noiseFactor(0f)
            colorEffects(
                listOf(
                    HazeColorEffect.tint(surfaceColor.copy(alpha = if (isDarkSurface) 0.42f else 0.36f)),
                ),
            )
            fallbackColorEffect(HazeColorEffect.tint(surfaceColor))
        }
    }
    val rimBrush = remember(isDarkSurface) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDarkSurface) 0.22f else 0.5f),
                Color.White.copy(alpha = 0.04f),
                Color.White.copy(alpha = if (isDarkSurface) 0.1f else 0.24f),
            ),
        )
    }
    Box(
        modifier = modifier
            .hazeBlur(
                input = hazeInput,
                style = blurStyle,
                expandLayerBounds = true,
            )
            .border(width = 1.dp, brush = rimBrush, shape = shape),
    )
}
