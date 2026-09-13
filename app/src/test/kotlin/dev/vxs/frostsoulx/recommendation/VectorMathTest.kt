/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.recommendation

import dev.vxs.frostsoulx.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorMathTest {
    @Test
    fun `quantized vector preserves self similarity`() {
        val vector = QuantizedVectorCodec.quantize(floatArrayOf(0.25f, -0.75f, 0.5f, 0.1f))

        assertEquals(1f, QuantizedVectorCodec.cosine(vector, vector), 0.015f)
    }

    @Test
    fun `orthogonal vectors have near zero cosine similarity`() {
        val first = QuantizedVectorCodec.quantize(floatArrayOf(1f, 0f, 0f, 0f))
        val second = QuantizedVectorCodec.quantize(floatArrayOf(0f, 1f, 0f, 0f))

        assertEquals(0f, QuantizedVectorCodec.cosine(first, second), 0.0001f)
    }

    @Test
    fun `metadata encoding is deterministic`() {
        val track =
            MediaMetadata(
                id = "song-1",
                title = "Night Drive",
                artists = listOf(MediaMetadata.Artist(id = "artist-1", name = "Aurora Lane")),
                duration = 240,
                thumbnailUrl = null,
            )
        val encoder = MetadataFeatureEncoder(dimension = 64)

        assertTrue(encoder.encode(track).contentEquals(encoder.encode(track)))
    }

    @Test
    fun `weighted average favors higher confidence vector`() {
        val positive = QuantizedVectorCodec.quantize(floatArrayOf(1f, 0f, 0f, 0f))
        val secondary = QuantizedVectorCodec.quantize(floatArrayOf(0f, 1f, 0f, 0f))
        val blended = QuantizedVectorCodec.weightedAverage(listOf(positive to 3f, secondary to 1f), dimension = 4)

        assertTrue(QuantizedVectorCodec.cosine(blended, positive) > QuantizedVectorCodec.cosine(blended, secondary))
    }

    @Test
    fun `bounded index evicts oldest entries and returns closest vector`() {
        val index = BoundedCosineVectorIndex(maximumItems = 2)
        index.upsert("old", QuantizedVectorCodec.quantize(floatArrayOf(1f, 0f)))
        index.upsert("near", QuantizedVectorCodec.quantize(floatArrayOf(0.9f, 0.1f)))
        index.upsert("far", QuantizedVectorCodec.quantize(floatArrayOf(0f, 1f)))

        assertEquals(listOf("near"), index.nearest(QuantizedVectorCodec.quantize(floatArrayOf(1f, 0f)), limit = 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `resource budget rejects oversized realtime candidate pools`() {
        RecommendationBudget(candidateLimit = 5_000)
    }

    @Test
    fun `weighted bounded probability matches integer counts at whole numbers`() {
        assertEquals(
            RecommendationScoreMath.boundedProbability(3, 1),
            RecommendationScoreMath.boundedProbabilityWeighted(3f, 1f),
            0.0001f,
        )
    }

    @Test
    fun `weighted bounded probability rewards a partially completed listen`() {
        val earlySkip = RecommendationScoreMath.boundedProbabilityWeighted(0f, 1.1f)
        val lateSkip = RecommendationScoreMath.boundedProbabilityWeighted(0.6f, 0f)

        assertTrue(lateSkip > earlySkip)
    }
}
