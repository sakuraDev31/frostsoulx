/*
 * FrostSoulX Intelligence Engine models.
 * Local-first, deterministic, and independent from playback/DSP code.
 */
package dev.vxs.frostsoulx.recommendation

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

const val IntelligenceModelVersion: Int = 1

/** A sparse, normalized feature vector. Missing metadata is represented by an empty map/null. */
data class SongFeatureVector(
    val songId: String,
    val genreDistribution: Map<String, Float> = emptyMap(),
    val subgenreDistribution: Map<String, Float> = emptyMap(),
    val moodDistribution: Map<String, Float> = emptyMap(),
    val energy: Float? = null,
    val tempoBpm: Float? = null,
    val acousticness: Float? = null,
    val instrumentalness: Float? = null,
    val artistId: String? = null,
    val albumId: String? = null,
    val popularity: Float? = null,
    val lastFmSimilarity: Map<String, Float> = emptyMap(),
    val tags: Set<String> = emptySet(),
    val interaction: InteractionStats = InteractionStats(),
) {
    init {
        require(songId.isNotBlank())
        require(genreDistribution.values.all(::isUnitFloat))
        require(subgenreDistribution.values.all(::isUnitFloat))
        require(moodDistribution.values.all(::isUnitFloat))
        require(lastFmSimilarity.values.all(::isUnitFloat))
        listOf(energy, acousticness, instrumentalness, popularity).filterNotNull().forEach {
            require(isUnitFloat(it))
        }
        tempoBpm?.let { require(it.isFinite() && it >= 0f) }
    }
}

data class InteractionStats(
    val plays: Int = 0,
    val completed: Int = 0,
    val skips: Int = 0,
    val replays: Int = 0,
    val likes: Int = 0,
    val saves: Int = 0,
    val lastPlayedAtMs: Long? = null,
    val firstPlayedAtMs: Long? = null,
    val shownCount: Int = 0,
) {
    init {
        require(listOf(plays, completed, skips, replays, likes, saves, shownCount).all { it >= 0 })
    }
}

data class UserTasteProfile(
    val modelVersion: Int = IntelligenceModelVersion,
    val generatedAtMs: Long = 0L,
    val longTermGenres: Map<String, Float> = emptyMap(),
    val shortTermGenres: Map<String, Float> = emptyMap(),
    val longTermMoods: Map<String, Float> = emptyMap(),
    val shortTermMoods: Map<String, Float> = emptyMap(),
    val artistAffinity: Map<String, Float> = emptyMap(),
    val songAffinity: Map<String, Float> = emptyMap(),
    val negativeSongs: Set<String> = emptySet(),
    val preferredEnergy: Float? = null,
    val preferredTempoBpm: Float? = null,
    val replayTendency: Float = 0f,
    val completionTendency: Float = 0f,
    val skipTendency: Float = 0f,
    val explorationPreference: Float = 0.5f,
    val familiarityPreference: Float = 0.5f,
) {
    init {
        require(modelVersion > 0)
        require(listOf(replayTendency, completionTendency, skipTendency, explorationPreference, familiarityPreference).all(::isUnitFloat))
        preferredEnergy?.let { require(isUnitFloat(it)) }
        preferredTempoBpm?.let { require(it.isFinite() && it >= 0f) }
    }
}

data class SessionState(
    val sessionId: Long,
    val startedAtMs: Long,
    val lastEventAtMs: Long = startedAtMs,
    val genreDistribution: Map<String, Float> = emptyMap(),
    val moodDistribution: Map<String, Float> = emptyMap(),
    val currentEnergy: Float? = null,
    val energyTrend: Float = 0f,
    val currentTempoBpm: Float? = null,
    val recentSongIds: List<String> = emptyList(),
    val recentArtistIds: List<String> = emptyList(),
    val durationMs: Long = 0L,
    val completionRate: Float = 0f,
    val skipRate: Float = 0f,
    val replayCount: Int = 0,
) {
    init {
        require(sessionId > 0L)
        require(durationMs >= 0L)
        require(isUnitFloat(completionRate) && isUnitFloat(skipRate))
    }

    fun updateWith(
        song: SongFeatureVector,
        nowMs: Long,
        completed: Boolean? = null,
        skipped: Boolean = false,
        replayed: Boolean = false,
        smoothing: Float = 0.25f,
        maxRecentSongs: Int = 20,
    ): SessionState {
        val beta = smoothing.coerceIn(0.001f, 1f)
        val energy = song.energy?.let { next ->
            currentEnergy?.let { old -> old + beta * (next - old) } ?: next
        } ?: currentEnergy
        val trend = if (song.energy != null && currentEnergy != null) {
            (1f - beta) * energyTrend + beta * (song.energy - currentEnergy)
        } else energyTrend
        val totalOutcomes = (completionRate + skipRate).let { if (it > 0f) 1f else 0f }
        val nextCompleted = completed?.let { if (it) 1f else 0f }
        val nextSkipped = if (skipped) 1f else 0f
        return copy(
            lastEventAtMs = nowMs,
            genreDistribution = smoothMap(genreDistribution, song.genreDistribution, beta),
            moodDistribution = smoothMap(moodDistribution, song.moodDistribution, beta),
            currentEnergy = energy,
            energyTrend = trend,
            currentTempoBpm = song.tempoBpm ?: currentTempoBpm,
            recentSongIds = (listOf(song.songId) + recentSongIds).distinct().take(maxRecentSongs),
            recentArtistIds = (listOfNotNull(song.artistId) + recentArtistIds).distinct().take(maxRecentSongs),
            durationMs = (nowMs - startedAtMs).coerceAtLeast(durationMs),
            completionRate = if (nextCompleted == null) completionRate else smoothScalar(completionRate, nextCompleted, beta),
            skipRate = if (totalOutcomes == 0f && !skipped) skipRate else smoothScalar(skipRate, nextSkipped, beta),
            replayCount = replayCount + if (replayed) 1 else 0,
        )
    }
}

