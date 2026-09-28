/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.playback.core

import androidx.compose.runtime.Immutable
import java.io.Serializable
import java.util.Random

@Immutable
data class PlaybackCoreState(
    val mediaItemCount: Int = 0,
    val currentIndex: Int = -1,
    val currentPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val repeatMode: Int = 0,
    val shuffleEnabled: Boolean = false,
    val playbackSpeed: Float = 1f,
    val playbackPitch: Float = 1f,
    val queueEditLocked: Boolean = false,
)

@Immutable
data class QueueMutationSnapshot<T>(
    val items: List<T>,
    val currentIndex: Int,
)

@Immutable
data class QueueUndoToken<T>(
    val before: QueueMutationSnapshot<T>,
    val reason: QueueMutationReason,
)

enum class QueueMutationReason {
    InsertNext,
    InsertLater,
    Move,
    Remove,
    Replace,
}

@Immutable
data class QueueMutationResult<T>(
    val items: List<T>,
    val currentIndex: Int,
    val undoToken: QueueUndoToken<T>?,
) {
    val changed: Boolean
        get() = undoToken != null
}

enum class QueueInsertionMode {
    Next,
    Later,
}

/**
 * Pure queue editing rules. The service remains the owner of Media3 mutations, while this class
 * makes queue ordering, selection, multi-delete, and undo behavior deterministic and testable.
 */
object QueueMutationPlanner {
    fun <T> insert(
        items: List<T>,
        currentIndex: Int,
        incoming: List<T>,
        mode: QueueInsertionMode,
    ): QueueMutationResult<T> {
        if (incoming.isEmpty()) return unchanged(items, currentIndex)
        val safeCurrentIndex = currentIndex.coerceIn(-1, items.lastIndex)
        val insertionIndex =
            when {
                items.isEmpty() -> 0
                mode == QueueInsertionMode.Next -> (safeCurrentIndex + 1).coerceIn(0, items.size)
                else -> items.size
            }
        val result = buildList(items.size + incoming.size) {
            addAll(items.subList(0, insertionIndex))
            addAll(incoming)
            addAll(items.subList(insertionIndex, items.size))
        }
        val resultCurrentIndex =
            when {
                items.isEmpty() -> 0
                safeCurrentIndex >= insertionIndex -> safeCurrentIndex + incoming.size
                else -> safeCurrentIndex
            }
        return QueueMutationResult(
            items = result,
            currentIndex = resultCurrentIndex,
            undoToken = QueueUndoToken(QueueMutationSnapshot(items, safeCurrentIndex), reasonFor(mode)),
        )
    }

    fun <T> move(
        items: List<T>,
        currentIndex: Int,
        fromIndex: Int,
        toIndex: Int,
    ): QueueMutationResult<T> {
        if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) {
            return unchanged(items, currentIndex)
        }
        val mutable = items.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        val safeCurrentIndex = currentIndex.coerceIn(-1, items.lastIndex)
        val newCurrentIndex =
            when {
                safeCurrentIndex == fromIndex -> toIndex
                fromIndex < safeCurrentIndex && toIndex >= safeCurrentIndex -> safeCurrentIndex - 1
                fromIndex > safeCurrentIndex && toIndex <= safeCurrentIndex -> safeCurrentIndex + 1
                else -> safeCurrentIndex
            }
        return QueueMutationResult(
            items = mutable,
            currentIndex = newCurrentIndex,
            undoToken = QueueUndoToken(QueueMutationSnapshot(items, safeCurrentIndex), QueueMutationReason.Move),
        )
    }

    fun <T> remove(
        items: List<T>,
        currentIndex: Int,
        indices: Collection<Int>,
    ): QueueMutationResult<T> {
        val targets = indices.filter { it in items.indices }.toSortedSet()
        if (targets.isEmpty()) return unchanged(items, currentIndex)
        val safeCurrentIndex = currentIndex.coerceIn(-1, items.lastIndex)
        val result = items.filterIndexed { index, _ -> index !in targets }
        val removedBeforeCurrent = targets.count { it < safeCurrentIndex }
        val nextCurrentIndex =
            if (result.isEmpty()) {
                -1
            } else {
                (safeCurrentIndex - removedBeforeCurrent).coerceIn(0, result.lastIndex)
            }
        return QueueMutationResult(
            items = result,
            currentIndex = nextCurrentIndex,
            undoToken = QueueUndoToken(QueueMutationSnapshot(items, safeCurrentIndex), QueueMutationReason.Remove),
        )
    }

    fun <T> undo(token: QueueUndoToken<T>): QueueMutationResult<T> =
        QueueMutationResult(
            items = token.before.items,
            currentIndex = token.before.currentIndex,
            undoToken = null,
        )

    private fun <T> unchanged(
        items: List<T>,
        currentIndex: Int,
    ): QueueMutationResult<T> =
        QueueMutationResult(
            items = items,
            currentIndex = currentIndex.coerceIn(-1, items.lastIndex),
            undoToken = null,
        )

    private fun reasonFor(mode: QueueInsertionMode): QueueMutationReason =
        if (mode == QueueInsertionMode.Next) QueueMutationReason.InsertNext else QueueMutationReason.InsertLater
}

/** A deterministic current-first shuffle order, stable for a given seed and safe for restoration. */
object PlaybackShuffleEngine {
    fun currentFirstOrder(
        itemCount: Int,
        currentIndex: Int,
        seed: Long,
    ): IntArray {
        if (itemCount <= 0) return IntArray(0)
        val safeCurrent = currentIndex.coerceIn(0, itemCount - 1)
        val remaining = (0 until itemCount).filterNot { it == safeCurrent }.toMutableList()
        val random = Random(seed)
        for (index in remaining.lastIndex downTo 1) {
            val swapIndex = random.nextInt(index + 1)
            val value = remaining[index]
            remaining[index] = remaining[swapIndex]
            remaining[swapIndex] = value
        }
        return IntArray(itemCount).also { order ->
            order[0] = safeCurrent
            remaining.forEachIndexed { index, value -> order[index + 1] = value }
        }
    }
}

/**
 * Removes invalid and duplicate candidates before a smart queue is appended. The caller supplies
 * an identity key so local, remote, and cast items can share the same policy.
 */
object SmartQueuePlanner {
    fun <T> appendDistinct(
        current: List<T>,
        candidates: List<T>,
        maxAdditionalItems: Int,
        keyOf: (T) -> String,
    ): List<T> {
        if (maxAdditionalItems <= 0 || candidates.isEmpty()) return emptyList()
        val known = current.mapTo(linkedSetOf()) { keyOf(it) }.filterTo(linkedSetOf()) { it.isNotBlank() }
        return buildList(minOf(candidates.size, maxAdditionalItems)) {
            for (candidate in candidates) {
                if (size >= maxAdditionalItems) break
                val key = keyOf(candidate)
                if (key.isBlank() || !known.add(key)) continue
                add(candidate)
            }
        }
    }
}

@Immutable
data class PlaybackSessionSnapshot(
    val queueTitle: String?,
    val items: List<PlaybackSnapshotItem>,
    val currentIndex: Int,
    val currentPositionMs: Long,
    val playWhenReady: Boolean,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    val playbackSpeed: Float,
    val playbackPitch: Float,
    val capturedAtEpochMs: Long = System.currentTimeMillis(),
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

@Immutable
data class PlaybackSnapshotItem(
    val mediaId: String,
    val title: String,
    val artistNames: List<String>,
    val artworkUrl: String?,
    val durationSeconds: Int,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
