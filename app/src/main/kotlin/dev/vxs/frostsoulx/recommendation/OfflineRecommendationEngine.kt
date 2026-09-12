/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.recommendation

import com.google.common.collect.ImmutableList
import dev.vxs.frostsoulx.db.MusicDatabase
import dev.vxs.frostsoulx.db.entities.RecommendationFeatureEntity
import dev.vxs.frostsoulx.db.entities.RecommendationProfileEntity
import dev.vxs.frostsoulx.db.entities.RecommendationSignalEntity
import dev.vxs.frostsoulx.library.GeneratedLibraryTopMix
import dev.vxs.frostsoulx.models.MediaMetadata
import dev.vxs.frostsoulx.models.toMediaMetadata
import dev.vxs.frostsoulx.repository.LibraryTopMixRepository
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
) {
    private val budget = RecommendationBudget()
    private val refreshMutex = Mutex()
    private val encoder = MetadataFeatureEncoder(budget.embeddingDimension)
    private val _lastRefresh = MutableStateFlow<RecommendationRefreshState>(RecommendationRefreshState.Idle)

    val lastRefresh: StateFlow<RecommendationRefreshState> = _lastRefresh.asStateFlow()

    suspend fun refresh(
        context: RecommendationContext,
    ): RecommendationRefreshState =
        refreshMutex.withLock {
            _lastRefresh.value = RecommendationRefreshState.Refreshing
            runCatching {
                withContext(Dispatchers.IO) {
                    val candidates = database.offlineRecommendationCandidates(budget.candidateLimit)
                    if (candidates.isEmpty()) return@withContext RecommendationRefreshState.Empty

                    val tracks = candidates.map { it.toMediaMetadata() }
                    val indexedFeatures = ensureFeatures(tracks)
                    val signals = database.recentRecommendationSignals(budget.maximumSignalsPerRefresh)
                    val signalsBySong = signals.groupBy { it.songId }
                    val contextSignalsBySong =
                        signals
                            .asSequence()
                            .filter { signal -> signal.contextFlags == context.flags() }
                            .groupBy { it.songId }
                    val features = updateBehaviorScores(indexedFeatures, signalsBySong)
                    val profiles = updateTasteProfiles(features, signals)
                    val narrowedTracks = narrowCandidates(tracks, features, profiles)
                    val recommendations =
                        rank(
                            narrowedTracks,
                            features,
                            signalsBySong,
                            contextSignalsBySong,
                            profiles,
                        )
                    val mixes = buildMixes(recommendations)
                    topMixRepository.replaceTopMixes(mixes)
                    RecommendationRefreshState.Success(
                        candidateCount = tracks.size,
                        recommendationCount = recommendations.size,
                        generatedAtMs = System.currentTimeMillis(),
                    )
                }
            }.getOrElse { error ->
                RecommendationRefreshState.Failure(error.message ?: error.javaClass.simpleName)
            }.also { _lastRefresh.value = it }
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
    ): Map<String, RecommendationFeatureEntity> {
        val updatedAtMs = System.currentTimeMillis()
        val updated =
            features.mapValues { (songId, feature) ->
                val history = signalsBySong[songId].orEmpty()
                val positives = history.count { it.isPositive() }
                val negatives = history.count { it.isNegative() }
                feature.copy(
                    replayScore = RecommendationScoreMath.boundedProbability(positives, negatives),
                    skipScore = RecommendationScoreMath.boundedProbability(negatives, positives),
                    updatedAtMs = updatedAtMs,
                )
            }
        database.upsertRecommendationFeatures(updated.values.toList())
        return updated
    }

    private suspend fun updateTasteProfiles(
        features: Map<String, RecommendationFeatureEntity>,
        signals: List<RecommendationSignalEntity>,
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
                                features[signal.songId]?.toQuantizedVector()?.let { vector -> vector to signal.weight() }
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

    private fun rank(
        tracks: List<MediaMetadata>,
        features: Map<String, RecommendationFeatureEntity>,
        signalsBySong: Map<String, List<RecommendationSignalEntity>>,
        contextSignalsBySong: Map<String, List<RecommendationSignalEntity>>,
        profiles: Map<TasteProfileKind, QuantizedVector>,
    ): List<OfflineRecommendation> {
        val profile = profiles[TasteProfileKind.Session] ?: profiles[TasteProfileKind.Weekly] ?: profiles[TasteProfileKind.LongTerm]
            ?: return emptyList()
        val ranked =
            tracks.mapNotNull { track ->
                val feature = features[track.id] ?: return@mapNotNull null
                val history = signalsBySong[track.id].orEmpty()
                val contextHistory = contextSignalsBySong[track.id].orEmpty()
                val positives = history.count { it.isPositive() }
                val negatives = history.count { it.isNegative() }
                val replayProbability =
                    (RecommendationScoreMath.boundedProbability(positives, negatives) + feature.replayScore) / 2f
                val skipProbability =
                    (RecommendationScoreMath.boundedProbability(negatives, positives) + feature.skipScore) / 2f
                val similarity = QuantizedVectorCodec.cosine(profile, feature.toQuantizedVector()).coerceAtLeast(0f)
                val novelty = (1f / (1f + history.size / 3f)).coerceIn(0f, 1f)
                val contextScore = (0.45f + contextHistory.count { it.isPositive() } * 0.11f).coerceAtMost(1f)
                val score =
                    (similarity * 0.45f) +
                        (novelty * 0.18f) +
                        (contextScore * 0.12f) +
                        (replayProbability * 0.22f) -
                        (skipProbability * 0.28f)
                val shelf =
                    when {
                        track.liked && history.size <= 2 -> RecommendationShelfType.ForgottenGems
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
                            similarity = similarity,
                            novelty = novelty,
                            context = contextScore,
                            replayProbability = replayProbability,
                            skipProbability = skipProbability,
                            reason = shelf.description,
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

    private fun buildMixes(recommendations: List<OfflineRecommendation>): List<GeneratedLibraryTopMix> {
        val byShelf = recommendations.groupBy { it.shelf }
        val orderedShelves =
            listOf(
                RecommendationShelfType.DailyMix,
                RecommendationShelfType.BecauseYouLike,
                RecommendationShelfType.ForgottenGems,
                RecommendationShelfType.DeepCuts,
                RecommendationShelfType.RandomDiscovery,
            )
        return orderedShelves.mapNotNull { shelf ->
            val source =
                when (shelf) {
                    RecommendationShelfType.DailyMix -> recommendations
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

    private fun RecommendationSignalEntity.weight(): Float =
        when (type) {
            RecommendationSignalType.Favorite.name -> 2.2f
            RecommendationSignalType.Complete.name -> 1.4f
            RecommendationSignalType.Replay.name -> 1.25f
            RecommendationSignalType.Play.name,
            RecommendationSignalType.Resume.name,
            -> 1f
            RecommendationSignalType.Skip.name -> 0.15f
            RecommendationSignalType.Unlike.name -> 0.05f
            RecommendationSignalType.Dislike.name -> -3.0f
            else -> 0.4f
        }

    private fun RecommendationSignalEntity.isPositive(): Boolean = type in PositiveSignalTypes

    private fun RecommendationSignalEntity.isNegative(): Boolean = type in NegativeSignalTypes

    private companion object {
        const val EmbeddingVersion = 1
        const val MinimumShelfSize = 5
        const val MaxTracksPerArtist = 2
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
        val NegativeSignalTypes =
            setOf(
                RecommendationSignalType.Skip.name,
                RecommendationSignalType.Unlike.name,
                RecommendationSignalType.Dislike.name,
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
