/*
 * FrostSoulX taste profile.
 * Built on-device from local listening history; nothing in here leaves the phone.
 */

package dev.vxs.frostsoulx.taste

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

internal data class TasteArtistRef(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
)

internal data class TastePlay(
    val songId: String,
    val artists: List<TasteArtistRef>,
    val timestamp: LocalDateTime,
    val playTimeMs: Long,
    val durationMs: Long,
    val releaseYear: Int?,
    val explicit: Boolean,
    val liked: Boolean,
)

internal enum class TasteFeedbackKind {
    Favorite,
    Dislike,
}

internal data class TasteFeedback(
    val artistIds: List<String>,
    val kind: TasteFeedbackKind,
    val ageDays: Float,
)

internal data class TasteLibrarySong(
    val artists: List<TasteArtistRef>,
    val liked: Boolean,
    val totalPlayTimeMs: Long,
    val durationMs: Long,
    val releaseYear: Int?,
    val explicit: Boolean,
)

internal data class TasteInput(
    val plays: List<TastePlay>,
    val feedback: List<TasteFeedback>,
    val library: List<TasteLibrarySong>,
)

/**
 * Pure, deterministic taste-profile construction.
 *
 * Artist affinity uses exponential half-life decay (a play loses half its weight every
 * [HalfLifeDays]) scaled by how much of the track was actually heard, the standard way to keep an
 * implicit-feedback taste model tracking a listener whose taste drifts. The radar axes are behavioural
 * (how someone listens) because the local database has no audio features; they follow the same
 * familiarity / loyalty / variety / newness split popularised by listening-personality summaries.
 */
internal object TasteProfileBuilder {
    private const val HalfLifeDays = 30f
    private const val DislikeHalfLifeDays = 90f
    private const val RecentWindowDays = 30L
    private const val DiscoveryHorizonDays = 30L
    private const val SessionGapMinutes = 45L
    private const val MinPlaysForAxes = 5
    private const val MinYearPlays = 15
    private const val VarietyReferenceArtists = 40f
    private const val ReplayCeiling = 0.6f
    private const val DiscoveryCeiling = 0.5f
    private const val SessionReferenceMinutes = 90f
    private const val NightShareCeiling = 0.5f
    private const val FreshnessHorizonYears = 15f
    private const val ConfidencePlays = 60f
    private const val LibraryConfidenceWeight = 0.25f
    private const val LibraryAffinityWeight = 0.35f
    private const val LibraryPlayTimeReferenceMs = 1_800_000f
    private const val SecondaryArtistShare = 0.5f
    private const val LikedBoost = 1.4f
    private const val FavoriteBoost = 2.5f
    private const val DislikePenalty = 3f
    private const val AvoidThreshold = -0.5f
    private const val MinCompletionWeight = 0.35f
    private const val UnknownCompletion = 0.6f
    private const val MaxTopArtists = 8
    private const val MaxAffinityEntries = 500
    private const val TraitThreshold = 0.45f
    private const val MinYear = 1900
    private const val MinutesPerDay = 1440f
    private val NightHours = setOf(21, 22, 23, 0, 1, 2, 3, 4)

    private class HistoryContext(
        val start: LocalDateTime?,
        val firstSeenByArtist: Map<String, LocalDateTime>,
    )

