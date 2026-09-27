package dev.vxs.frostsoulx.ui.frostsoul

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import kotlin.math.roundToInt

/** Shared source state. The source is applied to the background NavHost, while glass surfaces
 * are later siblings in the Scaffold and therefore never sample themselves. */
val LocalFrostSoulHazeState = staticCompositionLocalOf<HazeState> { HazeState() }

@Composable
fun FrostSoulBackdropSurface(
    modifier: Modifier = Modifier,
    shape: Shape = FrostSoulTheme.shapes.large,
    grain: Float = 0.35f,
    blurRadius: Float = 30f,
    tint: Color = Color.White,
    content: @Composable BoxScope.() -> Unit,
) {
    val safeGrain = grain.coerceIn(0f, 1f)
    val safeBlur = blurRadius.coerceIn(0f, 64f)
    val hazeState = LocalFrostSoulHazeState.current
    val isLight = FrostSoulTheme.colors.background.luminance() > 0.5f
    val blurStyle = HazeBlurStyle {
        blurRadius(safeBlur.dp)
        noiseFactor((safeGrain * 0.16f).coerceIn(0f, 0.16f))
        backgroundColor(Color.Transparent)
        fallbackColorEffect(
            HazeColorEffect.tint(
                if (isLight) tint.copy(alpha = 0.50f) else tint.copy(alpha = 0.065f),
            ),
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = blurStyle,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val noiseCount = (24f + safeGrain * 120f).roundToInt()
                    val noiseAlpha = (0.012f + safeGrain * 0.05f).coerceIn(0.012f, 0.062f)
                    val tintWash = if (isLight) {
                        Brush.linearGradient(
                            colors = listOf(
                                tint.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.50f),
                                tint.copy(alpha = 0.38f),
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                tint.copy(alpha = 0.055f),
                                Color.White.copy(alpha = 0.022f),
                                tint.copy(alpha = 0.035f),
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        )
                    }
                    val refractionHighlight = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = if (isLight) 0.18f else 0.035f),
                            Color.Transparent,
                        ),
                        startX = size.width * 0.18f,
                        endX = size.width * 0.82f,
                    )
                    val upperHighlight = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isLight) 0.45f else 0.13f),
                            Color.White.copy(alpha = if (isLight) 0.10f else 0.025f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height * 0.34f,
                    )
                    onDrawWithContent {
                        drawRect(brush = tintWash, blendMode = BlendMode.SrcOver)
                        drawRect(brush = refractionHighlight, blendMode = if (isLight) BlendMode.SrcOver else BlendMode.Screen)
                        drawRect(brush = upperHighlight, blendMode = if (isLight) BlendMode.SrcOver else BlendMode.Screen)
                        repeat(noiseCount) { index ->
                            val x = ((index * 83 + 17) % 101) / 100f * size.width
                            val y = ((index * 47 + 29) % 97) / 96f * size.height
                            drawCircle(
                                color = (if (isLight) Color.Black else Color.White).copy(
                                    alpha = if (index % 3 == 0) noiseAlpha else noiseAlpha * 0.42f,
                                ),
                                radius = 0.35f + ((index % 3) * 0.24f),
                                center = Offset(x, y),
                            )
                        }
                        drawContent()
                    }
                },
        ) {
            content()
        }
    }
}

@Composable
fun Modifier.frostSoulBackdropBorder(shape: Shape): Modifier {
    val isLight = FrostSoulTheme.colors.background.luminance() > 0.5f
    val borderBrush = if (isLight) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.80f),
                Color.Black.copy(alpha = 0.08f),
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.16f),
                Color.White.copy(alpha = 0.10f),
            ),
        )
    }
    return border(1.dp, borderBrush, shape)
}
