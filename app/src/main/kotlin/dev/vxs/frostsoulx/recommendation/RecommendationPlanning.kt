package dev.vxs.frostsoulx.recommendation

import dev.vxs.frostsoulx.models.MediaMetadata
import androidx.media3.common.MediaItem
import dev.vxs.frostsoulx.extensions.toMediaItem
import kotlin.random.Random

/** Candidate provenance lets Home retain section semantics instead of flattening everything. */
enum class CandidateSource { Library, Recent, ForgottenFavorite, SimilarArtist, LastFm, Transition, Discovery }

data class RecommendationCandidate(
    val track: MediaMetadata,
    val feature: SongFeatureVector,
    val source: CandidateSource,
)

/** Pure merge/filter stage shared by Home and Autoplay adapters. */
class RecommendationCandidateGenerator {
    fun generate(
        sources: List<List<RecommendationCandidate>>,
        context: RecommendationContext,
        limit: Int = 600,
        maxPerArtist: Int = 3,
    ): List<RecommendationCandidate> {
        val seen = HashSet<String>()
        val artists = HashMap<String, Int>()
        val blocked = context.queueState.explicitSongIds.toHashSet() +
            context.recentSongIds.toHashSet() +
            context.queueState.generatedSongIds.toHashSet()
        val negative = context.userProfile?.negativeSongs.orEmpty()
        return buildList {
            sources.asSequence().flatten().forEach { candidate ->
                val id = candidate.track.id
                if (id.isBlank() || id in seen || id in blocked || id in negative) return@forEach
                val artistId = candidate.feature.artistId ?: candidate.track.artists.firstOrNull()?.id
                if (artistId != null && (artists[artistId] ?: 0) >= maxPerArtist) return@forEach
                seen += id
                if (artistId != null) artists[artistId] = (artists[artistId] ?: 0) + 1
                add(candidate)
                if (size >= limit) return@buildList
            }
        }
    }
}

data class AutoplayPlan(
    val appendOnlySongIds: List<String>,
    val probabilities: List<Float>,
    val rankedCandidates: List<IntelligenceCandidateSnapshot> = emptyList(),
    val candidateSourceCounts: Map<String, Int> = emptyMap(),
    val filteringCounts: Map<String, Int> = emptyMap(),
    val energyTrajectory: List<Float> = emptyList(),
    val sequenceOptimizationResult: String? = null,
)

/**
 * Greedy look-ahead planner. It scores each next step against the evolving session, so it does not
 * independently sort by similarity and then create abrupt energy/mood/genre jumps.
 */
