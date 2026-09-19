/*
 * FrostSoulX taste profile.
 * Built on-device from local listening history; nothing in here leaves the phone.
 */

package dev.vxs.frostsoulx.taste

import dev.vxs.frostsoulx.db.MusicDatabase
import dev.vxs.frostsoulx.db.entities.ArtistEntity
import dev.vxs.frostsoulx.db.entities.EventWithSong
import dev.vxs.frostsoulx.db.entities.Song
import dev.vxs.frostsoulx.recommendation.RecommendationSignalType
import dev.vxs.frostsoulx.utils.reportException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the [TasteProfile] from Room history on demand and keeps the latest one in memory, so the
 * profile always exists by the time Home or the offline mixes need it, without a schema change.
 */
@Singleton
class TasteProfileRepository
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) {
        private val mutex = Mutex()

        @Volatile
        private var cached: TasteProfile? = null

        suspend fun profile(
            forceRefresh: Boolean = false,
            maxAgeMs: Long = DefaultMaxAgeMs,
        ): Result<TasteProfile> =
            mutex.withLock {
                val existing = cached
                val nowMs = System.currentTimeMillis()
                if (!forceRefresh && existing != null && nowMs - existing.generatedAtMs < maxAgeMs) {
                    Result.success(existing)
                } else {
                    try {
                        val input = withContext(Dispatchers.IO) { loadInput(nowMs) }
                        val built =
                            withContext(Dispatchers.Default) {
                                TasteProfileBuilder.build(input, LocalDateTime.now())
                            }
                        cached = built
                        Result.success(built)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        reportException(error)
                        Result.failure(error)
                    }
                }
            }

        private suspend fun loadInput(nowMs: Long): TasteInput {
            val events = database.events(limit = MaxEvents, offset = 0)
            val plays = events.mapNotNull { event -> event.toPlay() }
            val feedback = loadFeedback(events, nowMs)
            val library = database.offlineRecommendationCandidates(LibraryBootstrapLimit).map { song -> song.toLibrarySong() }
            return TasteInput(plays = plays, feedback = feedback, library = library)
        }

        private suspend fun loadFeedback(
            events: List<EventWithSong>,
            nowMs: Long,
        ): List<TasteFeedback> {
            val signals =
                database
                    .recentRecommendationSignals(MaxSignals)
                    .filter { signal -> signal.type in FeedbackTypes }
            if (signals.isEmpty()) return emptyList()

            val artistIdsBySong = HashMap<String, List<String>>()
            events.forEach { event ->
                artistIdsBySong[event.song.id] = event.song.artists.unblockedIds()
            }
            signals
                .map { signal -> signal.songId }
                .distinct()
                .filter { songId -> songId !in artistIdsBySong }
                .chunked(SongLookupChunk)
                .forEach { chunk ->
                    database.getSongsByIds(chunk).forEach { song ->
                        artistIdsBySong[song.id] = song.artists.unblockedIds()
                    }
                }

            return signals.mapNotNull { signal ->
                val artistIds = artistIdsBySong[signal.songId]?.takeIf { ids -> ids.isNotEmpty() }
                    ?: return@mapNotNull null
                TasteFeedback(
                    artistIds = artistIds,
                    kind =
                        if (signal.type == RecommendationSignalType.Favorite.name) {
                            TasteFeedbackKind.Favorite
                        } else {
                            TasteFeedbackKind.Dislike
                        },
                    ageDays = (nowMs - signal.occurredAtMs).coerceAtLeast(0L) / MillisPerDay,
                )
            }
        }

        private fun EventWithSong.toPlay(): TastePlay? {
            val artists = song.artists.unblocked()
            if (song.artists.isNotEmpty() && artists.isEmpty()) return null
            val entity = song.song
            return TastePlay(
                songId = entity.id,
                artists = artists,
                timestamp = event.timestamp,
                playTimeMs = event.playTime,
                durationMs = entity.duration.secondsToMillis(),
                releaseYear = entity.year,
                explicit = entity.explicit,
                liked = entity.liked,
            )
        }

        private fun Song.toLibrarySong(): TasteLibrarySong =
            TasteLibrarySong(
                artists = artists.unblocked(),
                liked = song.liked,
                totalPlayTimeMs = song.totalPlayTime,
                durationMs = song.duration.secondsToMillis(),
                releaseYear = song.year,
                explicit = song.explicit,
            )

        private fun List<ArtistEntity>.unblocked(): List<TasteArtistRef> =
            filter { artist -> artist.blockedAt == null }
                .map { artist -> TasteArtistRef(id = artist.id, name = artist.name, thumbnailUrl = artist.thumbnailUrl) }

        private fun List<ArtistEntity>.unblockedIds(): List<String> =
            filter { artist -> artist.blockedAt == null }.map { artist -> artist.id }

        private fun Int.secondsToMillis(): Long = if (this > 0) this * 1_000L else 0L

        private companion object {
            const val DefaultMaxAgeMs = 10L * 60L * 1000L
            const val MaxEvents = 4_000
            const val MaxSignals = 2_000
            const val LibraryBootstrapLimit = 300
            const val SongLookupChunk = 400
            const val MillisPerDay = 86_400_000f
            val FeedbackTypes =
                setOf(
                    RecommendationSignalType.Favorite.name,
                    RecommendationSignalType.Dislike.name,
                )
        }
    }
