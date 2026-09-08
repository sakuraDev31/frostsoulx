/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.playback.core

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import dev.vxs.frostsoulx.extensions.metadata
import dev.vxs.frostsoulx.extensions.toMediaItem
import dev.vxs.frostsoulx.models.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

/**
 * Serializes media mutations at the Media3 boundary. It deliberately does not own audio focus,
 * MediaSession, downloads, or casting; those remain platform adapters around the same Player.
 */
class Media3PlaybackCore(
    @Volatile private var player: Player,
    private val scope: CoroutineScope,
    private val snapshotRepository: PlaybackSnapshotRepository,
    private val queueTitleProvider: () -> String? = { null },
    private val historyLimit: Int = DefaultHistoryLimit,
) : Player.Listener {
    private val mutationMutex = Mutex()
    private val history = ArrayDeque<QueueUndoToken<MediaItem>>()
    private var snapshotJob: Job? = null
    private val _state = MutableStateFlow(projectState())

    val state: StateFlow<PlaybackCoreState> = _state.asStateFlow()
    val canUndoQueueMutation: Boolean
        get() = history.isNotEmpty()

    init {
        player.addListener(this)
    }

    suspend fun insertNext(items: List<MediaItem>): Boolean =
        mutateInsert(items, QueueInsertionMode.Next)

    suspend fun insertLater(items: List<MediaItem>): Boolean =
        mutateInsert(items, QueueInsertionMode.Later)

    suspend fun appendSmartQueue(
        candidates: List<MediaItem>,
        maxAdditionalItems: Int = DefaultSmartQueueLimit,
    ): Int =
        mutationMutex.withLock {
            val existing = currentItems()
            val additions =
                SmartQueuePlanner.appendDistinct(
                    current = existing,
                    candidates = candidates,
                    maxAdditionalItems = maxAdditionalItems,
                    keyOf = MediaItem::mediaId,
                )
            if (additions.isEmpty()) return@withLock 0
            recordSnapshot(QueueMutationSnapshot(existing, player.currentMediaItemIndex))
            player.addMediaItems(additions)
            player.prepare()
            publishStateAndSnapshot()
            additions.size
        }

    suspend fun move(fromIndex: Int, toIndex: Int): Boolean =
        mutationMutex.withLock {
            val items = currentItems()
            val result = QueueMutationPlanner.move(items, player.currentMediaItemIndex, fromIndex, toIndex)
            val token = result.undoToken ?: return@withLock false
            recordUndo(token)
            player.moveMediaItem(fromIndex, toIndex)
            publishStateAndSnapshot()
            true
        }

    suspend fun remove(indices: Collection<Int>): Boolean =
        mutationMutex.withLock {
            val items = currentItems()
            val result = QueueMutationPlanner.remove(items, player.currentMediaItemIndex, indices)
            val token = result.undoToken ?: return@withLock false
            recordUndo(token)
            indices
                .filter { it in items.indices }
                .distinct()
                .sortedDescending()
                .forEach(player::removeMediaItem)
            publishStateAndSnapshot()
            true
        }

    suspend fun undoLastQueueMutation(): Boolean =
        mutationMutex.withLock {
            val token = history.removeLastOrNull() ?: return@withLock false
            restoreQueue(token.before)
            true
        }

    suspend fun restoreLatestSnapshot(): Boolean =
        mutationMutex.withLock {
            if (player.mediaItemCount > 0) return@withLock false
            val snapshot = snapshotRepository.latest() ?: return@withLock false
            val restoredItems = snapshot.items.map { it.toMediaItem() }
            if (restoredItems.isEmpty()) return@withLock false
            player.repeatMode = snapshot.repeatMode
            player.shuffleModeEnabled = snapshot.shuffleEnabled
            player.playbackParameters = PlaybackParameters(snapshot.playbackSpeed, snapshot.playbackPitch)
            player.setMediaItems(
                restoredItems,
                snapshot.currentIndex.coerceIn(0, restoredItems.lastIndex),
                snapshot.currentPositionMs.coerceAtLeast(0L),
            )
            player.prepare()
            player.playWhenReady = false
            _state.value = projectState()
            true
        }

    fun setRepeatMode(mode: Int) {
        player.repeatMode = mode
        publishStateAndSnapshot()
    }

    fun setShuffleEnabled(enabled: Boolean) {
        player.shuffleModeEnabled = enabled
        publishStateAndSnapshot()
    }

    /**
     * Media3 uses the Sonic processor for speed adjustment. Supplying pitch=1f preserves the
     * original pitch, which is the expected music-player behavior for tempo changes.
     */
    fun setPlaybackSpeed(
        speed: Float,
        preservePitch: Boolean = true,
    ) {
        val safeSpeed = speed.coerceIn(MinPlaybackSpeed, MaxPlaybackSpeed)
        val pitch = if (preservePitch) 1f else player.playbackParameters.pitch
        player.playbackParameters = PlaybackParameters(safeSpeed, pitch)
        publishStateAndSnapshot()
    }

    fun setPitch(pitch: Float) {
        val parameters = player.playbackParameters
        player.playbackParameters = PlaybackParameters(parameters.speed, pitch.coerceIn(MinPitch, MaxPitch))
        publishStateAndSnapshot()
    }

    fun replacePlayer(newPlayer: Player) {
        val previous = player
        if (previous === newPlayer) return
        previous.removeListener(this)
        player = newPlayer
        newPlayer.addListener(this)
        _state.value = projectState()
    }

    fun close() {
        snapshotJob?.cancel()
        snapshotJob = null
        player.removeListener(this)
        history.clear()
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) = publishStateAndSnapshot()

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) = publishStateAndSnapshot()

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) = publishStateAndSnapshot()

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = publishStateAndSnapshot()

    override fun onRepeatModeChanged(repeatMode: Int) = publishStateAndSnapshot()

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = publishStateAndSnapshot()

    private suspend fun mutateInsert(
        incoming: List<MediaItem>,
        mode: QueueInsertionMode,
    ): Boolean =
        mutationMutex.withLock {
            val existing = currentItems()
            val result = QueueMutationPlanner.insert(existing, player.currentMediaItemIndex, incoming, mode)
            val token = result.undoToken ?: return@withLock false
            recordUndo(token)
            val insertionIndex =
                when (mode) {
                    QueueInsertionMode.Next -> (player.currentMediaItemIndex + 1).coerceIn(0, player.mediaItemCount)
                    QueueInsertionMode.Later -> player.mediaItemCount
                }
            player.addMediaItems(insertionIndex, incoming)
            player.prepare()
            publishStateAndSnapshot()
            true
        }

    private fun restoreQueue(snapshot: QueueMutationSnapshot<MediaItem>) {
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val playWhenReady = player.playWhenReady
        if (snapshot.items.isEmpty()) {
            player.clearMediaItems()
        } else {
            player.setMediaItems(
                snapshot.items,
                snapshot.currentIndex.coerceIn(0, snapshot.items.lastIndex),
                currentPosition,
            )
            player.prepare()
            player.playWhenReady = playWhenReady
        }
        publishStateAndSnapshot()
    }

    private fun recordSnapshot(snapshot: QueueMutationSnapshot<MediaItem>) {
        recordUndo(QueueUndoToken(snapshot, QueueMutationReason.Replace))
    }

    private fun recordUndo(token: QueueUndoToken<MediaItem>) {
        while (history.size >= historyLimit.coerceAtLeast(1)) history.removeFirst()
        history.addLast(token)
    }

    private fun currentItems(): List<MediaItem> = List(player.mediaItemCount) { player.getMediaItemAt(it) }

    private fun projectState(): PlaybackCoreState {
        val parameters = player.playbackParameters
        return PlaybackCoreState(
            mediaItemCount = player.mediaItemCount,
            currentIndex = player.currentMediaItemIndex,
            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
            isPlaying = player.isPlaying,
            repeatMode = player.repeatMode,
            shuffleEnabled = player.shuffleModeEnabled,
            playbackSpeed = parameters.speed,
            playbackPitch = parameters.pitch,
        )
    }

    private fun publishStateAndSnapshot() {
        // Media3 player reads must remain on the application thread. Capture the immutable
        // snapshot before switching to IO; only persistence is dispatched off the player thread.
        val snapshot = buildSnapshot()
        _state.value = projectState()
        snapshotJob?.cancel()
        snapshotJob =
            scope.launch(Dispatchers.IO) {
                snapshotRepository.save(snapshot)
            }
    }

    private fun buildSnapshot(): PlaybackSessionSnapshot =
        PlaybackSessionSnapshot(
            queueTitle = queueTitleProvider(),
            items =
                currentItems().mapNotNull { item ->
                    item.metadata?.let { metadata ->
                        PlaybackSnapshotItem(
                            mediaId = metadata.id.ifBlank { item.mediaId },
                            title = metadata.title,
                            artistNames = metadata.artists.map { it.name },
                            artworkUrl = metadata.thumbnailUrl,
                            durationSeconds = metadata.duration,
                        )
                    }
                },
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
            playWhenReady = player.playWhenReady,
            repeatMode = player.repeatMode,
            shuffleEnabled = player.shuffleModeEnabled,
            playbackSpeed = player.playbackParameters.speed,
            playbackPitch = player.playbackParameters.pitch,
        )

    private fun PlaybackSnapshotItem.toMediaItem(): MediaItem =
        MediaMetadata(
            id = mediaId,
            title = title,
            artists = artistNames.map { artistName -> MediaMetadata.Artist(id = null, name = artistName) },
            duration = durationSeconds,
            thumbnailUrl = artworkUrl,
        ).toMediaItem()

    private companion object {
        const val DefaultHistoryLimit = 48
        const val DefaultSmartQueueLimit = 40
        const val MinPlaybackSpeed = 0.25f
        const val MaxPlaybackSpeed = 3.0f
        const val MinPitch = 0.5f
        const val MaxPitch = 2.0f
    }
}

/** Resolves ReplayGain-style decibel values to a stable linear volume multiplier. */
object ReplayGainPolicy {
    fun gainMultiplier(
        trackGainDb: Float?,
        preampDb: Float = 0f,
        peak: Float? = null,
    ): Float {
        val gain = (trackGainDb ?: 0f) + preampDb
        val unclamped = Math.pow(10.0, (gain / 20f).toDouble()).toFloat()
        val peakSafe = peak?.takeIf { it > 0f }?.let { 1f / it } ?: 1f
        return unclamped.coerceAtMost(peakSafe).coerceIn(0f, 4f)
    }

    fun displayDb(multiplier: Float): Int = (20f * kotlin.math.log10(multiplier.coerceAtLeast(0.0001f))).roundToInt()
}
