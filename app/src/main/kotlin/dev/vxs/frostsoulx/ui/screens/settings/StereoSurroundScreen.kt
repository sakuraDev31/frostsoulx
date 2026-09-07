package dev.vxs.frostsoulx.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.constants.StereoSurroundEnabledKey
import dev.vxs.frostsoulx.constants.StereoSurroundIntensityKey
import dev.vxs.frostsoulx.playback.StereoSurroundRuntime
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulTheme
import dev.vxs.frostsoulx.utils.rememberPreference

@Composable
fun StereoSurroundScreen(navController: NavController) {
    var enabled by rememberPreference(StereoSurroundEnabledKey, defaultValue = false)
    var intensity by rememberPreference(StereoSurroundIntensityKey, defaultValue = 0.5f)

    LaunchedEffect(enabled, intensity) {
        StereoSurroundRuntime.setIntensity(intensity)
        StereoSurroundRuntime.setEnabled(enabled)
    }

    Scaffold(
        containerColor = FrostSoulTheme.colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Surround sound",
                            color = FrostSoulTheme.colors.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "V1 stereo field",
                            color = FrostSoulTheme.colors.onSurfaceMuted,
                            fontSize = 12.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = "Back",
                            tint = FrostSoulTheme.colors.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(FrostSoulTheme.colors.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FrostSoulTheme.colors.surface, FrostSoulTheme.shapes.large)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.equalizer),
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.primary else FrostSoulTheme.colors.onSurfaceMuted,
                        modifier = Modifier.size(32.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (enabled) "Surround is active" else "Surround is bypassed",
                            color = FrostSoulTheme.colors.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "The direct stereo image stays intact while a bounded rear ambience is added.",
                            color = FrostSoulTheme.colors.onSurfaceMuted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FrostSoulTheme.colors.surface, FrostSoulTheme.shapes.large)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Surround intensity",
                            color = FrostSoulTheme.colors.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Controls the V1 virtual-rear contribution",
                            color = FrostSoulTheme.colors.onSurfaceMuted,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        text = "${(intensity * 100f).toInt()}%",
                        color = if (enabled) MaterialTheme.colorScheme.primary else FrostSoulTheme.colors.onSurfaceMuted,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Slider(
                    value = intensity,
                    onValueChange = { intensity = it },
                    valueRange = 0f..1f,
                    enabled = enabled,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Transparent", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp)
                    Text("Wide", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp)
                }
                TextButton(
                    onClick = { intensity = 0.5f },
                    colors = ButtonDefaults.textButtonColors(contentColor = FrostSoulTheme.colors.onSurface),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Reset")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FrostSoulTheme.colors.surface, FrostSoulTheme.shapes.large)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text(
                    text = "Processing details",
                    color = FrostSoulTheme.colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Stereo-only V1 processing", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 13.sp)
                Text("No HRTF, reverb, loudness normalization, or limiter is included.", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 13.sp)
                Text("When bypassed, the PCM buffer is left untouched.", color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
