/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.recommendation

import dev.vxs.frostsoulx.models.MediaMetadata
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object QuantizedVectorCodec {
    fun quantize(values: FloatArray): QuantizedVector {
        require(values.isNotEmpty())
        val maxMagnitude = values.maxOf { abs(it) }
        if (!maxMagnitude.isFinite() || maxMagnitude <= 0.000001f) {
            return QuantizedVector(
                dimension = values.size,
                values = ByteArray(values.size),
                scale = 0f,
                norm = 0f,
            )
        }
        val scale = maxMagnitude / 127f
        val encoded = ByteArray(values.size)
        var normSquared = 0f
        values.forEachIndexed { index, value ->
            val clean = value.takeIf(Float::isFinite) ?: 0f
            encoded[index] = (clean / scale).toInt().coerceIn(-127, 127).toByte()
            normSquared += clean * clean
        }
        return QuantizedVector(
            dimension = values.size,
            values = encoded,
            scale = scale,
            norm = sqrt(normSquared),
        )
    }

    fun cosine(
        first: QuantizedVector,
        second: QuantizedVector,
    ): Float {
        if (first.dimension != second.dimension || first.norm <= 0f || second.norm <= 0f) return 0f
        var dot = 0f
        for (index in first.values.indices) {
            dot += first.values[index].toInt() * first.scale * second.values[index].toInt() * second.scale
        }
        return (dot / (first.norm * second.norm)).coerceIn(-1f, 1f)
    }

    fun weightedAverage(
        vectors: List<Pair<QuantizedVector, Float>>,
        dimension: Int,
    ): QuantizedVector {
        val accumulator = FloatArray(dimension)
        var totalWeight = 0f
        vectors.forEach { (vector, weight) ->
            if (vector.dimension != dimension || weight <= 0f || !weight.isFinite()) return@forEach
            totalWeight += weight
            vector.values.forEachIndexed { index, value ->
                accumulator[index] += value.toInt() * vector.scale * weight
            }
        }
        if (totalWeight > 0f) {
            accumulator.indices.forEach { accumulator[it] /= totalWeight }
        }
        return quantize(accumulator)
    }
}

/**
 * Produces a stable local feature representation from metadata. It intentionally avoids decoding
 * audio during normal runtime; optional expensive audio features can be blended later by version.
 */
class MetadataFeatureEncoder(
    private val dimension: Int,
) {
    fun encode(track: MediaMetadata): FloatArray {
        val result = FloatArray(dimension)
        addTokenFeatures(result, track.title, 1.0f)
        track.artists.forEach { artist -> addTokenFeatures(result, artist.name, 1.55f) }
        track.album?.title?.let { addTokenFeatures(result, it, 0.7f) }
        addContinuousFeature(result, 0, track.duration.coerceAtLeast(0) / 900f)
        addContinuousFeature(result, 1, if (track.explicit) 1f else -0.25f)
        addContinuousFeature(result, 2, if (track.liked) 1f else 0f)
        addContinuousFeature(result, 3, if (track.isMusicVideo) -0.5f else 0.25f)
        return normalize(result)
    }

    private fun addTokenFeatures(
        target: FloatArray,
        raw: String,
        weight: Float,
    ) {
        raw
            .lowercase()
            .split(TokenSeparator)
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .take(MaxTokensPerField)
            .forEach { token ->
                val primary = bucket(token.hashCode())
                val secondary = bucket(token.reversed().hashCode())
                target[primary] += weight
                target[secondary] -= weight * 0.45f
            }
    }

    private fun addContinuousFeature(
        target: FloatArray,
        preferredIndex: Int,
        value: Float,
    ) {
        target[preferredIndex.coerceIn(0, target.lastIndex)] += value.coerceIn(-2f, 2f)
    }

    private fun bucket(hash: Int): Int = (hash and Int.MAX_VALUE) % dimension

    private fun normalize(values: FloatArray): FloatArray {
        val norm = sqrt(values.sumOf { (it * it).toDouble() }.toFloat())
        if (norm <= 0.000001f) return values
        return FloatArray(values.size) { index -> values[index] / norm }
    }

    private companion object {
        val TokenSeparator = Regex("[^\\p{L}\\p{N}]+")
        const val MaxTokensPerField = 24
    }
}

object RecommendationScoreMath {
    fun boundedProbability(
        positives: Int,
        negatives: Int,
    ): Float =
        ((positives + 1f) / (positives + negatives + 2f)).coerceIn(0f, 1f)

    /**
     * Same Laplace-smoothed estimate as [boundedProbability], but over continuous weights instead
     * of integer counts — lets callers feed in graded signals (e.g. a skip weighted by how much of
     * the track had already played) instead of collapsing everything to a positive/negative count.
     */
    fun boundedProbabilityWeighted(
        positiveWeight: Float,
        negativeWeight: Float,
    ): Float {
        val safePositive = positiveWeight.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
        val safeNegative = negativeWeight.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
        return ((safePositive + 1f) / (safePositive + safeNegative + 2f)).coerceIn(0f, 1f)
    }

    fun freshnessScore(
        ageDays: Float,
        horizonDays: Float = 45f,
    ): Float = (1f - ageDays.coerceAtLeast(0f) / max(horizonDays, 1f)).coerceIn(0f, 1f)
}

/**
 * A bounded candidate-stage cosine index. Persistent feature vectors remain in Room; this index is
 * rebuilt only for the capped retrieval pool during a background refresh, so it cannot grow into
 * an unbounded process-memory cache.
 */
class BoundedCosineVectorIndex(
    private val maximumItems: Int,
) {
    private val vectors = LinkedHashMap<String, QuantizedVector>()

    fun upsert(
        id: String,
        vector: QuantizedVector,
    ) {
        if (id.isBlank() || vector.norm <= 0f) return
        if (id !in vectors && vectors.size >= maximumItems) {
            val eldest = vectors.entries.iterator().next()
            vectors.remove(eldest.key)
        }
        vectors[id] = vector
    }

    fun nearest(
        query: QuantizedVector,
        limit: Int,
    ): List<String> =
        vectors
            .asSequence()
            .map { (id, vector) -> id to QuantizedVectorCodec.cosine(query, vector) }
            .sortedByDescending { it.second }
            .take(limit.coerceAtLeast(0))
            .map { it.first }
            .toList()

    fun clear() = vectors.clear()
}