data class TemporalModel(
    val modelVersion: Int = IntelligenceModelVersion,
    val sampleCount: Long = 0L,
    val byHourAndDay: Map<Int, Map<Int, TemporalBucket>> = emptyMap(),
) {
    fun observe(hour: Int, dayOfWeek: Int, features: SongFeatureVector, alpha: Float = 0.2f): TemporalModel {
        val h = hour.coerceIn(0, 23)
        val d = dayOfWeek.coerceIn(1, 7)
        val old = byHourAndDay[h]?.get(d) ?: TemporalBucket()
        val next = old.observe(features, alpha)
        return copy(sampleCount = sampleCount + 1, byHourAndDay = byHourAndDay + (h to ((byHourAndDay[h] ?: emptyMap()) + (d to next))))
    }

    fun compatibility(hour: Int, dayOfWeek: Int, features: SongFeatureVector): Float {
        val bucket = byHourAndDay[hour.coerceIn(0, 23)]?.get(dayOfWeek.coerceIn(1, 7)) ?: return 0.5f
        return bucket.compatibility(features)
    }
}

data class TemporalBucket(
    val observations: Long = 0L,
    val genreDistribution: Map<String, Float> = emptyMap(),
    val moodDistribution: Map<String, Float> = emptyMap(),
    val energy: Float? = null,
    val tempoBpm: Float? = null,
) {
    fun observe(song: SongFeatureVector, alpha: Float): TemporalBucket {
        val a = alpha.coerceIn(0.001f, 1f)
        return copy(
            observations = observations + 1,
            genreDistribution = smoothMap(genreDistribution, song.genreDistribution, a),
            moodDistribution = smoothMap(moodDistribution, song.moodDistribution, a),
            energy = song.energy?.let { energy?.let { old -> old + a * (it - old) } ?: it } ?: energy,
            tempoBpm = song.tempoBpm?.let { tempoBpm?.let { old -> old + a * (it - old) } ?: it } ?: tempoBpm,
        )
    }

    fun compatibility(song: SongFeatureVector): Float {
        val genre = cosine(genreDistribution, song.genreDistribution)
        val mood = cosine(moodDistribution, song.moodDistribution)
        val energyMatch = if (energy != null && song.energy != null) 1f - (energy - song.energy).coerceIn(0f, 1f) else 0.5f
        return ((genre + mood + energyMatch) / 3f).coerceIn(0f, 1f)
    }
}

data class SessionFingerprint(
    val sessionId: Long,
    val features: Map<String, Float>,
    val durationMs: Long,
    val skipRate: Float,
    val completionRate: Float,
    val novelty: Float,
)

data class RecommendationModelConfig(
    val modelVersion: Int = IntelligenceModelVersion,
    val weightLastFm: Float = 0.8f,
    val weightUser: Float = 1.4f,
    val weightGenre: Float = 0.9f,
    val weightMood: Float = 0.9f,
    val weightEnergy: Float = 0.8f,
    val weightTemporal: Float = 0.7f,
    val weightSession: Float = 1.0f,
    val weightNovelty: Float = 0.5f,
    val weightPenalty: Float = 1.1f,
    val temperature: Float = 0.75f,
    val energySigma: Float = 0.22f,
    val sessionSmoothing: Float = 0.25f,
    val interactionHalfLifeMs: Long = 30L * 24L * 60L * 60L * 1000L,
) {
    init {
        require(temperature > 0f && energySigma > 0f && interactionHalfLifeMs > 0L)
        require(listOf(weightLastFm, weightUser, weightGenre, weightMood, weightEnergy, weightTemporal, weightSession, weightNovelty, weightPenalty).all { it >= 0f && it.isFinite() })
    }
}

