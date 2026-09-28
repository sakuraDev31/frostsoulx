package dev.vxs.frostsoulx.recommendation

import dev.vxs.frostsoulx.models.MediaMetadata
import kotlin.system.measureTimeMillis

/** Pure test harness: it never touches Android, Room, network, player, or DSP APIs. */
data class SimulationBenchmark(
    val candidateGenerationMs: Long,
    val rankingMs: Long,
    val sequenceOptimizationMs: Long,
    val totalMs: Long,
)

class IntelligenceSimulationHarness(
    private val ranker: ProbabilisticRecommendationRanker = ProbabilisticRecommendationRanker(),
    private val optimizer: RecommendationSequenceOptimizer = RecommendationSequenceOptimizer(ranker),
    val telemetry: IntelligenceTelemetryStore = IntelligenceTelemetryStore(),
) {
    var session: SessionState = SessionState(sessionId = 1L, startedAtMs = 0L)
        private set
    var temporal: TemporalModel = TemporalModel()
        private set
    var user: UserTasteProfile = UserTasteProfile()
        private set
    val events = mutableListOf<IntelligenceEventType>()

    fun benchmark(candidates: List<SongFeatureVector>, seed: Long): SimulationBenchmark {
        var generated = emptyList<SongFeatureVector>()
        val generationMs = measureTimeMillis {
            generated = candidates.filterNot { it.songId in user.negativeSongs }
        }
        val rankingMs = measureTimeMillis { rank(generated, 12, 2) }
        val sequenceMs = measureTimeMillis { autoplay(generated, seed) }
        return SimulationBenchmark(generationMs, rankingMs, sequenceMs, generationMs + rankingMs + sequenceMs)
    }

    fun observeTemporal(hour: Int, dayOfWeek: Int, feature: SongFeatureVector) {
        temporal = temporal.observe(hour, dayOfWeek, feature)
    }

    fun interact(
        feature: SongFeatureVector,
        hour: Int,
        dayOfWeek: Int,
        nowMs: Long,
        completed: Boolean = false,
        skipped: Boolean = false,
        liked: Boolean = false,
        saved: Boolean = false,
        replayed: Boolean = false,
    ) {
        events += IntelligenceEventType.PlayStarted
        if (completed) events += IntelligenceEventType.PlayCompleted
        if (skipped) events += IntelligenceEventType.SongSkipped
        if (liked) events += IntelligenceEventType.SongLiked
        if (saved) events += IntelligenceEventType.SongSaved
        if (replayed) events += IntelligenceEventType.SongReplayed
        session = session.updateWith(feature, nowMs, completed, skipped, replayed)
        if (!skipped || completed) temporal = temporal.observe(hour, dayOfWeek, feature)
        val artist = feature.artistId
        val nextAffinity = if (artist == null) user.artistAffinity else {
            val old = user.artistAffinity[artist] ?: 0f
            val delta = when {
                liked || saved || completed -> 0.15f
                skipped -> -0.12f
                else -> 0.03f
            }
            user.artistAffinity + (artist to (old + delta).coerceIn(0f, 1f))
        }
        user = user.copy(
            artistAffinity = nextAffinity,
            songAffinity = user.songAffinity + (feature.songId to ((user.songAffinity[feature.songId] ?: 0f) + if (liked) 0.2f else 0.02f).coerceIn(0f, 1f)),
            negativeSongs = if (skipped && !completed) user.negativeSongs + feature.songId else user.negativeSongs,
            preferredEnergy = session.currentEnergy,
        )
    }

    fun rank(features: List<SongFeatureVector>, hour: Int, dayOfWeek: Int, offline: Boolean = true): List<Pair<SongFeatureVector, RecommendationFeatureScore>> {
        val context = RecommendationContext(
            hourOfDay = hour,
            dayOfWeek = dayOfWeek,
            isHeadphones = false,
            isBluetooth = false,
            isCharging = false,
            isOffline = offline,
            currentSongId = session.recentSongIds.lastOrNull(),
            recentSongIds = session.recentSongIds,
            sessionState = session,
            userProfile = user,
            temporalModel = temporal,
            explorationLevel = user.explorationPreference,
        )
        return ranker.rank(features, user, session, temporal, context, nowMs = session.lastEventAtMs)
    }

    fun autoplay(candidates: List<SongFeatureVector>, seed: Long): AutoplayPlan {
        val context = RecommendationContext(
            hourOfDay = 12,
            dayOfWeek = 2,
            isHeadphones = false,
            isBluetooth = false,
            isCharging = false,
            isOffline = true,
            currentSongId = session.recentSongIds.lastOrNull(),
            recentSongIds = session.recentSongIds,
            sessionState = session,
            userProfile = user,
            temporalModel = temporal,
            autoplayEnabled = true,
        )
        val recommendationCandidates = candidates.filterNot { it.songId in user.negativeSongs }.map { feature ->
            RecommendationCandidate(
                track = MediaMetadata(
                    id = feature.songId,
                    title = feature.songId,
                    artists = listOf(MediaMetadata.Artist(feature.artistId ?: "unknown", feature.artistId ?: "Unknown")),
                    duration = 180,
                ),
                feature = feature,
                source = CandidateSource.Discovery,
            )
        }
        val plan = optimizer.plan(recommendationCandidates, context, candidates.size, seed)
        telemetry.publish(
            IntelligenceSnapshot(
                engineStatus = "ready",
                mode = "offline",
                recommendationMode = "autoplay",
                currentTrackId = context.currentSongId,
                sessionId = session.sessionId,
                hourOfDay = context.hourOfDay,
                dayOfWeek = context.dayOfWeek,
                candidateSourceCounts = mapOf("simulation" to candidates.size),
                filteringCounts = mapOf("filtered" to 0),
                rankedCandidates = plan.rankedCandidates,
                selectedNextTrackId = plan.appendOnlySongIds.firstOrNull(),
                energyTrajectory = plan.energyTrajectory,
                processingDurationMs = 0L,
                sequenceOptimizationResult = plan.sequenceOptimizationResult,
                isOffline = true,
            ),
        )
        return plan
    }
}
