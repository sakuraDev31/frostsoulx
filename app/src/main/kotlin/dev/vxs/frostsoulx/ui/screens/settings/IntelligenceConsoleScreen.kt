package dev.vxs.frostsoulx.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.vxs.frostsoulx.recommendation.IntelligenceCandidateSnapshot
import dev.vxs.frostsoulx.recommendation.IntelligenceSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntelligenceConsoleScreen(
    navController: NavHostController,
    viewModel: IntelligenceConsoleViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Intelligence Console") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        IntelligenceConsoleContent(snapshot, Modifier.padding(padding))
    }
}

@Composable
private fun IntelligenceConsoleContent(snapshot: IntelligenceSnapshot, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ConsoleSection("Engine Status") { KeyValue("Status", snapshot.engineStatus); KeyValue("Runtime", snapshot.mode); KeyValue("Recommendation", snapshot.recommendationMode); KeyValue("Model", snapshot.modelVersion); KeyValue("Processing", snapshot.processingDurationMs?.let { "$it ms" } ?: "Unavailable"); KeyValue("Sequence", snapshot.sequenceOptimizationResult ?: "Unavailable") } }
        item { ConsoleSection("Current Track") { KeyValue("Track ID", snapshot.currentTrackId ?: "Unavailable"); KeyValue("Selected next", snapshot.selectedNextTrackId ?: "Unavailable") } }
        item { ConsoleSection("Session Context") { KeyValue("Session", snapshot.sessionId?.toString() ?: "Unavailable"); Distribution("Mood", snapshot.moodDistribution); Distribution("Genre", snapshot.genreDistribution) } }
        item { ConsoleSection("Time Context") { KeyValue("Hour", snapshot.hourOfDay?.toString() ?: "Unavailable"); KeyValue("Day", snapshot.dayOfWeek?.toString() ?: "Unavailable"); KeyValue("Offline", snapshot.isOffline.toString()) } }
        item { ConsoleSection("Candidate Pipeline") { Distribution("Sources", snapshot.candidateSourceCounts.mapValues { it.value.toFloat() }); Distribution("Filtering", snapshot.filteringCounts.mapValues { it.value.toFloat() }) } }
        item { ConsoleSection("Energy Trajectory") { KeyValue("Values", snapshot.energyTrajectory.takeIf { it.isNotEmpty() }?.joinToString(" → ") { "%.2f".format(it) } ?: "Unavailable") } }
        item { ConsoleSection("Top Next-Track Probabilities") { ProbabilityList(snapshot.rankedCandidates.take(8)) } }
        item { ConsoleSection("Selected Next Track") { KeyValue("Track ID", snapshot.selectedNextTrackId ?: "Unavailable") } }
        item { ConsoleSection("Candidate List") { Text(if (snapshot.rankedCandidates.isEmpty()) "Unavailable" else "${snapshot.rankedCandidates.size} candidates") } }
        items(snapshot.rankedCandidates, key = { it.songId }) { candidate -> CandidateRow(candidate) }
        item { ConsoleSection("Session Metrics") { KeyValue("Ranked", snapshot.rankedCandidates.size.toString()); KeyValue("Events", snapshot.recentEvents.size.toString()) } }
        item { ConsoleSection("Recent Sequence") { KeyValue("Tracks", snapshot.rankedCandidates.take(5).joinToString(" → ") { it.songId }.ifBlank { "Unavailable" }) } }
        item { ConsoleSection("Live Event Log") { if (snapshot.recentEvents.isEmpty()) Text("Unavailable") else snapshot.recentEvents.takeLast(12).forEach { KeyValue(it.type, it.songId ?: "Unavailable") } } }
    }
}

@Composable
private fun CandidateRow(candidate: IntelligenceCandidateSnapshot) {
    var explaining by remember(candidate.songId) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(candidate.title, style = MaterialTheme.typography.titleMedium)
                    Text(candidate.artist.ifBlank { "Unavailable" }, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { explaining = !explaining }) { Text(if (explaining) "Hide" else "Explain") }
            }
            KeyValue("Probability", candidate.probability?.let { "%.3f".format(it) } ?: "Unavailable")
            if (explaining) {
                Spacer(Modifier.height(6.dp))
                KeyValue("Last.fm relationship", candidate.lastFm.metric())
                KeyValue("User affinity", candidate.userAffinity.metric())
                KeyValue("Genre compatibility", candidate.genreCompatibility.metric())
                KeyValue("Mood compatibility", candidate.moodCompatibility.metric())
                KeyValue("Energy transition", candidate.energyTransition.metric())
                KeyValue("Temporal compatibility", candidate.temporalCompatibility.metric())
                KeyValue("Session compatibility", candidate.sessionCompatibility.metric())
                KeyValue("Novelty", candidate.novelty.metric())
                KeyValue("Repetition penalty", candidate.repetitionPenalty.metric())
                KeyValue("Final score", candidate.score.metric())
            }
        }
    }
}

@Composable
private fun ProbabilityList(candidates: List<IntelligenceCandidateSnapshot>) {
    if (candidates.isEmpty()) Text("Unavailable") else candidates.forEach { KeyValue(it.title, it.probability?.let { p -> "%.3f".format(p) } ?: "Unavailable") }
}

@Composable
private fun ConsoleSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); content() } }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.width(8.dp)); Text(value, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun Distribution(label: String, values: Map<String, Float>) {
    KeyValue(label, values.entries.sortedByDescending { it.value }.take(5).joinToString(", ") { "${it.key}: %.2f".format(it.value) }.ifBlank { "Unavailable" })
}

private fun Float?.metric(): String = this?.let { "%.3f".format(it) } ?: "Unavailable"
