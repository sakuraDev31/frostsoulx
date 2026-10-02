package dev.vxs.frostsoulx.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = colors.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Stereo Surround is temporarily disabled",
                        color = colors.onSurface,
                        fontSize = 18.sp,
                    )
                    Text(
                        text = "The audio engine is dormant while the standalone DSP engine is being rebuilt and tested independently.",
                        color = colors.onSurfaceMuted,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
