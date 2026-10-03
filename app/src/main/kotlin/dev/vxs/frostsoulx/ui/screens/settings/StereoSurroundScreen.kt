package dev.vxs.frostsoulx.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulTheme

@Composable
fun StereoSurroundScreen(navController: NavController) {
    val colors = FrostSoulTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Stereo Surround",
                        color = colors.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Text(
                            text = "‹",
                            color = colors.onSurface,
                            fontSize = 30.sp,
                        )
                    }
                },
            )
        },
        containerColor = colors.background,
    ) { padding ->
        var enabled by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Stereo Surround",
                        color = colors.onSurface,
                        fontSize = 20.sp,
                    )
                    Text(
                        text = if (enabled) "Unified audio engine active" else "Unified audio engine ready",
                        color = colors.onSurfaceMuted,
                        fontSize = 13.sp,
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { value ->
                            enabled = value
                            dev.vxs.frostsoulx.playback.ImmersiveAudioRuntime.setEnabled(value)
                        },
                    )
                }
            }
        }
    }
}
