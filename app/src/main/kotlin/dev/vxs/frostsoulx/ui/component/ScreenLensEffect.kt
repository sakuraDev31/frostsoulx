/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package dev.vxs.frostsoulx.ui.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.constants.ScreenCurvatureRadiusKey
import dev.vxs.frostsoulx.constants.ScreenCurvatureStyle
import dev.vxs.frostsoulx.utils.rememberPreference
import kotlin.math.roundToInt

private const val SCREEN_LENS_SHADER_SRC = """
    uniform shader composable;
    uniform float2 size;
    uniform float curvature;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / size;
        float2 centered = (uv - 0.5) * 2.0;
        float r2 = dot(centered, centered);

        // Barrel (convex) vs Pincushion (concave) optical distortion
        float distortion = 1.0 + curvature * r2;
        float2 distortedCentered = centered * distortion;
        float2 distortedUv = (distortedCentered * 0.5) + 0.5;

        if (distortedUv.x < 0.0 || distortedUv.x > 1.0 || distortedUv.y < 0.0 || distortedUv.y > 1.0) {
            return half4(0.0, 0.0, 0.0, 1.0);
        }
        return composable.eval(distortedUv * size);
    }
"""

/**
 * Applies a physical screen curvature bend (convex or concave) across the UI
 * along with a corner lens effect (optical vignette and specular glass rim reflection).
 */