    fun build(
        input: TasteInput,
        now: LocalDateTime,
    ): TasteProfile {
        val plays = input.plays.sortedBy { it.timestamp }
        if (plays.isEmpty() && input.library.isEmpty()) return TasteProfile.Empty

        val ledger = ArtistLedger()
        plays.forEach { play -> ledger.addPlay(play, now) }
        input.library.forEach { song -> ledger.addLibrarySong(song) }
        input.feedback.forEach { feedback -> ledger.addFeedback(feedback) }

        val positive = ledger.weights.filterValues { weight -> weight > 0f }
        val strongest = positive.values.maxOrNull() ?: 0f
        val affinity =
            if (strongest > 0f) {
                positive.entries
                    .sortedByDescending { entry -> entry.value }
                    .take(MaxAffinityEntries)
                    .associate { entry -> entry.key to sqrt(entry.value / strongest) }
            } else {
                emptyMap()
            }
        val avoided = ledger.weights.filterValues { weight -> weight < AvoidThreshold }.keys
        val topArtists =
            positive.entries
                .sortedByDescending { entry -> entry.value }
                .mapNotNull { entry ->
                    ledger.refs[entry.key]?.let { artist ->
                        ArtistAffinity(
                            id = artist.id,
                            name = artist.name,
                            thumbnailUrl = artist.thumbnailUrl,
                            share = (entry.value / strongest).coerceIn(0f, 1f),
                            plays = ledger.playCounts[artist.id] ?: 0,
                        )
                    }
                }.take(MaxTopArtists)

        val confidence =
            if (affinity.isEmpty()) {
                0f
            } else {
                ((plays.size + LibraryConfidenceWeight * input.library.size) / ConfidencePlays).coerceIn(0f, 1f)
            }

        val context = historyContext(plays)
        val recentPlays = plays.filter { it.timestamp >= now.minusDays(RecentWindowDays) }
        val allScores = axisScores(plays, context, now)
        val recentScores = if (recentPlays.size >= MinPlaysForAxes) axisScores(recentPlays, context, now) else null
        val axes =
            TasteAxisId.entries.map { id ->
                val all = allScores[id]
                TasteAxisScore(
                    id = id,
                    score = all ?: 0f,
                    recentScore = recentScores?.get(id),
                    hasData = all != null,
                )
            }
        val traits =
            axes
                .filter { it.hasData && it.score >= TraitThreshold }
                .sortedByDescending { it.score }
                .take(2)
                .map { it.id }

        val durations = plays.filter { it.durationMs > 0L }.map { it.durationMs } +
            input.library.filter { it.durationMs > 0L }.map { it.durationMs }
        val years =
            (plays.mapNotNull { it.releaseYear } + input.library.mapNotNull { it.releaseYear })
                .filter { it in MinYear..now.year + 1 }
        val explicitSource = plays.map { it.explicit }.ifEmpty { input.library.map { it.explicit } }

        return TasteProfile(
            generatedAtMs = System.currentTimeMillis(),
            playCount = plays.size,
            confidence = confidence,
            axes = axes,
            dominantTraits = traits,
            topArtists = topArtists,
            artistAffinity = affinity,
            avoidedArtistIds = avoided,
            hourlyShare = share(plays.map { it.timestamp.hour }, 24),
            weekdayShare = share(plays.map { it.timestamp.dayOfWeek.value - 1 }, 7),
            distinctArtists = plays.mapNotNull { it.artists.firstOrNull()?.id }.toSet().size,
            averageDurationSeconds = if (durations.isEmpty()) 0 else (durations.average() / 1000.0).toInt(),
            explicitShare = fraction(explicitSource.count { it }, explicitSource.size),
            likedShare = fraction(plays.count { it.liked }, plays.size),
            medianReleaseYear = if (years.size >= MinYearPlays) years.sorted()[years.size / 2] else null,
            tasteShift = tasteShift(plays, recentPlays),
            averageSessionMinutes =
                if (plays.size >= MinPlaysForAxes) sessionMinutes(plays).average().toInt() else null,
        )
    }

    private class ArtistLedger {
        val weights = HashMap<String, Float>()
        val refs = HashMap<String, TasteArtistRef>()
        val playCounts = HashMap<String, Int>()

        fun addPlay(
            play: TastePlay,
            now: LocalDateTime,
        ) {
            val ageDays = ChronoUnit.MINUTES.between(play.timestamp, now).coerceAtLeast(0L) / MinutesPerDay
            val decay = 0.5f.pow(ageDays / HalfLifeDays)
            val completion = completion(play) ?: UnknownCompletion
            var weight = decay * (MinCompletionWeight + (1f - MinCompletionWeight) * completion)
            if (play.liked) weight *= LikedBoost
            add(play.artists, weight)
            play.artists.firstOrNull()?.let { primary ->
                playCounts[primary.id] = (playCounts[primary.id] ?: 0) + 1
            }
        }

