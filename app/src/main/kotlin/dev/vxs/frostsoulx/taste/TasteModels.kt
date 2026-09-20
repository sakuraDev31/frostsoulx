/*
 * FrostSoulX taste profile.
 * Built on-device from local listening history; nothing in here leaves the phone.
 */

package dev.vxs.frostsoulx.taste

import androidx.compose.runtime.Immutable
import dev.vxs.frostsoulx.db.entities.Song
import kotlin.math.abs
import kotlin.math.min

/** The dimensions drawn on the taste radar. Declaration order is the clockwise drawing order. */
enum class TasteAxisId {
    EXPLORATION,
    VARIETY,
    REPLAY,
    PATIENCE,
    ENDURANCE,
    NIGHT_OWL,
    FRESHNESS,
}

@Immutable
data class TasteAxisScore(
    val id: TasteAxisId,
    /** Normalised to 0..1. Only meaningful when [hasData] is true. */
    val score: Float,
    /** Same measurement over the last 30 days, or null when there is too little recent listening. */
    val recentScore: Float?,
    val hasData: Boolean,
)

@Immutable
data class ArtistAffinity(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    /** Weight relative to the strongest artist, 0..1. */
    val share: Float,
    val plays: Int,
)

/**
 * A snapshot of what the listener likes and how they listen.
 *
 * [artistAffinity] drives ranking (recency-decayed, square-root compressed so mid-tier artists
 * still count); the remaining fields drive the Taste Profile screen.
 */
@Immutable
data class TasteProfile(
    val generatedAtMs: Long,
    val playCount: Int,
    /** 0..1: how much listening evidence backs this profile. 0 means "no profile yet". */
    val confidence: Float,
    val axes: List<TasteAxisScore>,
    val dominantTraits: List<TasteAxisId>,
    val topArtists: List<ArtistAffinity>,
    val artistAffinity: Map<String, Float>,
    val avoidedArtistIds: Set<String>,
    /** 24 values (local hour of day) summing to 1, or all zeros without history. */
    val hourlyShare: List<Float>,
    /** 7 values, Monday first, summing to 1, or all zeros without history. */
    val weekdayShare: List<Float>,
    val distinctArtists: Int,
    val averageDurationSeconds: Int,
    val explicitShare: Float,
    val likedShare: Float,
    val medianReleaseYear: Int?,
    /** 0..1 distance between last-30-day and all-time artist mix, or null without enough history. */
    val tasteShift: Float?,
    val averageSessionMinutes: Int?,
) {
    val isUsable: Boolean
        get() = confidence > 0f && artistAffinity.isNotEmpty()

    /**
     * How well a track fits this profile, 0..1. Artist familiarity carries most of the weight; track
     * length and release era nudge it toward what the listener typically plays.
     */
    fun affinity(
        artistIds: List<String>,
        durationSeconds: Int,
        releaseYear: Int?,
    ): Float {
        if (!isUsable) return 0f
        val artistScore = artistIds.maxOfOrNull { artistAffinity[it] ?: 0f } ?: 0f
        var fitTotal = 0f
        var fitCount = 0
        if (durationSeconds > 0 && averageDurationSeconds > 0) {
            fitTotal += 1f - min(1f, abs(durationSeconds - averageDurationSeconds) / DurationTolerance)
            fitCount++
        }
        val median = medianReleaseYear
        if (releaseYear != null && median != null) {
            fitTotal += 1f - min(1f, abs(releaseYear - median) / EraTolerance)
            fitCount++
        }
        val traitFit = if (fitCount == 0) NeutralFit else fitTotal / fitCount
        return (artistScore * ArtistWeight + traitFit * (1f - ArtistWeight)).coerceIn(0f, 1f)
    }

    companion object {
        private const val ArtistWeight = 0.75f
        private const val NeutralFit = 0.5f
        private const val DurationTolerance = 240f
        private const val EraTolerance = 15f

        val Empty =
            TasteProfile(
                generatedAtMs = 0L,
                playCount = 0,
                confidence = 0f,
                axes = emptyList(),
                dominantTraits = emptyList(),
                topArtists = emptyList(),
                artistAffinity = emptyMap(),
                avoidedArtistIds = emptySet(),
                hourlyShare = List(24) { 0f },
                weekdayShare = List(7) { 0f },
                distinctArtists = 0,
                averageDurationSeconds = 0,
                explicitShare = 0f,
                likedShare = 0f,
                medianReleaseYear = null,
                tasteShift = null,
                averageSessionMinutes = null,
            )
    }
}

fun TasteProfile.affinityOf(song: Song): Float =
    affinity(
        artistIds = song.artists.map { artist -> artist.id },
        durationSeconds = song.song.duration,
        releaseYear = song.song.year,
    )
