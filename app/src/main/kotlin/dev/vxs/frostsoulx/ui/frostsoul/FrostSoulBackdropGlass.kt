package dev.vxs.frostsoulx.ui.frostsoul

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView
import kotlin.math.roundToInt

/**
 * Real Android backdrop glass for Compose surfaces.
 *
 * BlurView samples the Android view hierarchy behind this component. Its children remain sharp,
 * while the sampled content is blurred dynamically. The Compose layers above it add only subtle
 * tint, refraction highlight, border, and deterministic fine grain.
 */
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

    Box(
        modifier = modifier.clip(shape),
    ) {
        AndroidView(
            factory = { context ->
                BlurView(context).apply {
                    // BlurView 3.x samples a sibling/ancestor BlurTarget. The target is kept
                    // separate from this surface so the glass never captures itself.
                    setupWith(BlurTarget(context))
                        .setBlurRadius(safeBlur)
                        .setOverlayColor(AndroidColor.TRANSPARENT)
                }
            },
            update = { blurView ->
                blurView.setBlurRadius(safeBlur)
            },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val noiseCount = (24f + safeGrain * 120f).roundToInt()
                    val noiseAlpha = (0.012f + safeGrain * 0.05f).coerceIn(0.012f, 0.062f)
                    val tintWash = Brush.linearGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.055f),
                            Color.White.copy(alpha = 0.022f),
                            tint.copy(alpha = 0.035f),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    val refractionHighlight = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.035f),
                            Color.Transparent,
                        ),
                        startX = size.width * 0.18f,
                        endX = size.width * 0.82f,
                    )
                    val upperHighlight = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.13f),
                            Color.White.copy(alpha = 0.025f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height * 0.34f,
                    )
                    onDrawWithContent {
                        drawRect(brush = tintWash, blendMode = BlendMode.SrcOver)
                        drawRect(brush = refractionHighlight, blendMode = BlendMode.Screen)
                        drawRect(brush = upperHighlight, blendMode = BlendMode.Screen)
                        repeat(noiseCount) { index ->
                            val x = ((index * 83 + 17) % 101) / 100f * size.width
                            val y = ((index * 47 + 29) % 97) / 96f * size.height
                            drawCircle(
                                color = Color.White.copy(
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

fun Modifier.frostSoulBackdropBorder(shape: Shape): Modifier =
    border(1.dp, Color.White.copy(alpha = 0.16f), shape)