        fun addLibrarySong(song: TasteLibrarySong) {
            val listened = (song.totalPlayTimeMs / LibraryPlayTimeReferenceMs).coerceAtMost(1.5f)
            val weight = ((if (song.liked) 1f else 0f) + listened * 0.5f) * LibraryAffinityWeight
            if (weight > 0f) add(song.artists, weight)
        }

        fun addFeedback(feedback: TasteFeedback) {
            val delta =
                when (feedback.kind) {
                    TasteFeedbackKind.Favorite ->
                        FavoriteBoost * 0.5f.pow(feedback.ageDays / HalfLifeDays)
                    TasteFeedbackKind.Dislike ->
                        -DislikePenalty * 0.5f.pow(feedback.ageDays / DislikeHalfLifeDays)
                }
            feedback.artistIds.forEach { id -> weights[id] = (weights[id] ?: 0f) + delta }
        }

        private fun add(
            artists: List<TasteArtistRef>,
            weight: Float,
        ) {
            artists.forEachIndexed { index, artist ->
                if (artist.id !in refs) refs[artist.id] = artist
                val share = if (index == 0) weight else weight * SecondaryArtistShare
                weights[artist.id] = (weights[artist.id] ?: 0f) + share
            }
        }
    }

    private fun completion(play: TastePlay): Float? =
        if (play.durationMs > 0L) (play.playTimeMs.toFloat() / play.durationMs).coerceIn(0f, 1f) else null

    private fun historyContext(plays: List<TastePlay>): HistoryContext {
        val firstSeen = HashMap<String, LocalDateTime>()
        plays.forEach { play ->
            val artistId = play.artists.firstOrNull()?.id ?: return@forEach
            if (artistId !in firstSeen) firstSeen[artistId] = play.timestamp
        }
        return HistoryContext(start = plays.firstOrNull()?.timestamp, firstSeenByArtist = firstSeen)
    }

    private fun axisScores(
        subset: List<TastePlay>,
        context: HistoryContext,
        now: LocalDateTime,
    ): Map<TasteAxisId, Float?> {
        if (subset.size < MinPlaysForAxes) return TasteAxisId.entries.associateWith { null }
        return TasteAxisId.entries.associateWith { id ->
            when (id) {
                TasteAxisId.EXPLORATION -> exploration(subset, context)
                TasteAxisId.VARIETY -> variety(subset)
                TasteAxisId.REPLAY -> replay(subset)
                TasteAxisId.PATIENCE -> patience(subset)
                TasteAxisId.ENDURANCE -> endurance(subset)
                TasteAxisId.NIGHT_OWL -> nightOwl(subset)
                TasteAxisId.FRESHNESS -> freshness(subset, now)
            }
        }
    }

    /**
     * Share of listening spent on artists within [DiscoveryHorizonDays] of first hearing them. The
     * first horizon of history is skipped: everything there is trivially "new".
     */
    private fun exploration(
        subset: List<TastePlay>,
        context: HistoryContext,
    ): Float? {
        val start = context.start ?: return null
        val eligible = subset.filter { it.timestamp >= start.plusDays(DiscoveryHorizonDays) }
        if (eligible.size < MinPlaysForAxes) return null
        var discovery = 0
        var counted = 0
        eligible.forEach { play ->
            val artistId = play.artists.firstOrNull()?.id ?: return@forEach
            val firstSeen = context.firstSeenByArtist[artistId] ?: return@forEach
            counted++
            if (ChronoUnit.DAYS.between(firstSeen, play.timestamp) <= DiscoveryHorizonDays) discovery++
        }
        if (counted < MinPlaysForAxes) return null
        return (discovery.toFloat() / counted / DiscoveryCeiling).coerceIn(0f, 1f)
    }

    /** Normalised Shannon entropy of the artist mix, saturating at [VarietyReferenceArtists] artists. */
    private fun variety(subset: List<TastePlay>): Float? {
        val counts = subset.mapNotNull { it.artists.firstOrNull()?.id }.groupingBy { it }.eachCount()
        val total = counts.values.sum()
        if (total < MinPlaysForAxes) return null
        var entropy = 0f
        counts.values.forEach { count ->
            val probability = count.toFloat() / total
            entropy -= probability * ln(probability)
        }
        return (entropy / ln(VarietyReferenceArtists)).coerceIn(0f, 1f)
    }

