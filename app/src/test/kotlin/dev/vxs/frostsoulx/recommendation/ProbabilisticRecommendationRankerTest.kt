package dev.vxs.frostsoulx.recommendation

import org.junit.Assert.assertTrue
import org.junit.Test

class ProbabilisticRecommendationRankerTest {
    private val context = RecommendationContext(22, 1, false, false, false, true)
    private val user = UserTasteProfile(
        longTermGenres = mapOf("indie" to 1f),
        longTermMoods = mapOf("dreamy" to 1f),
        preferredEnergy = 0.35f,
    )

    @Test
    fun `compatible candidate outranks abrupt unrelated candidate`() {
        val ranker = ProbabilisticRecommendationRanker()
        val compatible = SongFeatureVector("a", genreDistribution = mapOf("indie" to 1f), moodDistribution = mapOf("dreamy" to 1f), energy = 0.38f)
        val abrupt = SongFeatureVector("b", genreDistribution = mapOf("metal" to 1f), moodDistribution = mapOf("energetic" to 1f), energy = 0.92f)
        val ranked = ranker.rank(listOf(abrupt, compatible), user, null, null, context, 1L)
        assertTrue(ranked.first().first.songId == "a")
    }

    @Test
    fun `negative song gets a strong repetition penalty`() {
        val ranker = ProbabilisticRecommendationRanker()
        val candidate = SongFeatureVector("blocked", genreDistribution = mapOf("indie" to 1f))
        val score = ranker.score(candidate, user.copy(negativeSongs = setOf("blocked")), null, null, context, 1L)
        assertTrue(score.repetitionPenalty >= 1f)
    }

    @Test
    fun `rank probabilities normalize`() {
        val ranker = ProbabilisticRecommendationRanker()
        val candidates = listOf(
            SongFeatureVector("a", genreDistribution = mapOf("indie" to 1f)),
            SongFeatureVector("b", genreDistribution = mapOf("pop" to 1f)),
        )
        val ranked = ranker.rank(candidates, user, null, null, context, 1L)
        assertTrue(ranked.sumOf { it.second.probability.toDouble() } in 0.999..1.001)
    }

    @Test
    fun `planner probability helper is normalized`() {
        val probabilities = ProbabilisticRecommendationRanker().probabilities(listOf(0.1f, 0.4f, 0.8f))
        assertTrue(probabilities.sumOf { it.toDouble() } in 0.999..1.001)
    }
}
