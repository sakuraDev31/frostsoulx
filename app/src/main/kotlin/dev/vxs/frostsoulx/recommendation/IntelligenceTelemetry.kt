package dev.vxs.frostsoulx.recommendation

import dev.vxs.frostsoulx.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Immutable, bounded debug snapshot; no mutable engine/database objects cross the UI boundary. */
data class IntelligenceSnapshot(
    val engineStatus: String = "unavailable",
    val mode: String = "local",
    val recommendationMode: String = "unavailable",
    val modelVersion: String = IntelligenceModelVersion.toString(),
    val currentTrackId: String? = null,
    val sessionId: Long? = null,
    val hourOfDay: Int? = null,
    val dayOfWeek: Int? = null,
    val moodDistribution: Map<String, Float> = emptyMap(),
    val genreDistribution: Map<String, Float> = emptyMap(),
    val candidateSourceCounts: Map<String, Int> = emptyMap(),
    val filteringCounts: Map<String, Int> = emptyMap(),
    val rankedCandidates: List<IntelligenceCandidateSnapshot> = emptyList(),
    val selectedNextTrackId: String? = null,
    val energyTrajectory: List<Float> = emptyList(),
    val processingDurationMs: Long? = null,
    val sequenceOptimizationResult: String? = null,
    val homeChipScores: List<IntelligenceChipSnapshot> = emptyList(),
    val selectedHomeSections: List<String> = emptyList(),
    val suppressedHomeSections: List<String> = emptyList(),
    val homeContentScores: Map<String, Float> = emptyMap(),
    val recentEvents: List<IntelligenceEventSnapshot> = emptyList(),
    val isOffline: Boolean = true,
)

data class IntelligenceCandidateSnapshot(
    val songId: String,
    val title: String,
    val artist: String,
    val probability: Float? = null,
    val score: Float? = null,
    val lastFm: Float? = null,
    val userAffinity: Float? = null,
    val genreCompatibility: Float? = null,
    val moodCompatibility: Float? = null,
    val energyTransition: Float? = null,
    val temporalCompatibility: Float? = null,
    val sessionCompatibility: Float? = null,
    val novelty: Float? = null,
    val repetitionPenalty: Float? = null,
)

data class IntelligenceEventSnapshot(
    val type: String,
    val songId: String?,
    val occurredAtMs: Long,
)

data class IntelligenceChipSnapshot(
    val title: String,
    val longTerm: Float? = null,
    val currentSession: Float? = null,
    val previousSession: Float? = null,
    val finalScore: Float? = null,
)

class IntelligenceTelemetryStore(
    private val maxCandidates: Int = 32,
    private val maxEvents: Int = 64,
) {
    private val _snapshot = MutableStateFlow(IntelligenceSnapshot())
    val snapshot: StateFlow<IntelligenceSnapshot> = _snapshot.asStateFlow()

    fun publish(snapshot: IntelligenceSnapshot) {
        if (!BuildConfig.DEBUG) return
        _snapshot.value = snapshot.copy(
            candidateSourceCounts = snapshot.candidateSourceCounts.toMap(),
            filteringCounts = snapshot.filteringCounts.toMap(),
            homeChipScores = snapshot.homeChipScores.take(5).toList(),
            selectedHomeSections = snapshot.selectedHomeSections.take(12).toList(),
            suppressedHomeSections = snapshot.suppressedHomeSections.take(32).toList(),
            homeContentScores = snapshot.homeContentScores.entries.take(32).associate { it.key to it.value },
            rankedCandidates = snapshot.rankedCandidates.take(maxCandidates).toList(),
            recentEvents = snapshot.recentEvents.takeLast(maxEvents).toList(),
            energyTrajectory = snapshot.energyTrajectory.takeLast(64).toList(),
        )
    }

    fun recordEvent(event: IntelligenceEvent) {
        if (!BuildConfig.DEBUG) return
        val current = _snapshot.value
        publish(current.copy(recentEvents = current.recentEvents + IntelligenceEventSnapshot(event.type.name, event.songId, event.occurredAtMs)))
    }
}
