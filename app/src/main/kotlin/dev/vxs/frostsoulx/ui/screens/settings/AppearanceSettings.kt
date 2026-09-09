/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vxs.frostsoulx.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.vxs.frostsoulx.LocalPlayerAwareWindowInsets
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.constants.AppFontPreference
import dev.vxs.frostsoulx.constants.ArchiveTuneCanvasKey
import dev.vxs.frostsoulx.constants.BackdropBlurAmountKey
import dev.vxs.frostsoulx.constants.BackdropEnabledKey
import dev.vxs.frostsoulx.constants.BlurRadiusKey
import dev.vxs.frostsoulx.constants.ChipSortTypeKey
import dev.vxs.frostsoulx.constants.CropThumbnailToSquareKey
import dev.vxs.frostsoulx.constants.CustomFontNameKey
import dev.vxs.frostsoulx.constants.CustomFontUriKey
import dev.vxs.frostsoulx.constants.DarkModeKey
import dev.vxs.frostsoulx.constants.DefaultOpenTabKey
import dev.vxs.frostsoulx.constants.DisableAnimationsKey
import dev.vxs.frostsoulx.constants.DisableBlurKey
import dev.vxs.frostsoulx.constants.FontPreferenceKey
import dev.vxs.frostsoulx.constants.ForceHighRefreshRateKey
import dev.vxs.frostsoulx.constants.GlassGrainIntensityKey
import dev.vxs.frostsoulx.constants.GridItemSize
import dev.vxs.frostsoulx.constants.GridItemsSizeKey
import dev.vxs.frostsoulx.constants.HidePlayerThumbnailKey
import dev.vxs.frostsoulx.constants.PlayerBackgroundStyle
import dev.vxs.frostsoulx.constants.PlayerBackgroundStyleKey
import dev.vxs.frostsoulx.constants.PlayerDesignStyle
import dev.vxs.frostsoulx.constants.PlayerDesignStyleKey
import dev.vxs.frostsoulx.constants.LibraryFilter
import dev.vxs.frostsoulx.constants.LyricsBackgroundStyle
import dev.vxs.frostsoulx.constants.LyricsBackgroundStyleKey
import dev.vxs.frostsoulx.constants.QuickPicksDisplayMode
import dev.vxs.frostsoulx.constants.QuickPicksDisplayModeKey
import dev.vxs.frostsoulx.constants.ShowHomeCategoryChipsKey
import dev.vxs.frostsoulx.constants.ShowPlayerVolumeBarKey
import dev.vxs.frostsoulx.constants.ShowTagsInLibraryKey
import dev.vxs.frostsoulx.constants.SwipeSensitivityKey
import dev.vxs.frostsoulx.constants.SwipeThumbnailKey
import dev.vxs.frostsoulx.constants.SwipeToSongKey
import dev.vxs.frostsoulx.constants.ThumbnailCornerRadiusKey
import dev.vxs.frostsoulx.ui.component.DefaultDialog
import dev.vxs.frostsoulx.ui.component.EnumListPreference
import dev.vxs.frostsoulx.ui.component.IconButton
import dev.vxs.frostsoulx.ui.component.ListPreference
import dev.vxs.frostsoulx.ui.component.PreferenceEntry
import dev.vxs.frostsoulx.ui.component.PreferenceGroup
import dev.vxs.frostsoulx.ui.component.SwitchPreference
import dev.vxs.frostsoulx.ui.component.ThumbnailCornerRadiusSelectorButton
import dev.vxs.frostsoulx.ui.theme.CustomFontLoader
import dev.vxs.frostsoulx.ui.utils.backToMain
import dev.vxs.frostsoulx.utils.isLowRamDevice
import dev.vxs.frostsoulx.utils.rememberEnumPreference
import dev.vxs.frostsoulx.utils.rememberPreference
import dev.vxs.frostsoulx.ui.theme.HIGH_REFRESH_RATE_THRESHOLD_FPS
import dev.vxs.frostsoulx.ui.theme.rememberSupportedHighestFps
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(navController: NavController) {
    val context = LocalContext.current
    val defaultDisableAnimations = remember(context) { context.isLowRamDevice() }
    val (darkMode, onDarkModeChange) =
        rememberEnumPreference(
            DarkModeKey,
            defaultValue = DarkMode.AUTO,
        )
    val (showPlayerVolumeBar, onShowPlayerVolumeBarChange) =
        rememberPreference(
            ShowPlayerVolumeBarKey,
            defaultValue = true,
        )
    val (playerDesignStyle, onPlayerDesignStyleChange) =
        rememberEnumPreference(
            PlayerDesignStyleKey,
            defaultValue = PlayerDesignStyle.FROSTSOUL,
        )
    val (playerBackgroundStyle, onPlayerBackgroundStyleChange) =
        rememberEnumPreference(
            PlayerBackgroundStyleKey,
            defaultValue = PlayerBackgroundStyle.GLOW_ANIMATED,
        )
    val (hidePlayerThumbnail, onHidePlayerThumbnailChange) =
        rememberPreference(
            HidePlayerThumbnailKey,
            defaultValue = false,
        )
    val (archiveTuneCanvasEnabled, onArchiveTuneCanvasEnabledChange) =
        rememberPreference(
            ArchiveTuneCanvasKey,
            defaultValue = false,
        )
    val (thumbnailCornerRadius, onThumbnailCornerRadiusChange) =
        rememberPreference(
            key = ThumbnailCornerRadiusKey,
            defaultValue = 16f, // default dp
        )
    val (cropThumbnailToSquare, onCropThumbnailToSquareChange) =
        rememberPreference(
            CropThumbnailToSquareKey,
            defaultValue = false,
        )
    val (configuredLyricsBackground, onLyricsBackgroundChange) =
        rememberEnumPreference(
            LyricsBackgroundStyleKey,
            defaultValue = LyricsBackgroundStyle.DEFAULT,
        )
    val (disableBlur, onDisableBlurChange) = rememberPreference(DisableBlurKey, defaultValue = false)
    val (disableAnimations, onDisableAnimationsChange) =
        rememberPreference(
            DisableAnimationsKey,
            defaultValue = defaultDisableAnimations,
        )
    val (forceHighRefreshRate, onForceHighRefreshRateChange) =
        rememberPreference(
            ForceHighRefreshRateKey,
            defaultValue = false,
        )
    val (blurRadius, onBlurRadiusChange) = rememberPreference(BlurRadiusKey, defaultValue = 48f)
    val (glassGrainIntensity, onGlassGrainIntensityChange) = rememberPreference(GlassGrainIntensityKey, defaultValue = 0.35f)
    val (backdropEnabled, onBackdropEnabledChange) = rememberPreference(BackdropEnabledKey, defaultValue = true)
    val (backdropBlurAmount, onBackdropBlurAmountChange) = rememberPreference(BackdropBlurAmountKey, defaultValue = 60)
    val (fontPreference, onFontPreferenceChange) =
        rememberEnumPreference(
            FontPreferenceKey,
            defaultValue = AppFontPreference.DEFAULT,
        )
    val (customFontUri, onCustomFontUriChange) = rememberPreference(CustomFontUriKey, defaultValue = "")
    val (customFontName, onCustomFontNameChange) = rememberPreference(CustomFontNameKey, defaultValue = "")
    val (defaultOpenTab, onDefaultOpenTabChange) =
        rememberEnumPreference(
            DefaultOpenTabKey,
            defaultValue = NavigationTab.HOME,
        )
    val (swipeThumbnail, onSwipeThumbnailChange) =
        rememberPreference(
            SwipeThumbnailKey,
            defaultValue = true,
        )
    val (swipeSensitivity, onSwipeSensitivityChange) =
        rememberPreference(
            SwipeSensitivityKey,
            defaultValue = 0.73f,
        )
    val (gridItemSize, onGridItemSizeChange) =
        rememberEnumPreference(
            GridItemsSizeKey,
            defaultValue = GridItemSize.SMALL,
        )

    val (swipeToSong, onSwipeToSongChange) =
        rememberPreference(
            SwipeToSongKey,
            defaultValue = false,
        )

    val (showTagsInLibrary, onShowTagsInLibraryChange) =
        rememberPreference(
            ShowTagsInLibraryKey,
            defaultValue = true,
        )
    val (showHomeCategoryChips, onShowHomeCategoryChipsChange) =
        rememberPreference(
            ShowHomeCategoryChipsKey,
            defaultValue = true,
        )
    val (quickPicksDisplayMode, onQuickPicksDisplayModeChange) =
        rememberEnumPreference(
            QuickPicksDisplayModeKey,
            defaultValue = QuickPicksDisplayMode.CARD,
        )

    val customFontPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            if (!CustomFontLoader.isSupportedTtf(context, uri)) {
                Toast.makeText(context, context.getString(R.string.custom_font_invalid), Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            if (customFontUri.isNotBlank() && customFontUri != uri.toString()) {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(customFontUri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }

            onCustomFontUriChange(uri.toString())
            onCustomFontNameChange(CustomFontLoader.displayName(context, uri))
            onFontPreferenceChange(AppFontPreference.CUSTOM)
        }
    val pickCustomFont =
        remember(customFontPickerLauncher) {
            {
                customFontPickerLauncher.launch(CustomFontLoader.supportedMimeTypes)
            }
        }
    val onFontPreferenceSelected =
        remember(customFontUri, onFontPreferenceChange, pickCustomFont) {
            { value: AppFontPreference ->
                onFontPreferenceChange(value)
                if (value == AppFontPreference.CUSTOM && customFontUri.isBlank()) {
                    pickCustomFont()
                }
            }
        }

    val availableLyricsBackgroundStyles =
        remember {
            listOf(
                LyricsBackgroundStyle.DEFAULT,
                LyricsBackgroundStyle.FOLLOW_THEME,
                LyricsBackgroundStyle.COLORING,
            )
        }
    val lyricsBackground = configuredLyricsBackground
    val availablePlayerDesignStyles =
        remember {
            listOf(
                PlayerDesignStyle.FROSTSOUL,
                PlayerDesignStyle.ARTWORK_BLUR,
            )
        }
    val availableVinylBackgroundStyles =
        remember {
            listOf(
                PlayerBackgroundStyle.BLUR,
                PlayerBackgroundStyle.GRADIENT,
                PlayerBackgroundStyle.GLOW_ANIMATED,
            )
        }
    val isVolumeBarSupported = true

    val (defaultChip, onDefaultChipChange) =
        rememberEnumPreference(
            key = ChipSortTypeKey,
            defaultValue = LibraryFilter.LIBRARY,
        )
    val supportedHighestFps = rememberSupportedHighestFps()
    val isHighRefreshRateSupported = supportedHighestFps > HIGH_REFRESH_RATE_THRESHOLD_FPS


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.theme)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.app_icon)) },
                        description = stringResource(R.string.app_icon_description),
                        icon = { Icon(painterResource(R.drawable.app_icon_small), null) },
                        onClick = { navController.navigate("settings/appearance/icon") },
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.dark_theme)) },
                        icon = { Icon(painterResource(R.drawable.dark_mode), null) },
                        selectedValue = darkMode,
                        onValueSelected = onDarkModeChange,
                        valueText = {
                            when (it) {
                                DarkMode.ON -> stringResource(R.string.dark_theme_on)
                                DarkMode.OFF -> stringResource(R.string.dark_theme_off)
                                DarkMode.AUTO -> stringResource(R.string.dark_theme_follow_system)
                            }
                        },
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.disable_blur)) },
                        description = stringResource(R.string.disable_blur_desc),
                        icon = { Icon(painterResource(R.drawable.blur_off), null) },
                        checked = disableBlur,
                        onCheckedChange = onDisableBlurChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.disable_animations)) },
                        description = stringResource(R.string.disable_animations_desc),
                        icon = { Icon(painterResource(R.drawable.animation), null) },
                        checked = disableAnimations,
                        onCheckedChange = onDisableAnimationsChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.force_high_refresh_rate)) },
                        description =
                            stringResource(
                                R.string.max_supported_refresh_rate,
                                supportedHighestFps.roundToInt(),
                            ),
                        icon = { Icon(painterResource(R.drawable.speed), null) },
                        checked = forceHighRefreshRate,
                        onCheckedChange = onForceHighRefreshRateChange,
                        isEnabled = isHighRefreshRateSupported,
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text("Glass blur radius") },
                        description = "Nav bar and mini-player backdrop blur: ${blurRadius.roundToInt()}dp",
                        icon = { Icon(painterResource(R.drawable.blur_on), null) },
                        isEnabled = !disableBlur,
                        content = {
                            Spacer(modifier = Modifier.height(10.dp))
                            Slider(
                                value = blurRadius,
                                onValueChange = onBlurRadiusChange,
                                valueRange = 0f..64f,
                                steps = 63,
                                enabled = !disableBlur,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text("Glass grain intensity") },
                        description = "Nav bar and mini-player fine grain: ${(glassGrainIntensity * 100f).roundToInt()}%",
                        icon = { Icon(painterResource(R.drawable.tune), null) },
                        content = {
                            Spacer(modifier = Modifier.height(10.dp))
                            Slider(
                                value = glassGrainIntensity,
                                onValueChange = { onGlassGrainIntensityChange(it.coerceIn(0f, 1f)) },
                                valueRange = 0f..1f,
                                steps = 19,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.album_backdrop)) },
                        description = stringResource(R.string.album_backdrop_desc),
                        icon = { Icon(painterResource(R.drawable.blur_on), null) },
                        checked = backdropEnabled,
                        onCheckedChange = onBackdropEnabledChange,
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.backdrop_blur_amount)) },
                        description = stringResource(R.string.backdrop_blur_amount_value, backdropBlurAmount),
                        icon = { Icon(painterResource(R.drawable.blur_on), null) },
                        isEnabled = backdropEnabled,
                        content = {
                            Spacer(modifier = Modifier.height(10.dp))
                            Slider(
                                value = backdropBlurAmount.toFloat(),
                                onValueChange = { onBackdropBlurAmountChange(it.roundToInt()) },
                                valueRange = 0f..100f,
                                steps = 19,
                                enabled = backdropEnabled,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.font_preference)) },
                        description = stringResource(R.string.font_preference_desc),
                        icon = { Icon(painterResource(R.drawable.text_fields), null) },
                        selectedValue = fontPreference,
                        onValueSelected = onFontPreferenceSelected,
                        valueText = {
                            when (it) {
                                AppFontPreference.DEFAULT -> stringResource(R.string.font_preference_default)
                                AppFontPreference.SYSTEM -> stringResource(R.string.font_preference_system)
                                AppFontPreference.CUSTOM -> stringResource(R.string.font_preference_custom)
                            }
                        },
                    )
                }

                item(visible = fontPreference == AppFontPreference.CUSTOM) {
                    val customFontDescription =
                        if (customFontName.isNotBlank()) {
                            customFontName
                        } else if (customFontUri.isBlank()) {
                            stringResource(R.string.custom_font_desc)
                        } else {
                            customFontUri
                        }
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.custom_font)) },
                        description = customFontDescription,
                        icon = { Icon(painterResource(R.drawable.text_fields), null) },
                        onClick = pickCustomFont,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.player)) {
                item {
                    ListPreference(
                        title = { Text("Player style") },
                        description = "Choose the player layout used on the Now Playing screen",
                        icon = { Icon(painterResource(R.drawable.music_note), null) },
                        selectedValue = playerDesignStyle,
                        values = availablePlayerDesignStyles,
                        onValueSelected = onPlayerDesignStyleChange,
                        isEnabled = true,
                        valueText = {
                            when (it) {
                                PlayerDesignStyle.FROSTSOUL -> "Vinyl player"
                                PlayerDesignStyle.ARTWORK_BLUR -> "Immersive"
                                else -> it.name
                            }
                        },
                    )
                }

                item {
                    ListPreference(
                        title = { Text("Vinyl player background") },
                        description =
                            if (playerDesignStyle == PlayerDesignStyle.FROSTSOUL) {
                                "Choose Blur, Gradient or Animated Glow"
                            } else {
                                "Clickable only when Vinyl player is selected"
                            },
                        icon = { Icon(painterResource(R.drawable.palette), null) },
                        selectedValue = playerBackgroundStyle,
                        values = availableVinylBackgroundStyles,
                        onValueSelected = onPlayerBackgroundStyleChange,
                        isEnabled = playerDesignStyle == PlayerDesignStyle.FROSTSOUL,
                        valueText = {
                            when (it) {
                                PlayerBackgroundStyle.BLUR -> "Blur"
                                PlayerBackgroundStyle.GRADIENT -> "Gradient"
                                PlayerBackgroundStyle.GLOW_ANIMATED -> "Animated Glow"
                                else -> it.name
                            }
                        },
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.show_player_volume_bar)) },
                        description =
                            if (isVolumeBarSupported) {
                                null
                            } else {
                                stringResource(R.string.player_volume_bar_v7_v8_only)
                            },
                        icon = { Icon(painterResource(R.drawable.volume_up), null) },
                        checked = showPlayerVolumeBar,
                        onCheckedChange = onShowPlayerVolumeBarChange,
                        isEnabled = isVolumeBarSupported,
                    )
                }

                item {
                    ListPreference(
                        title = { Text(stringResource(R.string.lyrics_background_style)) },
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        selectedValue = lyricsBackground,
                        values = availableLyricsBackgroundStyles,
                        onValueSelected = onLyricsBackgroundChange,
                        isEnabled = true,
                        valueText = {
                            when (it) {
                                LyricsBackgroundStyle.DEFAULT -> stringResource(R.string.lyrics_background_default)
                                LyricsBackgroundStyle.FOLLOW_THEME -> stringResource(R.string.follow_theme)
                                LyricsBackgroundStyle.COLORING -> stringResource(R.string.coloring)
                                LyricsBackgroundStyle.CUSTOM -> stringResource(R.string.custom)
                            }
                        },
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.hide_player_thumbnail)) },
                        description = stringResource(R.string.hide_player_thumbnail_desc),
                        icon = { Icon(painterResource(R.drawable.hide_image), null) },
                        checked = hidePlayerThumbnail,
                        onCheckedChange = onHidePlayerThumbnailChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.archivetune_canvas)) },
                        description = stringResource(R.string.archivetune_canvas_desc),
                        icon = { Icon(painterResource(R.drawable.motion_photos_on), null) },
                        checked = archiveTuneCanvasEnabled,
                        onCheckedChange = onArchiveTuneCanvasEnabledChange,
                    )
                }

                item {
                    ThumbnailCornerRadiusSelectorButton(
                        onRadiusSelected = {},
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.crop_thumbnail_to_square)) },
                        description = stringResource(R.string.crop_thumbnail_to_square_desc),
                        icon = { Icon(painterResource(R.drawable.image), null) },
                        checked = cropThumbnailToSquare,
                        onCheckedChange = onCropThumbnailToSquareChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.enable_swipe_thumbnail)) },
                        icon = { Icon(painterResource(R.drawable.swipe), null) },
                        checked = swipeThumbnail,
                        onCheckedChange = onSwipeThumbnailChange,
                    )
                }

                item(visible = swipeThumbnail) {
                    var showSensitivityDialog by rememberSaveable { mutableStateOf(false) }

                    if (showSensitivityDialog) {
                        var tempSensitivity by remember { mutableFloatStateOf(swipeSensitivity) }

                        DefaultDialog(
                            onDismiss = {
                                tempSensitivity = swipeSensitivity
                                showSensitivityDialog = false
                            },
                            buttons = {
                                TextButton(
                                    onClick = {
                                        tempSensitivity = 0.73f
                                    },
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text(stringResource(R.string.reset))
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                TextButton(
                                    onClick = {
                                        tempSensitivity = swipeSensitivity
                                        showSensitivityDialog = false
                                    },
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                                TextButton(
                                    onClick = {
                                        onSwipeSensitivityChange(tempSensitivity)
                                        showSensitivityDialog = false
                                    },
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Text(stringResource(android.R.string.ok))
                                }
                            },
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.swipe_sensitivity),
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )

                                Text(
                                    text = stringResource(R.string.sensitivity_percentage, (tempSensitivity * 100).roundToInt()),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(bottom = 16.dp),
                                )

                                Slider(
                                    value = tempSensitivity,
                                    onValueChange = { tempSensitivity = it },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    PreferenceEntry(
                        title = { Text(stringResource(R.string.swipe_sensitivity)) },
                        description = stringResource(R.string.sensitivity_percentage, (swipeSensitivity * 100).roundToInt()),
                        icon = { Icon(painterResource(R.drawable.tune), null) },
                        onClick = { showSensitivityDialog = true },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.misc)) {
                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.quick_picks_display_mode)) },
                        icon = { Icon(painterResource(R.drawable.grid_view), null) },
                        selectedValue = quickPicksDisplayMode,
                        onValueSelected = onQuickPicksDisplayModeChange,
                        valueText = {
                            when (it) {
                                QuickPicksDisplayMode.CARD -> stringResource(R.string.quick_picks_display_mode_card)
                                QuickPicksDisplayMode.LIST -> stringResource(R.string.quick_picks_display_mode_list)
                            }
                        },
                    )
                }

                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.default_open_tab)) },
                        icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                        selectedValue = defaultOpenTab,
                        onValueSelected = onDefaultOpenTabChange,
                        valueText = {
                            when (it) {
                                NavigationTab.HOME -> stringResource(R.string.home)
                                NavigationTab.SEARCH -> stringResource(R.string.search)
                                NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                            }
                        },
                    )
                }

                item {
                    ListPreference(
                        title = { Text(stringResource(R.string.default_lib_chips)) },
                        icon = { Icon(painterResource(R.drawable.tab), null) },
                        selectedValue = defaultChip,
                        values =
                            listOf(
                                LibraryFilter.LIBRARY,
                                LibraryFilter.PLAYLISTS,
                                LibraryFilter.SONGS,
                                LibraryFilter.ALBUMS,
                                LibraryFilter.ARTISTS,
                            ),
                        valueText = {
                            when (it) {
                                LibraryFilter.SONGS -> stringResource(R.string.songs)
                                LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                                LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                                LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                                LibraryFilter.SPOTIFY -> stringResource(R.string.spotify_playlists)
                                LibraryFilter.LIBRARY -> stringResource(R.string.filter_library)
                            }
                        },
                        onValueSelected = onDefaultChipChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.show_home_category_chips)) },
                        description = stringResource(R.string.show_home_category_chips_desc),
                        icon = { Icon(painterResource(R.drawable.home_outlined), null) },
                        checked = showHomeCategoryChips,
                        onCheckedChange = onShowHomeCategoryChipsChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.show_tags_in_library)) },
                        description = stringResource(R.string.show_tags_in_library_desc),
                        icon = { Icon(painterResource(R.drawable.filter_alt), null) },
                        checked = showTagsInLibrary,
                        onCheckedChange = onShowTagsInLibraryChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.swipe_song_to_add)) },
                        icon = { Icon(painterResource(R.drawable.swipe), null) },
                        checked = swipeToSong,
                        onCheckedChange = onSwipeToSongChange,
                    )
                }
            }
        }
    }
}


enum class DarkMode {
    ON,
    OFF,
    AUTO,
}

enum class NavigationTab {
    HOME,
    SEARCH,
    LIBRARY,
}

enum class PlayerTextAlignment {
    SIDED,
    CENTER,
}

enum class LyricsPosition {
    LEFT,
    CENTER,
    RIGHT,
}
