package dev.vxs.frostsoulx.recommendation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class IntelligenceBehaviorSimulationTest {
    private fun song(
        id: String,
        artist: String,
        energy: Float,
        genre: String = "indie",
        mood: String = "dreamy",
    ) = SongFeatureVector(
        songId = id,
        artistId = artist,
        energy = energy,
        genreDistribution = mapOf(genre to 1f),
        moodDistribution = mapOf(mood to 1f),
        popularity = 0.5f,
    )

    @Test
    fun `repeated positive interaction increases artist affinity and negative interaction decreases it`() {
        val harness = IntelligenceSimulationHarness()
        val positive = song("positive", "artist-a", 0.3f)
        harness.interact(positive, 10, 2, 1_000L, completed = true, liked = true, saved = true)
        val afterPositive = harness.user.artistAffinity.getValue("artist-a")
        harness.interact(positive, 10, 2, 2_000L, skipped = true)
        assertTrue(afterPositive > 0f)
        assertTrue(harness.user.artistAffinity.getValue("artist-a") < afterPositive)
    }

    @Test
    fun `temporal model learns observed hour and weekday rather than assumptions`() {
        val harness = IntelligenceSimulationHarness()
        val morning = song("morning", "a", 0.25f, genre = "ambient", mood = "calm")
        repeat(5) { harness.observeTemporal(8, 2, morning) }
        val matching = harness.temporal.compatibility(8, 2, morning)
        val unseenHour = harness.temporal.compatibility(22, 7, morning)
        assertTrue(matching > unseenHour)
        assertEquals(5L, harness.temporal.sampleCount)
    }

    @Test
    fun `session keeps short term energy trajectory and long term affinity distinct`() {
        val harness = IntelligenceSimulationHarness()
        val low = song("low", "artist-a", 0.2f)
        repeat(8) { harness.interact(low, 21, 6, it * 1_000L, completed = true) }
        assertNotNull(harness.session.currentEnergy)
        assertTrue(harness.user.artistAffinity.getValue("artist-a") > 0f)
        assertTrue(harness.session.recentSongIds.contains("low"))
    }

    @Test
    fun `home ranking evolves after simulated returning-user behavior`() {
        val harness = IntelligenceSimulationHarness()
        val familiar = song("familiar", "artist-a", 0.3f)
        val discovery = song("discovery", "artist-b", 0.7f, genre = "rock", mood = "energetic")
        val before = harness.rank(listOf(familiar, discovery), 12, 3).associate { it.first.songId to it.second.rawScore }
        harness.interact(familiar, 12, 3, 1_000L, completed = true, liked = true)
        val after = harness.rank(listOf(familiar, discovery), 12, 3).associate { it.first.songId to it.second.rawScore }
        assertTrue(after.getValue("familiar") > before.getValue("familiar"))
    }

    @Test
    fun `exposure alone does not mutate simulated preference`() {
        val harness = IntelligenceSimulationHarness()
        val before = harness.user
        harness.rank(listOf(song("shown", "artist-a", 0.4f)), 12, 3)
        assertEquals(before, harness.user)
    }

    @Test
    fun `autoplay keeps energy trajectory bounded, avoids disliked tracks, and exposes exploration`() {
        val harness = IntelligenceSimulationHarness()
        val current = song("current", "artist-a", 0.35f)
        harness.interact(current, 12, 2, 1_000L, completed = true)
        harness.interact(song("bad", "artist-b", 0.4f), 12, 2, 2_000L, skipped = true)
        val candidates = listOf(
            song("dreamy", "artist-c", 0.4f),
            song("psychedelic", "artist-d", 0.52f, genre = "psychedelic", mood = "curious"),
            song("moderate", "artist-e", 0.62f, genre = "alternative", mood = "focused"),
            song("bad", "artist-b", 0.4f),
        )
        val plan = harness.autoplay(candidates, seed = 44L)
        assertFalse(plan.appendOnlySongIds.contains("bad"))
        assertTrue(plan.energyTrajectory.zipWithNext().all { (a, b) -> abs(b - a) <= 0.7f })
        assertTrue(plan.rankedCandidates.isNotEmpty())
        assertTrue(plan.probabilities.all { it in 0f..1f })
    }

    @Test
    fun `different seeds can sample different valid autoplay sequences`() {
        val candidates = listOf(
            song("a", "a", 0.4f), song("b", "b", 0.42f), song("c", "c", 0.44f), song("d", "d", 0.46f),
        )
        val first = IntelligenceSimulationHarness().autoplay(candidates, 1L).appendOnlySongIds
        val second = IntelligenceSimulationHarness().autoplay(candidates, 99L).appendOnlySongIds
        assertTrue(first.isNotEmpty() && second.isNotEmpty())
        assertTrue(first != second || first.size == 1)
    }

    @Test
    fun `cold start remains functional from metadata and diversity`() {
        val harness = IntelligenceSimulationHarness()
        val ranked = harness.rank(
            listOf(song("one", "a", 0.3f), song("two", "b", 0.7f, "rock", "energetic")),
            9,
            1,
        )
        assertEquals(2, ranked.size)
        assertTrue(ranked.map { it.first.artistId }.distinct().size > 1)
    }

    @Test
    fun `invalid probability inputs become a normalized safe distribution`() {
        val probabilities = IntelligenceMath.softmax(listOf(Float.NaN, Float.POSITIVE_INFINITY, 2f), 0.75f)
        assertEquals(1f, probabilities.sum(), 0.0001f)
        assertTrue(probabilities.all { it.isFinite() && it in 0f..1f })
    }

    @Test
    fun `seeded sampling is reproducible and probabilities normalize`() {
        val scores = listOf(0.1f, 0.4f, 0.7f)
        val probabilities = IntelligenceMath.softmax(scores, 0.75f)
        assertEquals(1f, probabilities.sum(), 0.0001f)
        assertEquals(IntelligenceMath.sampleIndex(probabilities, 0.31f), IntelligenceMath.sampleIndex(probabilities, 0.31f))
    }

    @Test
    fun `telemetry contains actual autoplay mode counts factors probabilities selection and sequence`() {
        val harness = IntelligenceSimulationHarness()
        val plan = harness.autoplay(listOf(song("a", "a", 0.3f), song("b", "b", 0.5f)), 8L)
        val snapshot = harness.telemetry.snapshot.value
        assertEquals("autoplay", snapshot.recommendationMode)
        assertEquals(2, snapshot.candidateSourceCounts["simulation"])
        assertEquals(plan.appendOnlySongIds.firstOrNull(), snapshot.selectedNextTrackId)
        assertEquals(plan.rankedCandidates.size, snapshot.rankedCandidates.size)
        assertTrue(snapshot.rankedCandidates.any { it.userAffinity != null && it.probability != null })
        assertNotNull(snapshot.sequenceOptimizationResult)
    }

    @Test
    fun `empty sources and unavailable knowledge are safe no-op fallbacks`() {
        val harness = IntelligenceSimulationHarness()
        val plan = harness.autoplay(emptyList(), 1L)
        assertTrue(plan.appendOnlySongIds.isEmpty())
        assertTrue(plan.rankedCandidates.isEmpty())
        assertEquals("autoplay", harness.telemetry.snapshot.value.recommendationMode)
    }

    @Test
    fun `representative recommendation workload stays bounded and off realtime path`() {
        val harness = IntelligenceSimulationHarness()
        val candidates = List(200) { index -> song("song-$index", "artist-${index % 20}", (index % 100) / 100f) }
        val benchmark = harness.benchmark(candidates, 123L)
        assertTrue(benchmark.candidateGenerationMs >= 0L)
        assertTrue(benchmark.rankingMs >= 0L)
        assertTrue(benchmark.sequenceOptimizationMs >= 0L)
        assertEquals(
            benchmark.candidateGenerationMs + benchmark.rankingMs + benchmark.sequenceOptimizationMs,
            benchmark.totalMs,
        )
        assertTrue(benchmark.totalMs < 5_000L)
        assertTrue(harness.telemetry.snapshot.value.rankedCandidates.size <= 32)
    }
}