fun Modifier.screenLensEffect(
    style: ScreenCurvatureStyle,
    radiusDp: Float,
): Modifier {
    if (style == ScreenCurvatureStyle.OFF) return this

    val safeRadius = radiusDp.coerceIn(0f, 48f)

    return this
        .graphicsLayer {
            clip = safeRadius > 0f
            if (safeRadius > 0f) {
                shape = RoundedCornerShape(safeRadius.dp)
            }
            cameraDistance = 16f * density

            when (style) {
                ScreenCurvatureStyle.CONVEX -> {
                    scaleX = 1.006f
                    scaleY = 1.006f
                }
                ScreenCurvatureStyle.CONCAVE -> {
                    scaleX = 0.994f
                    scaleY = 0.994f
                }
                ScreenCurvatureStyle.OFF -> {}
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && safeRadius > 0f) {
                try {
                    val shader = android.graphics.RuntimeShader(SCREEN_LENS_SHADER_SRC)
                    val curvatureAmount =
                        when (style) {
                            ScreenCurvatureStyle.CONVEX -> (safeRadius / 320f).coerceIn(0.015f, 0.12f)
                            ScreenCurvatureStyle.CONCAVE -> -(safeRadius / 320f).coerceIn(0.015f, 0.12f)
                            ScreenCurvatureStyle.OFF -> 0f
                        }
                    shader.setFloatUniform("size", size.width, size.height)
                    shader.setFloatUniform("curvature", curvatureAmount)
                    renderEffect =
                        android.graphics.RenderEffect
                            .createRuntimeShaderEffect(shader, "composable")
                            .asComposeRenderEffect()
                } catch (_: Throwable) {
                    // Safe fallback if device GPU driver does not support AGSL
                }
            }
        }
        .drawWithContent {
            drawContent()

            if (safeRadius > 0f) {
                val cornerRadiusPx = safeRadius.dp.toPx()
                val strokeWidthPx = 1.5.dp.toPx()

                // Corner optical vignette: darkens the 4 corners simulating thick optical glass
                val cornerVignette =
                    Brush.radialGradient(
                        colorStops =
                            arrayOf(
                                0.60f to Color.Transparent,
                                0.86f to Color(0x24000000),
                                1.00f to Color(0x75000000),
                            ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = (size.width.coerceAtLeast(size.height)) * 0.72f,
                    )
                drawRect(brush = cornerVignette)

                // Corner lens glass rim: specular highlight along curved borders
                val glassRimBrush =
                    Brush.linearGradient(
                        colors =
                            listOf(
                                Color(0x35FFFFFF),
                                Color(0x08FFFFFF),
                                Color(0x15FFFFFF),
                                Color(0x28FFFFFF),
                            ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                    )
                drawRoundRect(
                    brush = glassRimBrush,
                    topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f),
                    size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    style = Stroke(width = strokeWidthPx),
                )
            }
        }
}

@Composable
fun ScreenCurvatureRadiusSelectorButton(
    modifier: Modifier = Modifier,
    onRadiusSelected: (Float) -> Unit = {},
) {
    val (screenCurvatureRadius, onScreenCurvatureRadiusChange) =
        rememberPreference(
            ScreenCurvatureRadiusKey,
            defaultValue = 16f,
        )

    var showDialog by remember { mutableStateOf(false) }

    PreferenceEntry(
        modifier = modifier,
        title = { Text(text = stringResource(id = R.string.screen_curvature_radius)) },
        description = stringResource(id = R.string.screen_curvature_radius_desc, screenCurvatureRadius.roundToInt()),
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.screenshot),
                contentDescription = null,
            )
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        onClick = { showDialog = true },
    )

    if (showDialog) {
        ScreenCurvatureRadiusModal(
            initialRadius = screenCurvatureRadius,
            onDismiss = { showDialog = false },
            onRadiusSelected = { newRadius ->
                onScreenCurvatureRadiusChange(newRadius)
                onRadiusSelected(newRadius)
                showDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenCurvatureRadiusModal(
    initialRadius: Float,
    onDismiss: () -> Unit,
    onRadiusSelected: (Float) -> Unit,
) {
    var radiusValue by remember { mutableFloatStateOf(initialRadius) }
    val presetValues = listOf(0f, 8f, 12f, 16f, 20f, 24f, 28f, 32f, 40f)
    var customText by remember { mutableStateOf("") }
    var isCustomSelected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isCustomSelected = !presetValues.contains(initialRadius)
        if (isCustomSelected) {
            customText = initialRadius.roundToInt().toString()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false,
            ),
    ) {
        BoxWithConstraints {
            val screenWidth = maxWidth
            val dialogWidth =
                when {
                    screenWidth > 840.dp -> 0.5f
                    screenWidth > 600.dp -> 0.7f
                    else -> 0.95f
                }

            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth(dialogWidth)
                        .wrapContentHeight()
                        .clip(RoundedCornerShape(28.dp))
                        .padding(16.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(id = R.string.adjust_screen_curvature_radius),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live preview of curved screen edge and glass lens
                    Box(
                        modifier =
                            Modifier
                                .size(width = 170.dp, height = 100.dp)
                                .clip(RoundedCornerShape(radiusValue.dp))
                                .background(Color(0xFF161616))
                                .border(
                                    width = 1.5.dp,
                                    brush =
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0x55FFFFFF),
                                                Color(0x10FFFFFF),
                                                Color(0x30FFFFFF),
                                            ),
                                        ),
                                    shape = RoundedCornerShape(radiusValue.dp),
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.screenshot),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${radiusValue.roundToInt()} dp",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Preset chips
                    CurvatureChipsGrid(
                        values = presetValues,
                        selectedValue = if (!isCustomSelected) radiusValue else null,
                        onValueSelected = { value ->
                            radiusValue = value
                            isCustomSelected = false
                            customText = ""
                        },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Slider for fine tuning
                    Slider(
                        value = radiusValue,
                        onValueChange = {
                            radiusValue = it
                            isCustomSelected = true
                            customText = it.roundToInt().toString()
                        },
                        valueRange = 0f..48f,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customText,
                        onValueChange = { newText ->
                            customText = newText
                            newText.toFloatOrNull()?.let {
                                if (it in 0f..48f) {
                                    radiusValue = it
                                    isCustomSelected = true
                                }
                            }
                        },
                        label = { Text(stringResource(id = R.string.custom)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        singleLine = true,
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(
                                text = stringResource(id = R.string.cancel_button),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }

                        Button(
                            onClick = { onRadiusSelected(radiusValue) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.check),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(id = R.string.ok_button),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurvatureChipsGrid(
    values: List<Float>,
    selectedValue: Float?,
    onValueSelected: (Float) -> Unit,
) {
    val chunkedValues = values.chunked(3)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chunkedValues.forEach { rowValues ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowValues.forEachIndexed { index, value ->
                    FilterChip(
                        selected = selectedValue == value,
                        onClick = { onValueSelected(value) },
                        label = {
                            Text(
                                text = "${value.roundToInt()} dp",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    )
                    if (index < rowValues.size - 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
        }
    }
}
