/*
 * FrostSoulX
 * Artwork-derived accent colours for dark player surfaces.
 */

package dev.vxs.frostsoulx.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.graphics.Color as AndroidColor

private const val NeutralSaturation = 0.12f
private const val MinSaturation = 0.35f
private const val MaxSaturation = 0.85f
private const val MinBrightness = 0.78f

/**
 * Adapts an artwork swatch into an accent that stays readable on the dark queue and menu surfaces:
 * brightness is lifted, coloured artwork keeps enough saturation to still read as its colour, and
 * near-grey artwork stays neutral instead of being forced into a random hue.
 */
internal fun legibleArtworkAccent(
    artwork: Color,
    fallback: Color = Color.White,
): Color {
    if (artwork == Color.Unspecified) return fallback
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(artwork.toArgb(), hsv)
    if (hsv[1] >= NeutralSaturation) hsv[1] = hsv[1].coerceIn(MinSaturation, MaxSaturation)
    hsv[2] = hsv[2].coerceIn(MinBrightness, 1f)
    return Color(AndroidColor.HSVToColor(hsv))
}
