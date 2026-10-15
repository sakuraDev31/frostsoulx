/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.utils

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import dev.vxs.frostsoulx.constants.HISTORY_DURATION_LEGACY_FLOAT_KEY
import dev.vxs.frostsoulx.constants.HISTORY_DURATION_MAX
import dev.vxs.frostsoulx.constants.HISTORY_DURATION_MIN
import dev.vxs.frostsoulx.constants.HistoryDuration
import dev.vxs.frostsoulx.constants.UpdateChannel
import dev.vxs.frostsoulx.constants.UpdateChannelKey
import dev.vxs.frostsoulx.extensions.toEnum
import kotlin.properties.ReadOnlyProperty

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { error ->
        reportException(error)
        emptyPreferences() // Deliberate recovery of an unreadable protobuf; never treat IO errors as corruption.
    },
    produceMigrations = { _ ->
        listOf(
            object : DataMigration<Preferences> {
                override suspend fun shouldMigrate(currentData: Preferences): Boolean =
                    currentData[HISTORY_DURATION_LEGACY_FLOAT_KEY] != null &&
                        currentData[HistoryDuration] == null

                override suspend fun migrate(currentData: Preferences): Preferences =
                    currentData.toMutablePreferences().apply {
                        val oldFloat = currentData[HISTORY_DURATION_LEGACY_FLOAT_KEY]
                        if (oldFloat != null) {
                            this[HistoryDuration] =
                                oldFloat
                                    .toInt()
                                    .coerceIn(HISTORY_DURATION_MIN, HISTORY_DURATION_MAX)
                            this.remove(HISTORY_DURATION_LEGACY_FLOAT_KEY)
                        }
                    }

                override suspend fun cleanUp() {}
            },
            object : DataMigration<Preferences> {
                override suspend fun shouldMigrate(currentData: Preferences): Boolean =
                    when (currentData[UpdateChannelKey]) {
                        "NIGHTLY", "DAILY_NIGHTLY" -> true
                        else -> false
                    }

                override suspend fun migrate(currentData: Preferences): Preferences =
                    currentData.toMutablePreferences().apply {
                        this[UpdateChannelKey] = UpdateChannel.CANARY.name
                    }

                override suspend fun cleanUp() {}
            },
        )
    },
)

/** Retry transient storage failures without converting a failed load into missing preferences. */
fun DataStore<Preferences>.observablePreferences(): Flow<Preferences> = data.retryWhen { error, attempt ->
    if (error is IOException) {
        reportException(error)
        delay((250L * (attempt + 1).coerceAtMost(20)).coerceAtMost(5_000L))
        true
    } else {
        false
    }
}

object PreferenceStore {
    enum class LoadState { NOT_LOADED, LOADED, FAILED }
    private val _loadState = MutableStateFlow(LoadState.NOT_LOADED)
    val loadState = _loadState.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _prefs = MutableStateFlow<Preferences?>(null)

    @Volatile private var started = false

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
            scope.launch {
                try {
                    context.applicationContext.dataStore.data.retryWhen { error, attempt ->
                        _loadState.value = LoadState.FAILED
                        if (error is IOException) {
                            reportException(error)
                            delay((250L * (attempt + 1).coerceAtMost(20)).coerceAtMost(5_000L))
                            true
                        } else false
                    }.collect { preferences ->
                        _prefs.value = preferences
                        _loadState.value = LoadState.LOADED
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    _loadState.value = LoadState.FAILED
                    reportException(error)
                } finally {
                    synchronized(this@PreferenceStore) { started = false }
                }
            }
        }
    }

    fun <T> get(key: Preferences.Key<T>): T? = _prefs.value?.get(key)

    fun launchEdit(
        dataStore: DataStore<Preferences>,
        block: MutablePreferences.() -> Unit,
    ) {
        scope.launch {
            try {
                dataStore.edit { prefs -> prefs.block() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                reportException(error)
            }
        }
    }
}

operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? =
    PreferenceStore.get(key)
        ?: if (Looper.getMainLooper().thread == Thread.currentThread()) {
            null
        } else {
            runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(1500) {
                    try { data.first()[key] } catch (error: IOException) {
                        reportException(error)
                        null
                    }
                }
            }
        }

fun <T> DataStore<Preferences>.get(
    key: Preferences.Key<T>,
    defaultValue: T,
): T =
    PreferenceStore.get(key)
        ?: if (Looper.getMainLooper().thread == Thread.currentThread()) {
            defaultValue
        } else {
            runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(1500) {
                    try { data.first()[key] } catch (error: IOException) {
                        reportException(error)
                        null
                    }
                } ?: defaultValue
            }
        }

suspend fun <T> DataStore<Preferences>.getAsync(key: Preferences.Key<T>): T? = data.first()[key]

suspend fun <T> DataStore<Preferences>.getAsync(
    key: Preferences.Key<T>,
    defaultValue: T,
): T = data.first()[key] ?: defaultValue

fun <T> preference(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key] ?: defaultValue }

inline fun <reified T : Enum<T>> enumPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
) = ReadOnlyProperty<Any?, T> { _, _ -> context.dataStore[key].toEnum(defaultValue) }

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current

    val state =
        remember {
            context.dataStore.observablePreferences()
                .catch { reportException(it) }
                .map { it[key] ?: defaultValue }
                .distinctUntilChanged()
        }.collectAsState(defaultValue)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    PreferenceStore.launchEdit(context.dataStore) {
                        this[key] = value
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current

    val state =
        remember {
            context.dataStore.observablePreferences()
                .catch { reportException(it) }
                .map { it[key].toEnum(defaultValue = defaultValue) }
                .distinctUntilChanged()
        }.collectAsState(defaultValue)

    return remember {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    PreferenceStore.launchEdit(context.dataStore) {
                        this[key] = value.name
                    }
                }

            override fun component1() = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