data class RecommendationFeatureScore(
    val rawScore: Float,
    val probability: Float = 0f,
    val lastFm: Float,
    val user: Float,
    val genre: Float,
    val mood: Float,
    val energy: Float,
    val temporal: Float,
    val session: Float,
    val novelty: Float,
    val repetitionPenalty: Float,
)

enum class IntelligenceEventType {
    PlayStarted, PlayProgress, PlayCompleted, SongSkipped, SongLiked, SongDisliked, SongSaved,
    SongReplayed, SongAddedToPlaylist, SongRemovedFromPlaylist, SongQueued, SongRemovedFromQueue,
    SearchPerformed, RecommendationShown, RecommendationPlayed, RecommendationSkipped,
    CandidatesGenerated, Filtered, Ranked, SequenceOptimized, AutoplayPlanReady,
}

data class IntelligenceEvent(
    val type: IntelligenceEventType,
    val songId: String? = null,
    val recommendationId: String? = null,
    val positionMs: Long = 0L,
    val listenedMs: Long = 0L,
    val occurredAtMs: Long,
    val context: RecommendationContext,
) {
    init { require(positionMs >= 0L && listenedMs >= 0L) }
}

object IntelligenceMath {
    fun decay(ageMs: Long, halfLifeMs: Long): Float {
        if (ageMs <= 0L) return 1f
        require(halfLifeMs > 0L)
        return exp(-ln(2.0) * ageMs.toDouble() / halfLifeMs.toDouble()).toFloat()
    }

    fun energyCompatibility(candidate: Float?, current: Float?, trend: Float, sigma: Float): Float {
        if (candidate == null || current == null) return 0.5f
        val target = (current + trend).coerceIn(0f, 1f)
        val delta = candidate - target
        return exp(-(delta * delta) / (2f * sigma * sigma)).coerceIn(0f, 1f)
    }

    fun softmax(scores: List<Float>, temperature: Float): List<Float> {
        if (scores.isEmpty()) return emptyList()
        require(temperature > 0f)
        val safeScores = scores.map { if (it.isFinite()) it else 0f }
        val max = safeScores.maxOrNull() ?: 0f
        val exps = safeScores.map { exp(((it - max) / temperature).toDouble()).toFloat() }
        val total = exps.sum()
        if (!total.isFinite() || total <= 0f) return List(scores.size) { 1f / scores.size }
        return exps.map { (it / total).coerceIn(0f, 1f) }
    }

    fun cosine(left: Map<String, Float>, right: Map<String, Float>): Float {
        if (left.isEmpty() || right.isEmpty()) return 0f
        val keys = left.keys intersect right.keys
        var dot = 0f
        var leftNorm = 0f
        var rightNorm = 0f
        left.forEach { (_, value) -> leftNorm += value * value }
        right.forEach { (_, value) -> rightNorm += value * value }
        keys.forEach { key -> dot += left.getValue(key) * right.getValue(key) }
        if (leftNorm == 0f || rightNorm == 0f) return 0f
        return (dot / (sqrt(leftNorm) * sqrt(rightNorm))).coerceIn(-1f, 1f)
    }

    fun sampleIndex(probabilities: List<Float>, randomUnit: Float): Int {
        require(probabilities.isNotEmpty())
        val target = randomUnit.coerceIn(0f, 0.99999994f)
        var cumulative = 0f
        probabilities.forEachIndexed { index, probability ->
            cumulative += probability.coerceAtLeast(0f)
            if (target < cumulative) return index
        }
        return probabilities.lastIndex
    }
}

private fun isUnitFloat(value: Float): Boolean = value.isFinite() && value in 0f..1f
private fun smoothScalar(old: Float, next: Float, alpha: Float): Float = old + alpha * (next - old)
private fun smoothMap(old: Map<String, Float>, next: Map<String, Float>, alpha: Float): Map<String, Float> {
    if (old.isEmpty()) return next.mapValues { it.value.coerceIn(0f, 1f) }
    val keys = old.keys + next.keys
    return keys.associateWith { key -> smoothScalar(old[key] ?: 0f, next[key] ?: 0f, alpha).coerceIn(0f, 1f) }
}
private fun cosine(left: Map<String, Float>, right: Map<String, Float>): Float = IntelligenceMath.cosine(left, right)
