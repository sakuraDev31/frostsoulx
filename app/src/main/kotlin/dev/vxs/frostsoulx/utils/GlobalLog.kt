/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.utils

import dev.vxs.frostsoulx.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

data class LogEntry(
    val time: Long,
    val level: Int,
    val tag: String?,
    val message: String,
)

object GlobalLog {
    private const val MAX_ENTRIES = 500
    @Volatile var verboseEnabled = BuildConfig.DEBUG
    private val buffer = ArrayDeque<LogEntry>()
    private var publicationPending = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun append(
        level: Int,
        tag: String?,
        message: String,
    ) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        synchronized(buffer) {
            if (buffer.size == MAX_ENTRIES) buffer.removeFirst()
            buffer.addLast(entry)
            if (publicationPending) return
            publicationPending = true
        }
        scope.launch {
            delay(100L)
            synchronized(buffer) {
                _logs.value = buffer.toList()
                publicationPending = false
            }
        }
    }

    fun clear() {
        synchronized(buffer) { buffer.clear(); _logs.value = emptyList() }
    }

    fun format(entry: LogEntry): String {
        val ts = synchronized(timeFormat) { timeFormat.format(Date(entry.time)) }
        val lvl =
            when (entry.level) {
                android.util.Log.VERBOSE -> "V"
                android.util.Log.DEBUG -> "D"
                android.util.Log.INFO -> "I"
                android.util.Log.WARN -> "W"
                android.util.Log.ERROR -> "E"
                else -> "?"
            }
        val tag = entry.tag ?: ""
        return "[$ts] $lvl/$tag: ${entry.message}"
    }
}

/** Timber Tree that forwards logs to GlobalLog */
class GlobalLogTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean =
        GlobalLog.verboseEnabled || priority >= android.util.Log.WARN
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        try {
            val final = if (t != null) "$message\n$t" else message
            GlobalLog.append(priority, tag, final)
        } catch (_: Exception) {
            // swallow
        }
    }
}
