package dev.vxs.frostsoulx.recommendation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligenceTelemetryTest {
    private val context = RecommendationContext.fromFlags(hourOfDay = 12, dayOfWeek = 2, flags = 0)

    @Test
    fun `snapshot propagation is immutable and bounded`() {
        val store = IntelligenceTelemetryStore(maxCandidates = 2, maxEvents = 2)
        val candidates = (1..4).map { id -> IntelligenceCandidateSnapshot(id.toString(), "t$id", "a") }
        val sourceCounts = mutableMapOf("library" to 4)
        store.publish(IntelligenceSnapshot(rankedCandidates = candidates, candidateSourceCounts = sourceCounts))
        sourceCounts["network"] = 9
        val first = store.snapshot.value
        assertEquals(2, first.rankedCandidates.size)
        assertEquals(1, first.candidateSourceCounts.size)
        assertNotSame(candidates, first.rankedCandidates)
    }

    @Test
    fun `event history is bounded and keeps newest events`() {
        val store = IntelligenceTelemetryStore(maxEvents = 2)
        (1..3).forEach { index ->
            store.recordEvent(
                IntelligenceEvent(
                    type = IntelligenceEventType.RecommendationShown,
                    songId = index.toString(),
                    occurredAtMs = index.toLong(),
                    context = context,
                ),
            )
        }
        assertEquals(listOf("2", "3"), store.snapshot.value.recentEvents.map { it.songId })
    }

    @Test
    fun `score breakdown and probability remain explicit`() {
        val candidate = IntelligenceCandidateSnapshot(
            songId = "song",
            title = "Title",
            artist = "Artist",
            probability = 0.75f,
            score = 1.2f,
            lastFm = 0.3f,
            userAffinity = 0.8f,
            genreCompatibility = 0.6f,
            moodCompatibility = 0.5f,
            energyTransition = 0.7f,
            temporalCompatibility = 0.4f,
            sessionCompatibility = 0.9f,
            novelty = 0.2f,
            repetitionPenalty = 0.1f,
        )
        assertEquals(0.75f, candidate.probability)
        assertEquals(0.8f, candidate.userAffinity)
        assertEquals(0.1f, candidate.repetitionPenalty)
    }

    @Test
    fun `unavailable values are null rather than fabricated`() {
        val candidate = IntelligenceCandidateSnapshot("song", "Title", "Artist")
        assertNull(candidate.probability)
        assertNull(candidate.lastFm)
        assertNull(candidate.score)
    }

    @Test
    fun `telemetry publication cannot mutate source collections`() {
        val store = IntelligenceTelemetryStore()
        val energy = mutableListOf(0.2f, 0.4f)
        store.publish(IntelligenceSnapshot(energyTrajectory = energy))
        energy += 0.8f
        assertEquals(listOf(0.2f, 0.4f), store.snapshot.value.energyTrajectory)
        assertTrue(store.snapshot.value.isOffline)
    }
}