class RecommendationSequenceOptimizer(
    private val ranker: ProbabilisticRecommendationRanker = ProbabilisticRecommendationRanker(),
) {
    fun plan(
        candidates: List<RecommendationCandidate>,
        context: RecommendationContext,
        count: Int,
        seed: Long,
    ): AutoplayPlan {
        if (!context.autoplayEnabled || context.queueState.isLocked || count <= 0) return AutoplayPlan(emptyList(), emptyList())
        val user = context.userProfile ?: UserTasteProfile()
        val selected = ArrayList<RecommendationCandidate>(count)
        val selectedProbabilities = ArrayList<Float>(count)
        val rankedSnapshots = ArrayList<IntelligenceCandidateSnapshot>()
        val remaining = candidates.toMutableList()
        var session = context.sessionState
        val energyTrajectory = ArrayList<Float>()
        session?.currentEnergy?.let(energyTrajectory::add)
        val random = Random(seed)
        while (selected.size < count && remaining.isNotEmpty()) {
            val ranked = ranker.rank(
                candidates = remaining.map { it.feature },
                user = user,
                session = session,
                temporal = context.temporalModel,
                context = context,
                nowMs = context.sessionState?.lastEventAtMs ?: 0L,
                limit = remaining.size,
            )
            if (ranked.isEmpty()) break
            val probability = ranker.probabilities(ranked.map { it.second.rawScore })
            ranked.forEachIndexed { index, (feature, score) ->
                val candidate = remaining.firstOrNull { it.feature.songId == feature.songId }
                if (candidate != null) {
                    rankedSnapshots += IntelligenceCandidateSnapshot(
                        songId = feature.songId,
                        title = candidate.track.title,
                        artist = candidate.track.artists.firstOrNull()?.name.orEmpty(),
                        probability = probability.getOrNull(index),
                        score = score.rawScore,
                        lastFm = score.lastFm,
                        userAffinity = score.user,
                        genreCompatibility = score.genre,
                        moodCompatibility = score.mood,
                        energyTransition = score.energy,
                        temporalCompatibility = score.temporal,
                        sessionCompatibility = score.session,
                        novelty = score.novelty,
                        repetitionPenalty = score.repetitionPenalty,
                    )
                }
            }
            val chosenIndex = IntelligenceMath.sampleIndex(probability, random.nextFloat())
            val chosenId = ranked[chosenIndex].first.songId
            val chosen = remaining.firstOrNull { it.feature.songId == chosenId } ?: break
            selected += chosen
            selectedProbabilities += probability[chosenIndex]
            remaining.removeAll { it.feature.songId == chosenId }
            session = session?.updateWith(chosen.feature, session.lastEventAtMs + 1L)
            session?.currentEnergy?.let(energyTrajectory::add)
        }
        return AutoplayPlan(
            appendOnlySongIds = selected.map { it.track.id },
            probabilities = selectedProbabilities,
            rankedCandidates = rankedSnapshots,
            energyTrajectory = energyTrajectory,
            sequenceOptimizationResult = "selected=${selected.size};remaining=${remaining.size}",
        )
    }
}

/** Adapter used by the service's existing background infinite-queue job. */
class IntelligenceAutoplayCoordinator {
    private val generator = RecommendationCandidateGenerator()
    private val optimizer = RecommendationSequenceOptimizer()
    @Volatile private var _lastPlan = AutoplayPlan(emptyList(), emptyList())
    val lastPlan: AutoplayPlan get() = _lastPlan

    fun orderMediaItems(
        items: List<MediaItem>,
        context: RecommendationContext,
        seed: Long,
        additionalCandidates: List<RecommendationCandidate> = emptyList(),
    ): List<MediaItem> {
        if (items.isEmpty() && additionalCandidates.isEmpty() || !context.autoplayEnabled || context.queueState.isLocked) {
            _lastPlan = AutoplayPlan(emptyList(), emptyList())
            return items
        }
        val radioCandidates = items.map { item ->
            val title = item.mediaMetadata.title?.toString().orEmpty().ifBlank { item.mediaId }
            val artist = item.mediaMetadata.artist?.toString().orEmpty()
            RecommendationCandidate(
                track = MediaMetadata(
                    id = item.mediaId,
                    title = title,
                    artists = listOf(MediaMetadata.Artist(id = artist.ifBlank { "unknown" }, name = artist)),
                    duration = (item.mediaMetadata.durationMs ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                ),
                feature = SongFeatureVector(
                    songId = item.mediaId,
                    artistId = artist.ifBlank { null },
                ),
                source = CandidateSource.Transition,
            )
        }
        val filtered = generator.generate(
            sources = listOf(radioCandidates, additionalCandidates),
            context = context,
            limit = items.size + additionalCandidates.size,
            maxPerArtist = Int.MAX_VALUE,
        )
        val plan = optimizer.plan(filtered, context, count = filtered.size, seed = seed).copy(
            candidateSourceCounts = (radioCandidates + additionalCandidates)
                .groupingBy { it.source.name }
                .eachCount(),
            filteringCounts = mapOf("filtered" to ((radioCandidates.size + additionalCandidates.size) - filtered.size).coerceAtLeast(0)),
        )
        _lastPlan = plan
        if (plan.appendOnlySongIds.isEmpty()) return items
        val byId = items.associateBy { it.mediaId } + additionalCandidates.associate { it.track.id to it.track.toMediaItem() }
        return plan.appendOnlySongIds.mapNotNull(byId::get)
    }
}
