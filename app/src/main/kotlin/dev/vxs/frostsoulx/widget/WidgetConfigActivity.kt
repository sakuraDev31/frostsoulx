/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.toMutablePreferences
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.launch

/**
 * "Configure widget" screen, shown when a widget is added and from the launcher's
 * reconfigure action. Writes the choices into that widget's own Glance preferences.
 */
class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId =
            intent?.extras?.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val provider =
            AppWidgetManager
                .getInstance(this)
                .getAppWidgetInfo(appWidgetId)
                ?.provider
                ?.className
                .orEmpty()

        setContent {
            MaterialTheme(colorScheme = ConfigColors) {
                WidgetConfigScreen(
                    showLayoutOptions = provider.endsWith("MusicWidgetReceiver"),
                    load = { loadSettings() },
                    onApply = { settings -> persist(settings, provider) },
                    onCancel = { finish() },
                )
            }
        }
    }

    private suspend fun loadSettings(): WidgetSettings =
        runCatching {
            val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
            getAppWidgetState(this, PreferencesGlanceStateDefinition, glanceId).toWidgetSettings()
        }.getOrDefault(WidgetSettings())

    private suspend fun persist(
        settings: WidgetSettings,
        provider: String,
    ) {
        runCatching {
            val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply { writeWidgetSettings(settings) }
            }
            widgetFor(provider)?.update(this, glanceId)
        }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    private fun widgetFor(provider: String): GlanceAppWidget? =
        when (provider.substringAfterLast('.')) {
            "MusicWidgetReceiver" -> MusicWidget()
            "AlbumArtWidgetReceiver" -> AlbumArtWidget()
            "PlaybackDeckWidgetReceiver" -> PlaybackDeckWidget()
            "NowPlayingCardWidgetReceiver" -> NowPlayingCardWidget()
            "PlaybackCapsuleWidgetReceiver" -> PlaybackCapsuleWidget()
            "PlaybackCommandWidgetReceiver" -> PlaybackCommandWidget()
            "PlaybackSpotlightWidgetReceiver" -> PlaybackSpotlightWidget()
            "ListeningInsightsWidgetReceiver" -> ListeningInsightsWidget()
            else -> null
        }
}

private val ConfigColors =
    darkColorScheme(
        primary = Color.White,
        onPrimary = Color.Black,
        background = Color.Black,
        onBackground = Color(0xFFF5F5F5),
        surface = Color(0xFF0B0B0B),
        onSurface = Color(0xFFF5F5F5),
        onSurfaceVariant = Color(0xFF9A9A9A),
        outline = Color(0xFF292929),
    )

private val BackgroundSwatches =
    listOf(0xFF000000, 0xFF0B0B0B, 0xFF121A2B, 0xFF2A1010, 0xFF0F2A1E, 0xFF241838, 0xFFF2F2F2, 0xFFEADCC5)
        .map { it.toInt() }

private val ForegroundSwatches =
    listOf(0xFFF5F5F5, 0xFF111111, 0xFFEADCC5, 0xFF7DE3FF, 0xFFFFB4A8, 0xFFB9F6CA)
        .map { it.toInt() }

private data class ThemeChoice(
    val id: Int,
    val title: String,
    val description: String,
)

private val ThemeChoices =
    listOf(
        ThemeChoice(WidgetSettings.THEME_ARTWORK, "Artwork", "Colors pulled from the cover"),
        ThemeChoice(WidgetSettings.THEME_CALM, "Calm", "Monochrome, like the app"),
        ThemeChoice(WidgetSettings.THEME_MATERIAL_YOU, "Material You", "Colors from your wallpaper (Android 12+)"),
        ThemeChoice(WidgetSettings.THEME_CUSTOM, "Custom", "Pick your own colors"),
    )

@Composable
private fun WidgetConfigScreen(
    showLayoutOptions: Boolean,
    load: suspend () -> WidgetSettings,
    onApply: suspend (WidgetSettings) -> Unit,
    onCancel: () -> Unit,
) {
    var settings by remember { mutableStateOf(WidgetSettings()) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        settings = load()
        loaded = true
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding(),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = "Configure widget",
                color = Color(0xFFF5F5F5),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(20.dp))

            SectionLabel("Theme")
            ThemeChoices.forEach { choice ->
                ChoiceRow(
                    title = choice.title,
                    description = choice.description,
                    selected = settings.theme == choice.id,
                    onClick = { settings = settings.copy(theme = choice.id) },
                )
            }

            if (settings.theme == WidgetSettings.THEME_CUSTOM) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("Background")
                SwatchRow(
                    colors = BackgroundSwatches,
                    selected = settings.customBackground,
                    onSelect = { settings = settings.copy(customBackground = it) },
                )
                Spacer(Modifier.height(12.dp))
                SectionLabel("Text and icons")
                SwatchRow(
                    colors = ForegroundSwatches,
                    selected = settings.customForeground,
                    onSelect = { settings = settings.copy(customForeground = it) },
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Background opacity: " + settings.opacity + "%")
            Slider(
                value = settings.opacity.toFloat(),
                onValueChange = { settings = settings.copy(opacity = it.toInt()) },
                valueRange = WidgetSettings.MIN_OPACITY.toFloat()..100f,
            )

            if (showLayoutOptions) {
                Spacer(Modifier.height(20.dp))
                SectionLabel("Artwork shape")
                ChoiceRow(
                    title = "Rounded",
                    description = "Soft square corners that scale with the cover",
                    selected = !settings.circleArtwork,
                    onClick = { settings = settings.copy(circleArtwork = false) },
                )
                ChoiceRow(
                    title = "Circle",
                    description = "Round cover",
                    selected = settings.circleArtwork,
                    onClick = { settings = settings.copy(circleArtwork = true) },
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Show progress bar",
                        color = Color(0xFFF5F5F5),
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = settings.showProgress,
                        onCheckedChange = { settings = settings.copy(showProgress = it) },
                    )
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color(0xFFB6B6B6))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = loaded,
                onClick = { scope.launch { onApply(settings) } },
            ) {
                Text("Apply")
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Color(0xFF9A9A9A),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun ChoiceRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(text = title, color = Color(0xFFF5F5F5), fontSize = 16.sp)
            Text(text = description, color = Color(0xFF9A9A9A), fontSize = 13.sp)
        }
    }
}

@Composable
private fun SwatchRow(
    colors: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEach { value ->
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(value))
                        .border(
                            width = if (value == selected) 2.dp else 1.dp,
                            color = if (value == selected) Color.White else Color(0x3DFFFFFF),
                            shape = CircleShape,
                        ).clickable { onSelect(value) },
            )
        }
    }
}
