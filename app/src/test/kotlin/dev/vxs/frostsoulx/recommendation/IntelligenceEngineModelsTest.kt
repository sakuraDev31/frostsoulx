package dev.vxs.frostsoulx.recommendation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelligenceEngineModelsTest {
    @Test
    fun `half life decay is one half at one half life`() {
        assertEquals(0.5f, IntelligenceMath.decay(1_000L, 1_000L), 0.0001f)
        assertEquals(1f, IntelligenceMath.decay(0L, 1_000L), 0.0001f)
    }

    @Test
    fun `energy compatibility follows session direction`() {
        val score = IntelligenceMath.energyCompatibility(0.42f, 0.35f, 0.08f, 0.22f)
        val abrupt = IntelligenceMath.energyCompatibility(0.92f, 0.35f, 0.08f, 0.22f)
        assertTrue(score > abrupt)
    }

    @Test
    fun `session update learns a declining energy trajectory`() {
        val initial = SessionState(sessionId = 1L, startedAtMs = 0L)
        val first = initial.updateWith(SongFeatureVector("a", energy = 0.82f), 1L)
        val second = first.updateWith(SongFeatureVector("b", energy = 0.44f), 2L)
        assertTrue(second.energyTrend < 0f)
        assertEquals(0.725f, second.currentEnergy!!, 0.0001f)
    }

    @Test
    fun `temporal model distinguishes learned day buckets`() {
        val calm = SongFeatureVector("calm", genreDistribution = mapOf("ambient" to 1f), energy = 0.2f)
        val loud = SongFeatureVector("loud", genreDistribution = mapOf("metal" to 1f), energy = 0.9f)
        val model = TemporalModel().observe(22, 1, calm, alpha = 1f).observe(22, 5, loud, alpha = 1f)
        assertTrue(model.compatibility(22, 1, calm) > model.compatibility(22, 1, loud))
        assertTrue(model.compatibility(22, 5, loud) > model.compatibility(22, 5, calm))
    }

    @Test
    fun `softmax is normalized and favors higher score`() {
        val probabilities = IntelligenceMath.softmax(listOf(1f, 2f, 3f), temperature = 0.7f)
        assertEquals(1f, probabilities.sum(), 0.0001f)
        assertTrue(probabilities[2] > probabilities[1])
        assertTrue(probabilities[1] > probabilities[0])
    }

    @Test
    fun `seeded sampling is deterministic`() {
        val probabilities = IntelligenceMath.softmax(listOf(0f, 1f, 2f), temperature = 1f)
        assertEquals(IntelligenceMath.sampleIndex(probabilities, 0.8f), IntelligenceMath.sampleIndex(probabilities, 0.8f))
    }
}
