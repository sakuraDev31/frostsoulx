/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.recommendation

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.vxs.frostsoulx.db.MusicDatabase
import dev.vxs.frostsoulx.db.entities.RecommendationSignalEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendationBehaviorTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending =
        Channel<RecommendationSignalEntity>(
            capacity = BufferCapacity,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private val sessionId = AtomicLong(System.currentTimeMillis())
    private val meaningfulPlaySignals = AtomicLong(0L)
    private val budget = RecommendationBudget()

    init {
        scope.launch {
            val batch = ArrayList<RecommendationSignalEntity>(BatchSize)
            while (isActive) {
                val first = pending.receive()
                batch += first
                while (batch.size < BatchSize) {
                    val next = pending.tryReceive().getOrNull() ?: break
                    batch += next
                }
                persistSignals(batch)
                batch.clear()
                pruneIfNeeded()
            }
        }
    }

    fun record(
        songId: String,
        type: RecommendationSignalType,
        positionMs: Long = 0L,
        listenedMs: Long = 0L,
        context: RecommendationContext = DefaultContext,
        occurredAtMs: Long = System.currentTimeMillis(),
    ) {
        val id = songId.trim()
        if (id.isEmpty()) return
        pending.trySend(
            RecommendationSignalEntity(
                songId = id,
                type = type.name,
                occurredAtMs = occurredAtMs,
                positionMs = positionMs.coerceAtLeast(0L),
                listenedMs = listenedMs.coerceAtLeast(0L),
                sessionId = sessionId.get(),
                contextFlags = context.flags(),
            ),
        )
        if (type == RecommendationSignalType.Play ||
            type == RecommendationSignalType.Complete ||
            type == RecommendationSignalType.Replay
        ) {
            val count = meaningfulPlaySignals.incrementAndGet()
            if (count % MixRefreshPlayThreshold == 0L) {
                OfflineRecommendationScheduler.enqueue(context)
            }
        }
    }

    fun beginNewSession() {
        sessionId.set(System.currentTimeMillis())
    }

    private suspend fun persistSignals(signals: List<RecommendationSignalEntity>) {
        if (signals.isEmpty()) return
        runCatching {
            val existingSongIds =
                database
                    .getSongsByIds(signals.mapTo(ArrayList(signals.size)) { it.songId })
                    .asSequence()
                    .map { it.id }
                    .toHashSet()
            val validSignals = signals.filter { it.songId in existingSongIds }
            if (validSignals.isNotEmpty()) {
                database.insertRecommendationSignals(validSignals)
            }
        }.onFailure { error ->
            Log.w(LogTag, "Skipping noncritical recommendation signal persistence", error)
        }
    }

    private suspend fun pruneIfNeeded() {
        if (database.recommendationSignalCount() <= budget.maximumSignalsRetained) return
        val cutoff = database.recommendationSignalCutoff(budget.maximumSignalsRetained - 1) ?: return
        database.pruneRecommendationSignals(cutoff)
    }

    private companion object {
        const val LogTag = "RecommendationTracker"
        const val BufferCapacity = 512
        const val BatchSize = 32
        const val MixRefreshPlayThreshold = 20L
        val DefaultContext =
            RecommendationContext(
                hourOfDay = 12,
                dayOfWeek = 1,
                isHeadphones = false,
                isBluetooth = false,
                isCharging = false,
                isOffline = true,
            )
    }
}
