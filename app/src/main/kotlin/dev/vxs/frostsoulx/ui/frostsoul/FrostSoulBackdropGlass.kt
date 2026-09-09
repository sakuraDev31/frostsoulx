package dev.vxs.frostsoulx.ui.frostsoul

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.rememberGraphicsLayer
import kotlin.math.roundToInt

/** The most recent content-only layer recorded by FrostSoulBackdropRecorder. */
val LocalFrostSoulBackdropLayer = staticCompositionLocalOf<GraphicsLayer?> { null }
val LocalFrostSoulBackdropOrigin = staticCompositionLocalOf<MutableState<IntOffset>?> { null }

/**
 * Records only the composable content passed to [content]. The recorded layer is later sampled
 * by FrostSoulBackdropSurface siblings, so the glass never captures itself or another overlay.
 */
@Composable
fun FrostSoulBackdropRecorder(
    layer: GraphicsLayer,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val originState = LocalFrostSoulBackdropOrigin.current
    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                originState?.value = IntOffset(position.x.roundToInt(), position.y.roundToInt())
            }
            .drawWithCache {
            onDrawWithContent {
                layer.record { drawContent() }
                drawContent()
            }
        },
        content = content,
    )
}

@Composable
fun FrostSoulBackdropProvider(
    content: @Composable () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val origin = remember { mutableStateOf(IntOffset.Zero) }
    CompositionLocalProvider(
        LocalFrostSoulBackdropLayer provides layer,
        LocalFrostSoulBackdropOrigin provides origin,
        content = content,
    )
}

/**
 * Content-aware glass surface. On Android 12+ the captured pixels are blurred by RenderEffect;
 * older devices receive the same transparent material treatment without pretending that a blur
 * exists. The surface remains mostly transparent and never paints an opaque black rectangle.
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
    val layer = LocalFrostSoulBackdropLayer.current
    val originInRoot = LocalFrostSoulBackdropOrigin.current?.value ?: IntOffset.Zero
    var positionInRoot by remember { mutableStateOf(IntOffset.Zero) }
    val safeGrain = grain.coerceIn(0f, 1f)
    val safeBlur = blurRadius.coerceIn(0f, 64f)
    val blurEffect: RenderEffect? = rememberNativeBackdropBlur(safeBlur)

    Box(
        modifier = modifier
            .clip(shape)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                positionInRoot = IntOffset(position.x.roundToInt(), position.y.roundToInt())
            },
    ) {
        if (layer != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = blurEffect
                        // A tiny enlargement prevents the blur edge from revealing a hard seam.
                        scaleX = 1.018f
                        scaleY = 1.018f
                    },
            ) {
                clipRect {
                                            translate(
                            -(positionInRoot.x - originInRoot.x).toFloat(),
                            -(positionInRoot.y - originInRoot.y).toFloat(),
                        ) {

                        drawLayer(layer)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val noiseCount = (24f + safeGrain * 120f).roundToInt()
                    val noiseAlpha = (0.012f + safeGrain * 0.05f).coerceIn(0.012f, 0.062f)
                    val wash = Brush.linearGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.055f),
                            Color.White.copy(alpha = 0.022f),
                            tint.copy(alpha = 0.035f),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    val refraction = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.0f),
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
                        drawRect(brush = wash, blendMode = BlendMode.SrcOver)
                        drawRect(brush = refraction, blendMode = BlendMode.Screen)
                        drawRect(brush = upperHighlight, blendMode = BlendMode.Screen)
                        repeat(noiseCount) { index ->
                            val x = ((index * 83 + 17) % 101) / 100f * size.width
                            val y = ((index * 47 + 29) % 97) / 96f * size.height
                            drawCircle(
                                color = Color.White.copy(alpha = if (index % 3 == 0) noiseAlpha else noiseAlpha * 0.42f),
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
private fun rememberNativeBackdropBlur(radius: Float): RenderEffect? {
    return remember(radius) {
        if (radius <= 0.5f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            null
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                val refractionShader =
                    RuntimeShader(
                        """
                        uniform shader content;
                        half4 main(float2 p) {
                            float2 delta = float2(
                                sin(p.y * 0.020) * 0.85,
                                cos(p.x * 0.017) * 0.55
                            );
                            return content.eval(p + delta);
                        }
                        """.trimIndent(),
                    )
                val blur =
                    android.graphics.RenderEffect.createBlurEffect(
                        radius,
                        radius,
                        android.graphics.Shader.TileMode.CLAMP,
                    )
                val refract =
                    android.graphics.RenderEffect.createRuntimeShaderEffect(
                        refractionShader,
                        "content",
                    )
                android.graphics.RenderEffect
                    .createChainEffect(refract, blur)
                    .asComposeRenderEffect()
            }.getOrNull()
        } else {
            BlurEffect(
                radiusX = radius,
                radiusY = radius,
                edgeTreatment = TileMode.Clamp,
            ).takeIf(RenderEffect::isSupported)
        }
    }
}

fun Modifier.frostSoulBackdropBorder(shape: Shape): Modifier =
    border(1.dp, Color.White.copy(alpha = 0.16f), shape)

fun Modifier.frostSoulBackdropFallback(
    shape: Shape,
    tint: Color,
): Modifier =
    background(tint.copy(alpha = 0.075f), shape).frostSoulBackdropBorder(shape)
