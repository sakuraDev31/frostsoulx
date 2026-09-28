/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.recommendation

import androidx.compose.runtime.Immutable
import dev.vxs.frostsoulx.models.MediaMetadata

/** Runtime budgets keep recommendation work out of the audio and UI critical paths. */
@Immutable
data class RecommendationBudget(
    val embeddingDimension: Int = 64,
    val candidateLimit: Int = 600,
    val rankLimit: Int = 120,
    val shelfSize: Int = 20,
    val maximumSignalsPerRefresh: Int = 2_000,
    val maximumSignalsRetained: Int = 100_000,
    val maximumIndexItems: Int = 50_000,
    val backgroundBatchSize: Int = 96,
) {
    init {
        require(embeddingDimension in 16..384)
        require(candidateLimit in 50..2_000)
        require(rankLimit in 20..candidateLimit)
        require(shelfSize in 5..50)
        require(maximumSignalsPerRefresh in 100..10_000)
        require(maximumSignalsRetained in 10_000..100_000)
        require(maximumIndexItems in 1_000..50_000)
        require(backgroundBatchSize in 16..256)
    }
}

enum class RecommendationSignalType {
    Play,
    Progress,
    Pause,
    Resume,
    Skip,
    Complete,
    Replay,
    Seek,
    Favorite,
    Unlike,
    Dislike,
    Search,
    QueueInsert,
    QueueRemove,
    Saved,
    AddedToPlaylist,
    RemovedFromPlaylist,
    RecommendationShown,
    RecommendationPlayed,
    RecommendationSkipped,
}

enum class TasteProfileKind {
    LongTerm,
    Weekly,
    Daily,
    Session,
    Discovery,
    Context,
}

enum class RecommendationShelfType(
    val title: String,
    val description: String,
) {
    DailyMix("Daily Mix", "A local mix shaped by your recent listening"),
    ForgottenGems("Forgotten Gems", "Favorites that deserve another listen"),
    ContinueListening("Continue Listening", "Return to music already in your flow"),
    BecauseYouLike("Because You Like", "Tracks with familiar signals and fresh variety"),
    RecentRediscovery("Recently Rediscovered", "Music returning to your rotation"),
    DeepCuts("Deep Cuts", "Less-played songs with a strong taste match"),
    RandomDiscovery("Random Discovery", "A diverse local surprise set"),
}

@Immutable
data class RecommendationContext(
    val hourOfDay: Int,
    val dayOfWeek: Int,
    val isHeadphones: Boolean,
    val isBluetooth: Boolean,
    val isCharging: Boolean,
    val isOffline: Boolean,
    val currentSongId: String? = null,
    val recentSongIds: List<String> = emptyList(),
    val sessionState: SessionState? = null,
    val userProfile: UserTasteProfile? = null,
    val temporalModel: TemporalModel? = null,
    val queueState: RecommendationQueueState = RecommendationQueueState(),
    val playbackPositionMs: Long = 0L,
    val autoplayEnabled: Boolean = false,
    val explorationLevel: Float = 0.5f,
) {
    init {
        require(playbackPositionMs >= 0L)
        require(explorationLevel.isFinite() && explorationLevel in 0f..1f)
    }
    companion object {
        const val HeadphonesBit = 1 shl 0
        const val BluetoothBit = 1 shl 1
        const val ChargingBit = 1 shl 2
        const val OfflineBit = 1 shl 3

        fun fromFlags(
            hourOfDay: Int,
            dayOfWeek: Int,
            flags: Int,
        ): RecommendationContext =
            RecommendationContext(
                hourOfDay = hourOfDay.coerceIn(0, 23),
                dayOfWeek = dayOfWeek.coerceIn(1, 7),
                isHeadphones = flags and HeadphonesBit != 0,
                isBluetooth = flags and BluetoothBit != 0,
                isCharging = flags and ChargingBit != 0,
                isOffline = flags and OfflineBit != 0,
            )
    }

    fun flags(): Int =
        (if (isHeadphones) HeadphonesBit else 0) or
            (if (isBluetooth) BluetoothBit else 0) or
            (if (isCharging) ChargingBit else 0) or
            (if (isOffline) OfflineBit else 0)
}

/** Queue facts visible to recommendation code; it never owns or mutates the player queue. */
@Immutable
data class RecommendationQueueState(
    val explicitSongIds: List<String> = emptyList(),
    val generatedSongIds: List<String> = emptyList(),
    val isLocked: Boolean = false,
    val loopQueue: Boolean = false,
    val loopOne: Boolean = false,
)

@Immutable
data class RecommendationExplanation(
    val score: Float,
    val similarity: Float,
    val novelty: Float,
    val context: Float,
    val replayProbability: Float,
    val skipProbability: Float,
    val reason: String,
    /** Session-derived "usually played after the last track(s)" affinity, in [0, 1]. */
    val sequenceAffinity: Float = 0f,
)

@Immutable
data class OfflineRecommendation(
    val track: MediaMetadata,
    val shelf: RecommendationShelfType,
    val explanation: RecommendationExplanation,
)

@Immutable
data class QuantizedVector(
    val dimension: Int,
    val values: ByteArray,
    val scale: Float,
    val norm: Float,
) {
    init {
        require(dimension > 0)
        require(values.size == dimension)
        require(scale.isFinite() && scale >= 0f)
        require(norm.isFinite() && norm >= 0f)
    }
}
