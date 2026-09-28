package dev.vxs.frostsoulx.recommendation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeIntelligencePlannerTest {
    @Test
    fun `chips use normalized 30 40 30 weighting`() {
        val result = HomeIntelligencePlanner.scoreChips(
            listOf("Rock", "Jazz"),
            HomeChipSignals(
                longTerm = mapOf("Rock" to 1f, "Jazz" to 0f),
                currentSession = mapOf("Rock" to 0f, "Jazz" to 1f),
                previousSession = mapOf("Rock" to 1f, "Jazz" to 0f),
            ),
            limit = 2,
        )
        assertEquals("Rock", result.first().title)
        assertEquals(0.6f, result.first().finalScore, 0.001f)
        assertEquals(0.4f, result[1].finalScore, 0.001f)
    }

    @Test
    fun `missing previous session renormalizes available weights`() {
        val result = HomeIntelligencePlanner.scoreChips(
            listOf("Rock", "Jazz"),
            HomeChipSignals(
                longTerm = mapOf("Rock" to 1f, "Jazz" to 0f),
                currentSession = mapOf("Rock" to 0f, "Jazz" to 1f),
                previousSession = null,
            ),
            limit = 2,
        )
        assertEquals(0.428f, result.first { it.title == "Rock" }.finalScore, 0.01f)
        assertEquals(0.571f, result.first { it.title == "Jazz" }.finalScore, 0.01f)
        assertTrue(result.all { it.previousSession == null })
    }

    @Test
    fun `chip source values are normalized before combining`() {
        val result = HomeIntelligencePlanner.scoreChips(
            listOf("A", "B"),
            HomeChipSignals(longTerm = mapOf("A" to 100f, "B" to 50f)),
            limit = 2,
        )
        assertEquals(1f, result.first { it.title == "A" }.longTerm!!, 0.001f)
        assertEquals(0f, result.first { it.title == "B" }.longTerm!!, 0.001f)
    }

    @Test
    fun `chip updates are deterministic and duplicate contexts are diversified`() {
        val signals = HomeChipSignals(
            longTerm = mapOf("Late Night Rock" to 1f, "Rock" to 0.9f, "Jazz" to 0.8f),
            currentSession = mapOf("Late Night Rock" to 1f, "Rock" to 0.9f, "Jazz" to 0.8f),
        )
        val first = HomeIntelligencePlanner.scoreChips(listOf("Late Night Rock", "Rock", "Jazz"), signals)
        val second = HomeIntelligencePlanner.scoreChips(listOf("Late Night Rock", "Rock", "Jazz"), signals)
        assertEquals(first, second)
        assertTrue(first.map { it.title }.contains("Jazz"))
    }

    @Test
    fun `section planner suppresses low relevance and repeated sources`() {
        val plan = HomeIntelligencePlanner.planSections(
            listOf(
                HomeSectionCandidate("a", "Because You Like", "remote", 10, longTermAffinity = 1f),
                HomeSectionCandidate("b", "Similar Artists", "remote", 10, currentSessionCompatibility = 0.9f),
                HomeSectionCandidate("c", "New Releases", "remote", 10, novelty = 0.9f),
                HomeSectionCandidate("d", "Old Shelf", "remote", 10, longTermAffinity = 0f, novelty = 0f, discoveryValue = 0f),
            ),
            maxSections = 4,
        )
        assertTrue(plan.selected.size <= 2)
        assertTrue(plan.suppressed.any { it.id == "d" })
    }

    @Test
    fun `cold start keeps an available section`() {
        val plan = HomeIntelligencePlanner.planSections(
            listOf(HomeSectionCandidate("fallback", "Popular", "global", 8)),
            maxSections = 4,
        )
        assertEquals(listOf("fallback"), plan.selected.map { it.id })
    }

    @Test
    fun `distinct remote section identities remain available`() {
        val plan = HomeIntelligencePlanner.planSections(
            (0 until 4).map { index ->
                HomeSectionCandidate(
                    id = "browse-$index",
                    title = "Shelf $index",
                    source = "browse-$index",
                    itemCount = 6,
                )
            },
            maxSections = 4,
        )
        assertEquals(4, plan.selected.size)
    }
}
