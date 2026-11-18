package dev.vxs.frostsoulx.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import dev.vxs.frostsoulx.LocalPlayerAwareWindowInsets
import dev.vxs.frostsoulx.LocalPlayerConnection
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.constants.SpatialActiveEffectIdKey
import dev.vxs.frostsoulx.constants.SpatialActiveTabKey
import dev.vxs.frostsoulx.constants.SpatialAzimuthKey
import dev.vxs.frostsoulx.constants.SpatialBassWidthKey
import dev.vxs.frostsoulx.constants.SpatialDistanceKey
import dev.vxs.frostsoulx.constants.SpatialElevationKey
import dev.vxs.frostsoulx.constants.SpatialFavoriteEffectIdsKey
import dev.vxs.frostsoulx.constants.SpatialMovingSoundSpeedKey
import dev.vxs.frostsoulx.constants.SpatialOrbitEnabledKey
import dev.vxs.frostsoulx.constants.StereoSurroundBassGainDbKey
import dev.vxs.frostsoulx.constants.StereoSurroundCarFaderKey
import dev.vxs.frostsoulx.constants.StereoSurroundDampeningKey
import dev.vxs.frostsoulx.constants.StereoSurroundEnabledKey
import dev.vxs.frostsoulx.constants.StereoSurroundIntensityKey
import dev.vxs.frostsoulx.constants.StereoSurroundOutputGainDbKey
import dev.vxs.frostsoulx.constants.StereoSurroundQuantumFramesKey
import dev.vxs.frostsoulx.constants.StereoSurroundReflectionAmountKey
import dev.vxs.frostsoulx.constants.StereoSurroundReverbTimeKey
import dev.vxs.frostsoulx.constants.StereoSurroundRoomMixKey
import dev.vxs.frostsoulx.constants.StereoSurroundRoomPresetKey
import dev.vxs.frostsoulx.constants.StereoSurroundRoomSizeKey
import dev.vxs.frostsoulx.constants.StereoSurroundSavedPresetsKey
import dev.vxs.frostsoulx.constants.StereoSurroundStereoWidthKey
import dev.vxs.frostsoulx.playback.ImmersiveAudioPreset
import dev.vxs.frostsoulx.playback.ImmersiveAudioRuntime
import dev.vxs.frostsoulx.playback.ImmersiveControls
import dev.vxs.frostsoulx.playback.ImmersiveRoomPreset
import dev.vxs.frostsoulx.ui.screens.settings.spatial.BuiltInSpatialEffects
import dev.vxs.frostsoulx.ui.screens.settings.spatial.EffectDetailPage
import dev.vxs.frostsoulx.ui.screens.settings.spatial.EffectsTab
import dev.vxs.frostsoulx.ui.screens.settings.spatial.EqualizerTab
import dev.vxs.frostsoulx.ui.screens.settings.spatial.FineTuneScreen
import dev.vxs.frostsoulx.ui.screens.settings.spatial.PresetsTab
import dev.vxs.frostsoulx.ui.screens.settings.spatial.SpatialAudioTokens
import dev.vxs.frostsoulx.ui.screens.settings.spatial.SpatialEffect
import dev.vxs.frostsoulx.ui.screens.settings.spatial.rememberSpatialColorScheme
import dev.vxs.frostsoulx.utils.dataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpatialAudioScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dynamic color scheme from current playing track artwork with Contrast Guard
    val playerConnection = LocalPlayerConnection.current
    val mediaMetadata by (playerConnection?.mediaMetadata ?: MutableStateFlow(null)).collectAsState()
    val artworkUrl = mediaMetadata?.thumbnailUrl
    val dynamicColorScheme = rememberSpatialColorScheme(artworkUrl)

    // DataStore Preferences
    val preferences by context.dataStore.data.collectAsState(initial = null)
    var controls by remember { mutableStateOf(ImmersiveAudioRuntime.currentControls()) }

    // Sync controls from preferences
    LaunchedEffect(preferences) {
        val p = preferences ?: return@LaunchedEffect
        controls = readSpatialControls(p)
    }

    fun updateControls(value: ImmersiveControls) {
        controls = value.sanitized()
        ImmersiveAudioRuntime.applyControls(controls)
        val snapshot = controls
        scope.launch {
            context.dataStore.edit { writeSpatialControls(it, snapshot) }
        }
    }

    // Tab state (0: Effects, 1: My presets, 2: Equalizer), persisted in DataStore
    var selectedTab by remember { mutableIntStateOf(preferences?.get(SpatialActiveTabKey) ?: 0) }
    LaunchedEffect(preferences?.get(SpatialActiveTabKey)) {
        preferences?.get(SpatialActiveTabKey)?.let { savedTab ->
            if (savedTab in 0..2) selectedTab = savedTab
        }
    }

    fun selectTab(tabIndex: Int) {
        selectedTab = tabIndex
        scope.launch {
            context.dataStore.edit { it[SpatialActiveTabKey] = tabIndex }
        }
    }

    // Active Effect ID & Favorites persisted
    val activeEffectId = preferences?.get(SpatialActiveEffectIdKey) ?: "studio"
    val favoriteEffectIds = preferences?.get(SpatialFavoriteEffectIdsKey) ?: emptySet()
    var movingSoundSpeed by remember { mutableFloatStateOf(preferences?.get(SpatialMovingSoundSpeedKey) ?: 1.0f) }

    fun toggleFavorite(effectId: String) {
        scope.launch {
            context.dataStore.edit { prefs ->
                val current = prefs[SpatialFavoriteEffectIdsKey] ?: emptySet()
                prefs[SpatialFavoriteEffectIdsKey] = if (effectId in current) current - effectId else current + effectId
            }
        }
    }

    fun applyEffect(effect: SpatialEffect) {
        val previousControls = controls
        val newControls = effect.defaultControls.copy(enabled = true)
        updateControls(newControls)
        scope.launch {
            context.dataStore.edit { it[SpatialActiveEffectIdKey] = effect.id }
            val result = snackbarHostState.showSnackbar(
                message = "${effect.name} on · Undo",
                actionLabel = "Undo",
            )
            if (result == SnackbarResult.ActionPerformed) {
                updateControls(previousControls)
            }
        }
    }

    // Navigation subpage state: null = main 3-tab screen, non-null = Detail page
    var detailEffect by remember { mutableStateOf<SpatialEffect?>(null) }
    var isFineTuneOpen by remember { mutableStateOf(false) }

    // Saved presets list
    val savedPresets = remember(preferences) {
        ImmersiveAudioPreset.decodeAll(preferences?.get(StereoSurroundSavedPresetsKey) ?: "[]")
    }

    // Mini Player aware bottom insets padding
    val playerInsets = LocalPlayerAwareWindowInsets.current.asPaddingValues()

    // Android System Back Button Handler
    BackHandler(enabled = isFineTuneOpen || detailEffect != null) {
        if (isFineTuneOpen) {
            isFineTuneOpen = false
        } else if (detailEffect != null) {
            detailEffect = null
        }
    }

    MaterialTheme(colorScheme = dynamicColorScheme) {
        when {
            // Screen 3: Fine tune advanced accordion screen
            isFineTuneOpen -> {
                FineTuneScreen(
                    currentControls = controls,
                    onControlsChange = ::updateControls,
                    onBackClick = { isFineTuneOpen = false },
                    contentPadding = playerInsets,
                )
            }

            // Screen 2: Effect detail page
            detailEffect != null -> {
                val currentEffect = detailEffect!!
                EffectDetailPage(
                    effect = currentEffect,
                    currentControls = controls,
                    isUsing = controls.enabled && activeEffectId == currentEffect.id,
                    isFavorite = currentEffect.id in favoriteEffectIds,
                    movingSoundSpeed = movingSoundSpeed,
                    onMovingSoundSpeedChange = { speed ->
                        movingSoundSpeed = speed
                        scope.launch {
                            context.dataStore.edit { it[SpatialMovingSoundSpeedKey] = speed }
                        }
                    },
                    onApplyEffect = { applyEffect(currentEffect) },
                    onToggleFavorite = { toggleFavorite(currentEffect.id) },
                    onControlsChange = ::updateControls,
                    onOpenFineTune = { isFineTuneOpen = true },
                    onSaveAsPreset = { name, presetControls, baseName ->
                        val newPreset = ImmersiveAudioPreset.fromControls(
                            name = name,
                            controls = presetControls,
                            basedOn = baseName,
                        )
                        scope.launch {
                            context.dataStore.edit {
                                it[StereoSurroundSavedPresetsKey] = ImmersiveAudioPreset.encodeAll(savedPresets + newPreset)
                            }
                        }
                    },
                    onBackClick = { detailEffect = null },
                    contentPadding = playerInsets,
                )
            }

            // Screen 1: Main 3-Tab Spatial Audio Screen (Effects | My presets | Equalizer)
            else -> {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.spatial_audio_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { navController.navigateUp() }) {
                                    Icon(
                                        painter = painterResource(R.drawable.arrow_back),
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            },
                            actions = {
                                // Master switch: dims content to 38% alpha and disables interaction when off
                                Switch(
                                    checked = controls.enabled,
                                    onCheckedChange = { checked ->
                                        updateControls(controls.copy(enabled = checked))
                                    },
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .semantics { contentDescription = "Master spatial audio switch" },
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        // 3-Tab Row: Effects | My presets | Equalizer
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    height = 3.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                            divider = {},
                        ) {
                            val tabs = listOf(
                                stringResource(R.string.spatial_tab_effects),
                                stringResource(R.string.spatial_tab_presets),
                                stringResource(R.string.spatial_tab_equalizer),
                            )
                            tabs.forEachIndexed { index, tabTitle ->
                                val selected = selectedTab == index
                                Tab(
                                    selected = selected,
                                    onClick = { selectTab(index) },
                                    text = {
                                        Text(
                                            text = tabTitle,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            ),
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                )
                            }
                        }

                        // Content container: When master switch is off, dims to 38% alpha and disables interaction
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .alpha(if (controls.enabled) 1.0f else SpatialAudioTokens.DisabledAlpha),
                        ) {
                            Crossfade(
                                targetState = selectedTab,
                                animationSpec = tween(durationMillis = 300),
                                label = "TabCrossfade",
                            ) { tab ->
                                when (tab) {
                                    0 -> EffectsTab(
                                        activeEffectId = if (controls.enabled) activeEffectId else null,
                                        favoriteIds = favoriteEffectIds,
                                        onEffectUse = ::applyEffect,
                                        onEffectOpen = { detailEffect = it },
                                        onToggleFavorite = ::toggleFavorite,
                                        contentPadding = playerInsets,
                                    )

                                    1 -> PresetsTab(
                                        presets = savedPresets,
                                        currentControls = controls,
                                        activeEffectName = BuiltInSpatialEffects.firstOrNull { it.id == activeEffectId }?.name,
                                        onApplyPreset = { preset ->
                                            updateControls(preset.toControls().copy(enabled = true))
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Preset \"${preset.name}\" applied")
                                            }
                                        },
                                        onSaveCurrentAsPreset = { name ->
                                            val newPreset = ImmersiveAudioPreset.fromControls(
                                                name = name,
                                                controls = controls,
                                                basedOn = BuiltInSpatialEffects.firstOrNull { it.id == activeEffectId }?.name ?: "",
                                            )
                                            scope.launch {
                                                context.dataStore.edit {
                                                    it[StereoSurroundSavedPresetsKey] = ImmersiveAudioPreset.encodeAll(savedPresets + newPreset)
                                                }
                                            }
                                        },
                                        onRenamePreset = { index, newName ->
                                            val updated = savedPresets.mapIndexed { idx, p ->
                                                if (idx == index) p.copy(name = newName) else p
                                            }
                                            scope.launch {
                                                context.dataStore.edit {
                                                    it[StereoSurroundSavedPresetsKey] = ImmersiveAudioPreset.encodeAll(updated)
                                                }
                                            }
                                        },
                                        onDeletePreset = { index ->
                                            val updated = savedPresets.filterIndexed { idx, _ -> idx != index }
                                            scope.launch {
                                                context.dataStore.edit {
                                                    it[StereoSurroundSavedPresetsKey] = ImmersiveAudioPreset.encodeAll(updated)
                                                }
                                            }
                                        },
                                        onRestorePreset = { index, restored ->
                                            val updated = savedPresets.toMutableList().apply { add(index.coerceIn(0, size), restored) }
                                            scope.launch {
                                                context.dataStore.edit {
                                                    it[StereoSurroundSavedPresetsKey] = ImmersiveAudioPreset.encodeAll(updated)
                                                }
                                            }
                                        },
                                        snackbarHostState = snackbarHostState,
                                        contentPadding = playerInsets,
                                    )

                                    2 -> EqualizerTab(
                                        currentControls = controls,
                                        onControlsChange = ::updateControls,
                                        snackbarHostState = snackbarHostState,
                                        contentPadding = playerInsets,
                                    )
                                }
                            }

                            // If master switch is off, overlay invisible blocking layer to disable clicks
                            if (!controls.enabled) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Turn on the master switch to enable effects")
                                                }
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun readSpatialControls(p: Preferences): ImmersiveControls = ImmersiveControls(
    enabled = p[StereoSurroundEnabledKey] ?: false,
    intensity = p[StereoSurroundIntensityKey] ?: 0.5f,
    roomPreset = ImmersiveRoomPreset.fromNative(p[StereoSurroundRoomPresetKey] ?: 2),
    roomMix = p[StereoSurroundRoomMixKey] ?: 0.18f,
    reflectionAmount = p[StereoSurroundReflectionAmountKey] ?: 0.28f,
    reverbTimeSeconds = p[StereoSurroundReverbTimeKey] ?: 1.35f,
    roomSize = p[StereoSurroundRoomSizeKey] ?: 0.5f,
    dampening = p[StereoSurroundDampeningKey] ?: 0.5f,
    stereoWidth = p[StereoSurroundStereoWidthKey] ?: 0.5f,
    carFader = p[StereoSurroundCarFaderKey] ?: 0f,
    bassGainDb = p[StereoSurroundBassGainDbKey] ?: 0f,
    outputGainDb = p[StereoSurroundOutputGainDbKey] ?: 0f,
    quantumFrames = p[StereoSurroundQuantumFramesKey] ?: 384,
    azimuth = p[SpatialAzimuthKey] ?: 0f,
    elevation = p[SpatialElevationKey] ?: 0f,
    distance = p[SpatialDistanceKey] ?: 1f,
    orbitEnabled = p[SpatialOrbitEnabledKey] ?: false,
    bassWidth = p[SpatialBassWidthKey] ?: 1f,
).sanitized()

private fun writeSpatialControls(p: androidx.datastore.preferences.core.MutablePreferences, c: ImmersiveControls) {
    p[StereoSurroundEnabledKey] = c.enabled
    p[StereoSurroundIntensityKey] = c.intensity
    p[StereoSurroundRoomPresetKey] = c.roomPreset.nativeValue
    p[StereoSurroundRoomMixKey] = c.roomMix
    p[StereoSurroundReflectionAmountKey] = c.reflectionAmount
    p[StereoSurroundReverbTimeKey] = c.reverbTimeSeconds
    p[StereoSurroundRoomSizeKey] = c.roomSize
    p[StereoSurroundDampeningKey] = c.dampening
    p[StereoSurroundStereoWidthKey] = c.stereoWidth
    p[StereoSurroundCarFaderKey] = c.carFader
    p[StereoSurroundBassGainDbKey] = c.bassGainDb
    p[StereoSurroundOutputGainDbKey] = c.outputGainDb
    p[StereoSurroundQuantumFramesKey] = c.quantumFrames
    p[SpatialAzimuthKey] = c.azimuth
    p[SpatialElevationKey] = c.elevation
    p[SpatialDistanceKey] = c.distance
    p[SpatialOrbitEnabledKey] = c.orbitEnabled
    p[SpatialBassWidthKey] = c.bassWidth
}
