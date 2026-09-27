/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package dev.vxs.frostsoulx.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.palette.graphics.Palette
import dev.vxs.frostsoulx.constants.AppFontPreference
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulDesignSystem

val LocalArchiveTuneFontPreference = staticCompositionLocalOf { AppFontPreference.DEFAULT }
val LocalArchiveTuneFontFamily = staticCompositionLocalOf { AppFontFamily }

@Composable
fun rememberArchiveTuneLyricsFontFamily(): FontFamily {
    val fontPreference = LocalArchiveTuneFontPreference.current
    val fontFamily = LocalArchiveTuneFontFamily.current
    return remember(fontPreference, fontFamily) {
        if (fontPreference == AppFontPreference.DEFAULT) LyricsFontFamily else fontFamily
    }
}

private val PureBlackColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1A1A1A),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFD0D0D0),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1A1A1A),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFBDBDBD),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF202020),
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF181818),
    onSurfaceVariant = Color(0xFFD0D0D0),
    outline = Color(0xFF505050),
    outlineVariant = Color(0xFF2B2B2B),
    scrim = Color.Black,
)

private val PureWhiteColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECEFF3),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF404040),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECEFF3),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF555555),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE2E6EC),
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFECEFF3),
    onSurfaceVariant = Color(0xFF454545),
    outline = Color(0xFF777777),
    outlineVariant = Color(0xFFCCCCCC),
    scrim = Color.Black,
)

fun Bitmap.extractThemeColor(): Color {
    val swatch =
        Palette
            .from(this)
            .maximumColorCount(16)
            .generate()
            .dominantSwatch
    return swatch?.rgb?.let { Color(it.toLong() and 0xFFFFFFFFL) } ?: Color(0xFF808080)
}

@Composable
fun ArchiveTuneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    disableAnimations: Boolean = false,
    fontPreference: AppFontPreference = AppFontPreference.DEFAULT,
    customFontUri: String = "",
    content: @Composable () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val customFontFamily =
        produceState<FontFamily?>(
            initialValue = null,
            context,
            fontPreference,
            customFontUri,
        ) {
            value =
                if (fontPreference == AppFontPreference.CUSTOM && customFontUri.isNotBlank()) {
                    CustomFontLoader.loadFontFamily(context.applicationContext, customFontUri)
                } else {
                    null
                }
        }.value
    val resolvedFontFamily =
        remember(fontPreference, customFontFamily) {
            when (fontPreference) {
                AppFontPreference.DEFAULT -> AppFontFamily
                AppFontPreference.SYSTEM -> FontFamily.Default
                AppFontPreference.CUSTOM -> customFontFamily ?: AppFontFamily
            }
        }
    val typography =
        remember(resolvedFontFamily) {
            when (resolvedFontFamily) {
                AppFontFamily -> AppTypography
                FontFamily.Default -> SystemTypography
                else -> typographyFor(resolvedFontFamily)
            }
        }
    val colorScheme = if (darkTheme) PureBlackColorScheme else PureWhiteColorScheme

    CompositionLocalProvider(
        LocalArchiveTuneFontPreference provides fontPreference,
        LocalArchiveTuneFontFamily provides resolvedFontFamily,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
        ) {
            FrostSoulDesignSystem(
                darkTheme = darkTheme,
                content = content,
            )
        }
    }
}
