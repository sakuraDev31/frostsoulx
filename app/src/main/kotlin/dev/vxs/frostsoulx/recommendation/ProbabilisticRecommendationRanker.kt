package dev.vxs.frostsoulx.recommendation

/** Pure scorer used by Home, Discovery, and Autoplay adapters. */
class ProbabilisticRecommendationRanker(
    private val config: RecommendationModelConfig = RecommendationModelConfig(),
) {
    fun score(
        candidate: SongFeatureVector,
        user: UserTasteProfile,
        session: SessionState?,
        temporal: TemporalModel?,
        context: RecommendationContext,
        nowMs: Long,
        recentSongIds: Set<String> = session?.recentSongIds?.toSet().orEmpty(),
    ): RecommendationFeatureScore {
        val lastFm = candidate.lastFmSimilarity.averageOrZero()
        val userSimilarity = userSimilarity(candidate, user)
        val genre = IntelligenceMath.cosine(candidate.genreDistribution, user.longTermGenres + user.shortTermGenres)
        val mood = IntelligenceMath.cosine(candidate.moodDistribution, user.longTermMoods + user.shortTermMoods)
        val currentEnergy = session?.currentEnergy ?: user.preferredEnergy
        val energy = IntelligenceMath.energyCompatibility(candidate.energy, currentEnergy, session?.energyTrend ?: 0f, config.energySigma)
        val temporalScore = temporal?.compatibility(context.hourOfDay, context.dayOfWeek, candidate) ?: 0.5f
        val sessionScore = session?.let {
            val genreAffinity = IntelligenceMath.cosine(candidate.genreDistribution, it.genreDistribution)
            val moodAffinity = IntelligenceMath.cosine(candidate.moodDistribution, it.moodDistribution)
            ((genreAffinity + moodAffinity) / 2f).coerceIn(0f, 1f)
        } ?: 0.5f
        val novelty = novelty(candidate, user, nowMs)
        val repetitionPenalty = repetitionPenalty(candidate, recentSongIds, user.negativeSongs)
        val raw =
            config.weightLastFm * lastFm +
                config.weightUser * userSimilarity +
                config.weightGenre * genre +
                config.weightMood * mood +
                config.weightEnergy * energy +
                config.weightTemporal * temporalScore +
                config.weightSession * sessionScore +
                config.weightNovelty * novelty -
                config.weightPenalty * repetitionPenalty
        return RecommendationFeatureScore(
            rawScore = raw,
            lastFm = lastFm,
            user = userSimilarity,
            genre = genre,
            mood = mood,
            energy = energy,
            temporal = temporalScore,
            session = sessionScore,
            novelty = novelty,
            repetitionPenalty = repetitionPenalty,
        )
    }

    fun rank(
        candidates: List<SongFeatureVector>,
        user: UserTasteProfile,
        session: SessionState?,
        temporal: TemporalModel?,
        context: RecommendationContext,
        nowMs: Long,
        limit: Int = candidates.size,
    ): List<Pair<SongFeatureVector, RecommendationFeatureScore>> {
        if (candidates.isEmpty()) return emptyList()
        val unnormalized = candidates.map { candidate ->
            candidate to score(candidate, user, session, temporal, context, nowMs)
        }
        val probabilities = IntelligenceMath.softmax(unnormalized.map { it.second.rawScore }, config.temperature)
        return unnormalized.mapIndexed { index, (candidate, score) ->
            candidate to score.copy(probability = probabilities[index])
        }.sortedByDescending { it.second.rawScore }.take(limit.coerceAtLeast(0))
    }

    /** Seeded probability-aware selection. Callers can pass a stable seed for reproducible tests. */
    fun sample(
        ranked: List<Pair<SongFeatureVector, RecommendationFeatureScore>>,
        randomUnit: Float,
    ): SongFeatureVector? {
        if (ranked.isEmpty()) return null
        val probabilities = IntelligenceMath.softmax(ranked.map { it.second.rawScore }, config.temperature)
        return ranked[IntelligenceMath.sampleIndex(probabilities, randomUnit)].first
    }

    fun probabilities(scores: List<Float>): List<Float> =
        IntelligenceMath.softmax(scores, config.temperature)

    private fun userSimilarity(candidate: SongFeatureVector, user: UserTasteProfile): Float {
        val artist = candidate.artistId?.let { user.artistAffinity[it] } ?: 0f
        val song = user.songAffinity[candidate.songId] ?: 0f
        val genre = IntelligenceMath.cosine(candidate.genreDistribution, user.longTermGenres + user.shortTermGenres)
        val mood = IntelligenceMath.cosine(candidate.moodDistribution, user.longTermMoods + user.shortTermMoods)
        return (0.35f * artist + 0.25f * song + 0.2f * genre + 0.2f * mood).coerceIn(0f, 1f)
    }

    private fun novelty(candidate: SongFeatureVector, user: UserTasteProfile, nowMs: Long): Float {
        val seen = candidate.interaction.plays > 0 || candidate.songId in user.songAffinity
        if (!seen) return 1f
        val lastPlayed = candidate.interaction.lastPlayedAtMs ?: return 0.5f
        return IntelligenceMath.decay((nowMs - lastPlayed).coerceAtLeast(0L), config.interactionHalfLifeMs)
    }

    private fun repetitionPenalty(
        candidate: SongFeatureVector,
        recentSongIds: Set<String>,
        negativeSongIds: Set<String>,
    ): Float {
        if (candidate.songId in negativeSongIds) return 1f
        return if (candidate.songId in recentSongIds) 1f else 0f
    }

    private fun Map<String, Float>.averageOrZero(): Float = values.takeIf { it.isNotEmpty() }?.average()?.toFloat()?.coerceIn(0f, 1f) ?: 0f
}
