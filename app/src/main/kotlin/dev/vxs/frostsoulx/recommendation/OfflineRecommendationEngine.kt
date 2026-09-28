/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.recommendation

import com.google.common.collect.ImmutableList
import dev.vxs.frostsoulx.BuildConfig
import dev.vxs.frostsoulx.db.MusicDatabase
import dev.vxs.frostsoulx.db.entities.RecommendationFeatureEntity
import dev.vxs.frostsoulx.db.entities.RecommendationProfileEntity
import dev.vxs.frostsoulx.db.entities.RecommendationSignalEntity
import dev.vxs.frostsoulx.library.GeneratedLibraryTopMix
import dev.vxs.frostsoulx.models.MediaMetadata
import dev.vxs.frostsoulx.models.toMediaMetadata
import dev.vxs.frostsoulx.repository.LibraryTopMixRepository
import dev.vxs.frostsoulx.taste.GetTasteProfileUseCase
import dev.vxs.frostsoulx.taste.TasteProfile
import dev.vxs.frostsoulx.taste.affinityOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineRecommendationEngine @Inject constructor(
    private val database: MusicDatabase,
    private val topMixRepository: LibraryTopMixRepository,
    private val getTasteProfile: GetTasteProfileUseCase,
    private val telemetryStore: IntelligenceTelemetryStore,
) {
    private val budget = RecommendationBudget()
    private val modelConfig = RecommendationModelConfig()
    private val formalRanker = ProbabilisticRecommendationRanker(modelConfig)
    private val candidateGenerator = RecommendationCandidateGenerator()
    private val refreshMutex = Mutex()

    @Volatile
    private var lastMixTrackIds: Set<String> = emptySet()
    private val encoder = MetadataFeatureEncoder(budget.embeddingDimension)
    private val _lastRefresh = MutableStateFlow<RecommendationRefreshState>(RecommendationRefreshState.Idle)
    private val _uiState = MutableStateFlow(RecommendationUiState())
    private var lastFormalScores: Map<String, RecommendationFeatureScore> = emptyMap()

    val lastRefresh: StateFlow<RecommendationRefreshState> = _lastRefresh.asStateFlow()
    val uiState: StateFlow<RecommendationUiState> = _uiState.asStateFlow()
    val telemetry: StateFlow<IntelligenceSnapshot> = telemetryStore.snapshot

    suspend fun refresh(
        context: RecommendationContext,
        forceTasteRefresh: Boolean = true,
    ): RecommendationRefreshState =
        refreshMutex.withLock {
            val startedAtNs = System.nanoTime()
            _lastRefresh.value = RecommendationRefreshState.Refreshing
            _uiState.value = _uiState.value.copy(refreshState = RecommendationRefreshState.Refreshing, isOffline = context.isOffline)
            runCatching {
                withContext(Dispatchers.IO) {
                    val taste = getTasteProfile(forceRefresh = forceTasteRefresh).getOrDefault(TasteProfile.Empty)
                    val candidates =
                        database
                            .offlineRecommendationCandidates(budget.candidateLimit)
                            .filterNot { song -> song.artists.any { artist -> artist.id in taste.avoidedArtistIds } }
                    if (candidates.isEmpty()) {
                        _uiState.value = _uiState.value.copy(homeMixes = emptyList())
                        return@withContext RecommendationRefreshState.Empty
                    }

                    val tracks = candidates.map { it.toMediaMetadata() }
                    // MediaMetadata.duration is seconds; signal timing (listenedMs/positionMs) is
                    // milliseconds, so this map normalizes the unit once for every consumer below.
                    val durationById = tracks.associate { it.id to it.duration.toLong() * 1_000L }
                    val indexedFeatures = ensureFeatures(tracks)
                    val signals = database.recentRecommendationSignals(budget.maximumSignalsPerRefresh)
                    val signalsBySong = signals.groupBy { it.songId }
                    val contextSignalsBySong =
                        signals
                            .asSequence()
                            .filter { signal -> signal.contextFlags == context.flags() }
                            .groupBy { it.songId }
                    val features = updateBehaviorScores(indexedFeatures, signalsBySong, durationById)
                    val profiles = updateTasteProfiles(features, signals, durationById)
                    val sequenceAffinity = buildSequenceAffinity(signals)
                    val tasteAffinity =
                        if (taste.isUsable) candidates.associate { song -> song.id to taste.affinityOf(song) } else emptyMap()
                    val tasteWeight = if (taste.isUsable) taste.confidence * MaxTasteWeight else 0f
                    val narrowedTracks = narrowCandidates(tracks, features, profiles)
                    val generatedHomeCandidates =
                        candidateGenerator
                            .generate(
                                sources = listOf(
                                    narrowedTracks.mapNotNull { track ->
                                        features[track.id]?.let { feature ->
                                            RecommendationCandidate(
                                                track = track,
                                                feature = SongFeatureVector(
                                                    songId = track.id,
                                                    artistId = track.artists.firstOrNull()?.id,
                                                    albumId = track.album?.id,
                                                ),
                                                source = CandidateSource.Library,
                                            )
                                        }
                                    },
                                ),
                                context = context,
                                limit = budget.candidateLimit,
                                maxPerArtist = MaxTracksPerArtist,
                            ).map { it.track }
                    val recommendations =
                        rank(
                            generatedHomeCandidates,
                            features,
                            signalsBySong,
                            contextSignalsBySong,
                            profiles,
                            sequenceAffinity,
                            tasteAffinity,
                            tasteWeight,
                            taste,
                            context,
                        )
                    val mixes = buildMixes(recommendations)
                    topMixRepository.replaceTopMixes(mixes)
                    lastMixTrackIds = mixes.flatMapTo(HashSet()) { mix -> mix.tracks.map { track -> track.id } }
                    _uiState.value = _uiState.value.copy(homeMixes = mixes)
                    publishTelemetry(
                        context = context,
                        status = "ready",
                        candidateCount = tracks.size,
                        generatedCandidateCount = generatedHomeCandidates.size,
                        recommendations = recommendations,
                        processingDurationMs = (System.nanoTime() - startedAtNs) / 1_000_000L,
                    )
                    RecommendationRefreshState.Success(
                        candidateCount = tracks.size,
                        recommendationCount = recommendations.size,
                        generatedAtMs = System.currentTimeMillis(),
                    )
                }
            }.getOrElse { error ->
                RecommendationRefreshState.Failure(error.message ?: error.javaClass.simpleName)
            }.also {
                _lastRefresh.value = it
                _uiState.value = _uiState.value.copy(
                    refreshState = it,
                    hasError = it is RecommendationRefreshState.Failure,
                )
            }
        }

    private fun publishTelemetry(
        context: RecommendationContext,
        status: String,
        candidateCount: Int,
        generatedCandidateCount: Int,
        recommendations: List<OfflineRecommendation>,
        processingDurationMs: Long,
    ) {
        if (!BuildConfig.DEBUG) return
        val ranked = recommendations.map { recommendation ->
            val factor = lastFormalScores[recommendation.track.id]
            IntelligenceCandidateSnapshot(
                songId = recommendation.track.id,
                title = recommendation.track.title,
                artist = recommendation.track.artists.firstOrNull()?.name.orEmpty(),
                probability = factor?.probability,
                score = factor?.rawScore ?: recommendation.explanation.score,
                lastFm = factor?.lastFm,
                userAffinity = factor?.user,
                genreCompatibility = factor?.genre,
                moodCompatibility = factor?.mood,
                energyTransition = factor?.energy,
                temporalCompatibility = factor?.temporal,
                sessionCompatibility = factor?.session,
                novelty = factor?.novelty,
                repetitionPenalty = factor?.repetitionPenalty,
            )
        }
        telemetryStore.publish(
            IntelligenceSnapshot(
                engineStatus = status,
                mode = if (context.isOffline) "offline" else "local-cache",
                recommendationMode = "home",
                modelVersion = IntelligenceModelVersion.toString(),
                currentTrackId = context.currentSongId,
                sessionId = context.sessionState?.sessionId,
                hourOfDay = context.hourOfDay,
                dayOfWeek = context.dayOfWeek,
                moodDistribution = context.sessionState?.moodDistribution.orEmpty(),
                genreDistribution = context.sessionState?.genreDistribution.orEmpty(),
                candidateSourceCounts = mapOf("library" to generatedCandidateCount),
                filteringCounts = mapOf("filtered" to (candidateCount - generatedCandidateCount).coerceAtLeast(0)),
                rankedCandidates = ranked,
                selectedNextTrackId = ranked.firstOrNull()?.songId,
                energyTrajectory = buildList {
                    context.sessionState?.currentEnergy?.let(::add)
                    context.sessionState?.energyTrend?.let { trend -> add((context.sessionState?.currentEnergy ?: 0f) + trend) }
                },
                processingDurationMs = processingDurationMs,
                isOffline = context.isOffline,
            ),
        )
    }

    private suspend fun ensureFeatures(
        tracks: List<MediaMetadata>,
    ): Map<String, RecommendationFeatureEntity> {
        val existing = database.recommendationFeatures(tracks.map { it.id }).associateBy { it.songId }.toMutableMap()
        val now = System.currentTimeMillis()
        val missing = tracks.filter { track ->
            val saved = existing[track.id]
            saved == null || saved.embeddingVersion != EmbeddingVersion || saved.dimension != budget.embeddingDimension
        }
        if (missing.isNotEmpty()) {
            missing.chunked(budget.backgroundBatchSize).forEach { chunk ->
                val encoded =
                    chunk.map { track ->
                        val vector = QuantizedVectorCodec.quantize(encoder.encode(track))
                        RecommendationFeatureEntity(
                            songId = track.id,
                            embeddingVersion = EmbeddingVersion,
                            dimension = vector.dimension,
                            quantizedEmbedding = vector.values,
                            scale = vector.scale,
                            norm = vector.norm,
                            replayScore = 0.5f,
                            skipScore = 0.5f,
                            updatedAtMs = now,
                        )
                    }
                database.upsertRecommendationFeatures(encoded)
                encoded.forEach { existing[it.songId] = it }
            }
        }
        return existing
    }

    private suspend fun updateBehaviorScores(
        features: Map<String, RecommendationFeatureEntity>,
        signalsBySong: Map<String, List<RecommendationSignalEntity>>,
        durationById: Map<String, Long>,
    ): Map<String, RecommendationFeatureEntity> {
        val updatedAtMs = System.currentTimeMillis()
        val updated =
            features.mapValues { (songId, feature) ->
                val history = signalsBySong[songId].orEmpty()
                val duration = durationById[songId]
                val positiveWeight = history.sumOf { it.positiveWeight(duration).toDouble() }.toFloat()
                val negativeWeight = history.sumOf { it.negativeWeight(duration).toDouble() }.toFloat()
                feature.copy(
                    replayScore = RecommendationScoreMath.boundedProbabilityWeighted(positiveWeight, negativeWeight),
                    skipScore = RecommendationScoreMath.boundedProbabilityWeighted(negativeWeight, positiveWeight),
                    updatedAtMs = updatedAtMs,
                )
            }
        database.upsertRecommendationFeatures(updated.values.toList())
        return updated
    }

    private suspend fun updateTasteProfiles(
        features: Map<String, RecommendationFeatureEntity>,
        signals: List<RecommendationSignalEntity>,
        durationById: Map<String, Long>,
    ): Map<TasteProfileKind, QuantizedVector> {
        val nowMs = System.currentTimeMillis()
        val profileSignals =
            mapOf(
                TasteProfileKind.LongTerm to signals,
                TasteProfileKind.Weekly to signals.filter { it.occurredAtMs >= nowMs - WeekMs },
                TasteProfileKind.Daily to signals.filter { it.occurredAtMs >= nowMs - DayMs },
                TasteProfileKind.Session to signals.take(120),
                TasteProfileKind.Discovery to signals.filter { it.type == RecommendationSignalType.Complete.name },
                TasteProfileKind.Context to signals.filter { it.contextFlags != 0 },
            )
        return buildMap {
            profileSignals.forEach { (kind, source) ->
                val vector =
                    QuantizedVectorCodec.weightedAverage(
                        vectors =
                            source.mapNotNull { signal ->
                                features[signal.songId]?.toQuantizedVector()?.let { vector ->
                                    vector to signal.positiveWeight(durationById[signal.songId])
                                }
                            },
                        dimension = budget.embeddingDimension,
                    )
                if (vector.norm <= 0f) return@forEach
                database.upsertRecommendationProfile(
                    RecommendationProfileEntity(
                        profile = kind.name,
                        embeddingVersion = EmbeddingVersion,
                        dimension = vector.dimension,
                        quantizedEmbedding = vector.values,
                        scale = vector.scale,
                        norm = vector.norm,
                        updatedAtMs = System.currentTimeMillis(),
                    ),
                )
                put(kind, vector)
            }
            if (isEmpty()) {
                val fallback = QuantizedVectorCodec.weightedAverage(
                    features.values.map { it.toQuantizedVector() to 1f },
                    budget.embeddingDimension,
                )
                if (fallback.norm > 0f) put(TasteProfileKind.LongTerm, fallback)
            }
        }
    }

    private fun narrowCandidates(
        tracks: List<MediaMetadata>,
        features: Map<String, RecommendationFeatureEntity>,
        profiles: Map<TasteProfileKind, QuantizedVector>,
    ): List<MediaMetadata> {
        val profile = profiles[TasteProfileKind.Session] ?: profiles[TasteProfileKind.Weekly] ?: profiles[TasteProfileKind.LongTerm]
            ?: return tracks
        val index = BoundedCosineVectorIndex(maximumItems = minOf(budget.candidateLimit, budget.maximumIndexItems))
        tracks.forEach { track ->
            features[track.id]?.toQuantizedVector()?.let { vector -> index.upsert(track.id, vector) }
        }
        val retrievalIds = index.nearest(profile, limit = minOf(budget.candidateLimit, budget.rankLimit * 4)).toSet()
        return tracks.filter { it.id in retrievalIds }
    }

    /**
     * Personal, session-derived sequential affinity: "after playing X, this listener usually
     * plays Y next". This is the sequential-listening signal the research notes cite as a core
     * recommendation input but that the ranking formula never actually consumed — no server or
     * other-user data is required, since a single listener's own session history is enough to
     * build it.
     *
     * Anchors on the most recently played distinct tracks (most-recent weighted highest), then
     * scores every candidate by how often it has historically followed one of those anchors
     * within the same listening session, normalized to [0, 1].
     */
    private fun buildSequenceAffinity(signals: List<RecommendationSignalEntity>): Map<String, Float> {
        val bySession = signals.groupBy { it.sessionId }
        val transitionCounts = mutableMapOf<Pair<String, String>, Int>()
        bySession.values.forEach { sessionSignals ->
            val ordered = sessionSignals.filter { it.isPositive() }.sortedBy { it.occurredAtMs }
            for (index in 1 until ordered.size) {
                val from = ordered[index - 1].songId
                val to = ordered[index].songId
                if (from == to) continue
                val key = from to to
                transitionCounts[key] = (transitionCounts[key] ?: 0) + 1
            }
        }
        if (transitionCounts.isEmpty()) return emptyMap()

        val recentAnchors =
            signals
                .asSequence()
                .filter { it.isPositive() }
                .sortedByDescending { it.occurredAtMs }
                .map { it.songId }
                .distinct()
                .take(RecentAnchorCount)
                .toList()
        if (recentAnchors.isEmpty()) return emptyMap()

        val raw = mutableMapOf<String, Float>()
        recentAnchors.forEachIndexed { index, anchor ->
            val anchorWeight = 1f / (index + 1f)
            transitionCounts.forEach { (pair, count) ->
                if (pair.first == anchor) {
                    raw[pair.second] = (raw[pair.second] ?: 0f) + count * anchorWeight
                }
            }
        }
        val maxValue = raw.values.maxOrNull() ?: return emptyMap()
        if (maxValue <= 0f) return emptyMap()
        return raw.mapValues { (_, value) -> (value / maxValue).coerceIn(0f, 1f) }
    }

    private fun rank(
        tracks: List<MediaMetadata>,
        features: Map<String, RecommendationFeatureEntity>,
        signalsBySong: Map<String, List<RecommendationSignalEntity>>,
        contextSignalsBySong: Map<String, List<RecommendationSignalEntity>>,
        profiles: Map<TasteProfileKind, QuantizedVector>,
        sequenceAffinity: Map<String, Float>,
        tasteAffinity: Map<String, Float>,
        tasteWeight: Float,
        tasteProfile: TasteProfile,
        context: RecommendationContext,
    ): List<OfflineRecommendation> {
        val profile = profiles[TasteProfileKind.Session] ?: profiles[TasteProfileKind.Weekly] ?: profiles[TasteProfileKind.LongTerm]
            ?: return emptyList()
        val formalScores =
            tracks.mapNotNull { track ->
                val feature = features[track.id] ?: return@mapNotNull null
                val history = signalsBySong[track.id].orEmpty()
                track.id to formalRanker.score(
                    candidate = SongFeatureVector(
                        songId = track.id,
                        artistId = track.artists.firstOrNull()?.id,
                        albumId = track.album?.id,
                        popularity = null,
                        interaction = InteractionStats(
                            plays = history.count { it.isPositive() },
                            completed = history.count { it.type == RecommendationSignalType.Complete.name },
                            skips = history.count { it.type == RecommendationSignalType.Skip.name },
                            replays = history.count { it.type == RecommendationSignalType.Replay.name },
                            likes = if (track.liked) 1 else 0,
                            lastPlayedAtMs = history.maxOfOrNull { it.occurredAtMs },
                        ),
                    ),
                    user = UserTasteProfile(
                        artistAffinity = tasteProfile.artistAffinity,
                        songAffinity = tasteAffinity,
                        negativeSongs = emptySet(),
                        explorationPreference = context.explorationLevel,
                        familiarityPreference = 1f - context.explorationLevel,
                    ),
                    session = context.sessionState,
                    temporal = context.temporalModel,
                    context = context,
                    nowMs = System.currentTimeMillis(),
                    recentSongIds = context.recentSongIds.toSet() + lastMixTrackIds,
                )
            }.toMap()
        lastFormalScores = formalScores
        val ranked =
            tracks.mapNotNull { track ->
                val feature = features[track.id] ?: return@mapNotNull null
                val history = signalsBySong[track.id].orEmpty()
                val contextHistory = contextSignalsBySong[track.id].orEmpty()
                val durationMs = track.duration.toLong() * 1_000L
                val positiveWeight = history.sumOf { it.positiveWeight(durationMs).toDouble() }.toFloat()
                val negativeWeight = history.sumOf { it.negativeWeight(durationMs).toDouble() }.toFloat()
                val replayProbability =
                    (RecommendationScoreMath.boundedProbabilityWeighted(positiveWeight, negativeWeight) + feature.replayScore) / 2f
                val skipProbability =
                    (RecommendationScoreMath.boundedProbabilityWeighted(negativeWeight, positiveWeight) + feature.skipScore) / 2f
                // The hashed metadata embedding is a weak signal on its own; blend in how well the
                // track fits the listener's artist-level taste profile.
                val embeddingSimilarity = QuantizedVectorCodec.cosine(profile, feature.toQuantizedVector()).coerceAtLeast(0f)
                val similarity = embeddingSimilarity * (1f - tasteWeight) + (tasteAffinity[track.id] ?: 0f) * tasteWeight
                val novelty = (1f / (1f + history.size / 3f)).coerceIn(0f, 1f)
                val contextScore = (0.45f + contextHistory.count { it.isPositive() } * 0.11f).coerceAtMost(1f)
                // "People who just played this also played that" — a personal, session-derived
                // sequential signal (see buildSequenceAffinity) that previously had no path into
                // the score at all.
                val sequence = sequenceAffinity[track.id] ?: 0f
                // Without any variation every refresh returned the identical mixes. A little jitter
                // plus a nudge against last time's tracks lets near-ties rotate, while strong taste
                // matches still win.
                val formalScore = formalScores[track.id] ?: return@mapNotNull null
                val score = formalScore.rawScore
                val shelf =
                    when {
                        track.liked && history.size <= 2 -> RecommendationShelfType.ForgottenGems
                        sequence >= 0.55f -> RecommendationShelfType.ContinueListening
                        novelty >= 0.68f && similarity >= 0.38f -> RecommendationShelfType.DeepCuts
                        similarity >= 0.52f -> RecommendationShelfType.BecauseYouLike
                        else -> RecommendationShelfType.RandomDiscovery
                    }
                OfflineRecommendation(
                    track = track,
                    shelf = shelf,
                    explanation =
                        RecommendationExplanation(
                            score = score,
                            similarity = formalScore.user,
                            novelty = formalScore.novelty,
                            context = formalScore.temporal,
                            replayProbability = replayProbability,
                            skipProbability = skipProbability,
                            reason = shelf.description,
                            sequenceAffinity = sequence,
                        ),
                )
            }.sortedByDescending { it.explanation.score }

        return diversify(ranked).take(budget.rankLimit)
    }

    private fun diversify(ranked: List<OfflineRecommendation>): List<OfflineRecommendation> {
        val artistCounts = mutableMapOf<String, Int>()
        return buildList(ranked.size) {
            ranked.forEach { recommendation ->
                val primaryArtist = recommendation.track.artists.firstOrNull()?.name.orEmpty()
                val count = artistCounts[primaryArtist] ?: 0
                if (primaryArtist.isNotBlank() && count >= MaxTracksPerArtist) return@forEach
                artistCounts[primaryArtist] = count + 1
                add(recommendation)
            }
        }
    }

    private fun stableJitter(songId: String): Float =
        ((songId.hashCode() and Int.MAX_VALUE) % 10_000) / 10_000f

    private fun buildMixes(recommendations: List<OfflineRecommendation>): List<GeneratedLibraryTopMix> {
        val byShelf = recommendations.groupBy { it.shelf }
        val orderedShelves =
            listOf(
                RecommendationShelfType.DailyMix,
                RecommendationShelfType.ContinueListening,
                RecommendationShelfType.BecauseYouLike,
                RecommendationShelfType.ForgottenGems,
                RecommendationShelfType.DeepCuts,
                RecommendationShelfType.RandomDiscovery,
            )
        return orderedShelves.mapNotNull { shelf ->
            val source =
                when (shelf) {
                    RecommendationShelfType.DailyMix -> recommendations
                    RecommendationShelfType.RandomDiscovery -> byShelf[shelf].orEmpty().shuffled()
                    else -> byShelf[shelf].orEmpty()
                }
            val tracks = source.take(budget.shelfSize).map { it.track }
            tracks.takeIf { it.size >= MinimumShelfSize }?.let {
                GeneratedLibraryTopMix(
                    id = "offline_${shelf.name.lowercase()}",
                    title = shelf.title,
                    description = shelf.description,
                    tracks = ImmutableList.copyOf(it),
                )
            }
        }
    }

    private fun RecommendationFeatureEntity.toQuantizedVector(): QuantizedVector =
        QuantizedVector(dimension, quantizedEmbedding, scale, norm)

    /** Fraction of the track actually heard for this signal, or 0 when duration is unknown. */
    private fun RecommendationSignalEntity.listenFraction(durationMs: Long?): Float {
        if (durationMs == null || durationMs <= 0L) return 0f
        return (listenedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Continuous positive contribution, used both for taste-profile construction and for the
     * replay-probability blend in ranking. A Skip is no longer a flat constant: skipping after
     * most of the track had already played reads as a near-complete listen, not a rejection —
     * the same distinction Spotify's own published skip-prediction research treats as essential,
     * and one this app already has the data (listenedMs) to make but wasn't using.
     */
    private fun RecommendationSignalEntity.positiveWeight(durationMs: Long?): Float {
        val fraction = listenFraction(durationMs)
        return when (type) {
            RecommendationSignalType.Favorite.name -> 2.2f
            RecommendationSignalType.Complete.name -> 1.4f
            RecommendationSignalType.Replay.name -> 1.25f
            RecommendationSignalType.Play.name,
            RecommendationSignalType.Resume.name,
            -> 1f
            RecommendationSignalType.Skip.name -> ((fraction - 0.4f) * 2f).coerceAtLeast(0f)
            RecommendationSignalType.Unlike.name -> 0.05f
            else -> 0f
        }
    }

    /** Continuous negative contribution, mirroring [positiveWeight] for the skip-probability side. */
    private fun RecommendationSignalEntity.negativeWeight(durationMs: Long?): Float {
        val fraction = listenFraction(durationMs)
        return when (type) {
            RecommendationSignalType.Dislike.name -> 3.0f
            RecommendationSignalType.Unlike.name -> 0.4f
            RecommendationSignalType.Skip.name -> ((0.5f - fraction) * 2.2f).coerceAtLeast(0f)
            else -> 0f
        }
    }

    private fun RecommendationSignalEntity.isPositive(): Boolean = type in PositiveSignalTypes

    private companion object {
        const val EmbeddingVersion = 1
        const val MaxTasteWeight = 0.6f
        const val ExplorationJitter = 0.10f
        const val RepeatExposurePenalty = 0.06f
        const val MinimumShelfSize = 5
        const val MaxTracksPerArtist = 2
        const val RecentAnchorCount = 5
        const val DayMs = 24L * 60L * 60L * 1000L
        const val WeekMs = 7L * DayMs
        val PositiveSignalTypes =
            setOf(
                RecommendationSignalType.Play.name,
                RecommendationSignalType.Resume.name,
                RecommendationSignalType.Complete.name,
                RecommendationSignalType.Replay.name,
                RecommendationSignalType.Favorite.name,
            )
    }
}

sealed interface RecommendationRefreshState {
    data object Idle : RecommendationRefreshState

    data object Refreshing : RecommendationRefreshState

    data object Empty : RecommendationRefreshState

    data class Success(
        val candidateCount: Int,
        val recommendationCount: Int,
        val generatedAtMs: Long,
    ) : RecommendationRefreshState

    data class Failure(
        val detail: String,
    ) : RecommendationRefreshState
}

data class RecommendationUiState(
    val refreshState: RecommendationRefreshState = RecommendationRefreshState.Idle,
    val homeMixes: List<GeneratedLibraryTopMix> = emptyList(),
    val isOffline: Boolean = false,
    val hasError: Boolean = false,
)
