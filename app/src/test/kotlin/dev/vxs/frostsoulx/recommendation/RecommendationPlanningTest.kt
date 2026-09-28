package dev.vxs.frostsoulx.recommendation

import dev.vxs.frostsoulx.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationPlanningTest {
    private val context = RecommendationContext(
        hourOfDay = 18,
        dayOfWeek = 5,
        isHeadphones = false,
        isBluetooth = false,
        isCharging = false,
        isOffline = true,
        currentSongId = "current",
        recentSongIds = listOf("recent"),
        userProfile = UserTasteProfile(negativeSongs = setOf("disliked")),
        autoplayEnabled = true,
        queueState = RecommendationQueueState(explicitSongIds = listOf("manual-1", "manual-2")),
    )

    private fun candidate(id: String, artist: String = id, energy: Float? = 0.4f) = RecommendationCandidate(
        track = MediaMetadata(id, id, listOf(MediaMetadata.Artist(artist, artist)), 180),
        feature = SongFeatureVector(id, artistId = artist, energy = energy, genreDistribution = mapOf("indie" to 1f)),
        source = CandidateSource.Library,
    )

    @Test
    fun `candidate generator removes duplicates recent explicit and negative songs`() {
        val generator = RecommendationCandidateGenerator()
        val result = generator.generate(
            listOf(listOf(candidate("manual-1"), candidate("ok"), candidate("ok"), candidate("recent"), candidate("disliked"))),
            context,
            limit = 20,
        )
        assertEquals(listOf("ok"), result.map { it.track.id })
    }

    @Test
    fun `candidate generator unions sources and preserves provenance`() {
        val generator = RecommendationCandidateGenerator()
        val radio = candidate("radio").copy(source = CandidateSource.Transition)
        val cached = candidate("cached").copy(source = CandidateSource.LastFm)
        val result = generator.generate(listOf(listOf(radio), listOf(cached)), context)
        assertEquals(listOf(CandidateSource.Transition, CandidateSource.LastFm), result.map { it.source })
    }

    @Test
    fun `autoplay is append only and keeps manual ids untouched`() {
        val candidates = listOf(candidate("a"), candidate("b"), candidate("c"))
        val plan = RecommendationSequenceOptimizer().plan(candidates, context, count = 3, seed = 99L)
        assertEquals(3, plan.appendOnlySongIds.size)
        assertTrue(plan.appendOnlySongIds.none { it in context.queueState.explicitSongIds })
    }

    @Test
    fun `queue lock prevents generated additions`() {
        val locked = context.copy(queueState = context.queueState.copy(isLocked = true))
        val plan = RecommendationSequenceOptimizer().plan(listOf(candidate("a")), locked, count = 1, seed = 1L)
        assertTrue(plan.appendOnlySongIds.isEmpty())
    }

    @Test
    fun `seeded planning is deterministic`() {
        val candidates = listOf(candidate("a"), candidate("b"), candidate("c"))
        val optimizer = RecommendationSequenceOptimizer()
        val first = optimizer.plan(candidates, context, count = 3, seed = 44L)
        val second = optimizer.plan(candidates, context, count = 3, seed = 44L)
        assertEquals(first, second)
    }

    @Test
    fun `autoplay coordinator exposes real telemetry plan data`() {
        val coordinator = IntelligenceAutoplayCoordinator()
        val result = coordinator.orderMediaItems(
            items = candidatesForMediaItems("radio-a", "radio-b"),
            context = context,
            seed = 7L,
        )
        val plan = coordinator.lastPlan
        assertEquals(result.map { it.mediaId }, plan.appendOnlySongIds)
        assertTrue(plan.candidateSourceCounts[CandidateSource.Transition.name] ?: 0 > 0)
        assertTrue(plan.rankedCandidates.all { it.probability != null })
        assertEquals(plan.appendOnlySongIds.firstOrNull(), plan.appendOnlySongIds.firstOrNull())
        assertTrue(plan.sequenceOptimizationResult?.startsWith("selected=") == true)
    }

    @Test
    fun `planner follows an energy trajectory instead of unrelated jumps`() {
        val session = SessionState(sessionId = 1L, startedAtMs = 0L, currentEnergy = 0.35f, energyTrend = 0.05f)
        val trajectoryContext = context.copy(sessionState = session)
        val low = candidate("low", energy = 0.4f)
        val abrupt = candidate("abrupt", energy = 0.95f)
        val ranked = ProbabilisticRecommendationRanker().rank(
            listOf(low.feature, abrupt.feature),
            UserTasteProfile(preferredEnergy = 0.35f),
            session,
            null,
            trajectoryContext,
            1L,
        )
        assertEquals("low", ranked.first().first.songId)
    }

    private fun candidatesForMediaItems(vararg ids: String): List<androidx.media3.common.MediaItem> =
        ids.map { id ->
            androidx.media3.common.MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(id)
                        .setArtist(id)
                        .build(),
                ).build()
        }
}
