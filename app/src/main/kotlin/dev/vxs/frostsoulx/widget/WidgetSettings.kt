/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences

/** Per-widget appearance, stored in the widget's own Glance preferences. */
internal data class WidgetSettings(
    val theme: Int = THEME_ARTWORK,
    val opacity: Int = DEFAULT_OPACITY,
    val customBackground: Int = DEFAULT_CUSTOM_BACKGROUND,
    val customForeground: Int = DEFAULT_CUSTOM_FOREGROUND,
    val circleArtwork: Boolean = false,
    val showProgress: Boolean = true,
) {
    companion object {
        const val THEME_ARTWORK = 0
        const val THEME_CALM = 1
        const val THEME_MATERIAL_YOU = 2
        const val THEME_CUSTOM = 3

        const val MIN_OPACITY = 20
        const val DEFAULT_OPACITY = 95

        val DEFAULT_CUSTOM_BACKGROUND: Int = 0xFF0B0B0B.toInt()
        val DEFAULT_CUSTOM_FOREGROUND: Int = 0xFFF5F5F5.toInt()
    }
}

internal fun Preferences.toWidgetSettings(): WidgetSettings =
    WidgetSettings(
        theme =
            (this[MusicWidgetKeys.WIDGET_THEME] ?: WidgetSettings.THEME_ARTWORK)
                .coerceIn(WidgetSettings.THEME_ARTWORK, WidgetSettings.THEME_CUSTOM),
        opacity =
            (this[MusicWidgetKeys.WIDGET_OPACITY] ?: WidgetSettings.DEFAULT_OPACITY)
                .coerceIn(WidgetSettings.MIN_OPACITY, 100),
        customBackground = this[MusicWidgetKeys.WIDGET_CUSTOM_BG] ?: WidgetSettings.DEFAULT_CUSTOM_BACKGROUND,
        customForeground = this[MusicWidgetKeys.WIDGET_CUSTOM_FG] ?: WidgetSettings.DEFAULT_CUSTOM_FOREGROUND,
        circleArtwork = this[MusicWidgetKeys.WIDGET_ART_CIRCLE] ?: false,
        showProgress = this[MusicWidgetKeys.WIDGET_SHOW_PROGRESS] ?: true,
    )

internal fun MutablePreferences.writeWidgetSettings(settings: WidgetSettings) {
    this[MusicWidgetKeys.WIDGET_THEME] = settings.theme
    this[MusicWidgetKeys.WIDGET_OPACITY] = settings.opacity
    this[MusicWidgetKeys.WIDGET_CUSTOM_BG] = settings.customBackground
    this[MusicWidgetKeys.WIDGET_CUSTOM_FG] = settings.customForeground
    this[MusicWidgetKeys.WIDGET_ART_CIRCLE] = settings.circleArtwork
    this[MusicWidgetKeys.WIDGET_SHOW_PROGRESS] = settings.showProgress
}