    private fun replay(subset: List<TastePlay>): Float {
        val distinct = subset.map { it.songId }.toSet().size
        val repeatRate = 1f - distinct.toFloat() / subset.size
        return (repeatRate / ReplayCeiling).coerceIn(0f, 1f)
    }

    /** Average fraction of a track heard before moving on. */
    private fun patience(subset: List<TastePlay>): Float? {
        val fractions = subset.mapNotNull { completion(it) }
        if (fractions.size < MinPlaysForAxes) return null
        return fractions.average().toFloat().coerceIn(0f, 1f)
    }

    private fun endurance(subset: List<TastePlay>): Float =
        (sessionMinutes(subset).average().toFloat() / SessionReferenceMinutes).coerceIn(0f, 1f)

    private fun nightOwl(subset: List<TastePlay>): Float {
        val share = subset.count { it.timestamp.hour in NightHours }.toFloat() / subset.size
        return (share / NightShareCeiling).coerceIn(0f, 1f)
    }

    private fun freshness(
        subset: List<TastePlay>,
        now: LocalDateTime,
    ): Float? {
        val years = subset.mapNotNull { it.releaseYear }.filter { it in MinYear..now.year + 1 }
        if (years.size < MinYearPlays) return null
        return years
            .map { year -> (1f - (now.year - year).coerceAtLeast(0) / FreshnessHorizonYears).coerceIn(0f, 1f) }
            .average()
            .toFloat()
    }

    /** Listening minutes per session; a gap longer than [SessionGapMinutes] starts a new one. */
    private fun sessionMinutes(plays: List<TastePlay>): List<Float> {
        val ordered = plays.sortedBy { it.timestamp }
        if (ordered.isEmpty()) return emptyList()
        val sessions = ArrayList<Float>()
        var current = 0f
        var previous: LocalDateTime? = null
        ordered.forEach { play ->
            val last = previous
            if (last != null && ChronoUnit.MINUTES.between(last, play.timestamp) > SessionGapMinutes) {
                sessions += current
                current = 0f
            }
            current += play.playTimeMs / 60_000f
            previous = play.timestamp
        }
        sessions += current
        return sessions
    }

    private fun tasteShift(
        all: List<TastePlay>,
        recent: List<TastePlay>,
    ): Float? {
        if (recent.size < MinPlaysForAxes || all.size - recent.size < MinPlaysForAxes) return null
        val allCounts = all.mapNotNull { it.artists.firstOrNull()?.id }.groupingBy { it }.eachCount()
        val recentCounts = recent.mapNotNull { it.artists.firstOrNull()?.id }.groupingBy { it }.eachCount()
        return (1f - cosine(allCounts, recentCounts)).coerceIn(0f, 1f)
    }

    private fun cosine(
        first: Map<String, Int>,
        second: Map<String, Int>,
    ): Float {
        var dot = 0f
        var firstNorm = 0f
        var secondNorm = 0f
        first.forEach { (key, value) ->
            firstNorm += value.toFloat() * value
            second[key]?.let { other -> dot += value.toFloat() * other }
        }
        second.values.forEach { value -> secondNorm += value.toFloat() * value }
        if (firstNorm <= 0f || secondNorm <= 0f) return 0f
        return (dot / (sqrt(firstNorm) * sqrt(secondNorm))).coerceIn(0f, 1f)
    }

    private fun share(
        buckets: List<Int>,
        size: Int,
    ): List<Float> {
        if (buckets.isEmpty()) return List(size) { 0f }
        val counts = IntArray(size)
        buckets.forEach { bucket -> counts[bucket.coerceIn(0, size - 1)]++ }
        return counts.map { count -> count.toFloat() / buckets.size }
    }

    private fun fraction(
        part: Int,
        whole: Int,
    ): Float = if (whole <= 0) 0f else part.toFloat() / whole
}
