/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.viewmodels

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.backup.BackupArchiveCategory
import dev.vxs.frostsoulx.backup.BackupArchiveRepository
import dev.vxs.frostsoulx.backup.BackupArchiveStep
import dev.vxs.frostsoulx.backup.CreateBackupUseCase
import dev.vxs.frostsoulx.backup.ObserveScheduledBackupSettingsUseCase
import dev.vxs.frostsoulx.backup.ScheduledBackupFrequency
import dev.vxs.frostsoulx.backup.ScheduledBackupSettings
import dev.vxs.frostsoulx.backup.UpdateScheduledBackupUseCase
import dev.vxs.frostsoulx.db.InternalDatabase
import dev.vxs.frostsoulx.db.MusicDatabase
import dev.vxs.frostsoulx.db.entities.ArtistEntity
import dev.vxs.frostsoulx.db.entities.Song
import dev.vxs.frostsoulx.db.entities.SongEntity
import dev.vxs.frostsoulx.extensions.zipInputStream
import dev.vxs.frostsoulx.utils.dataStore
import dev.vxs.frostsoulx.utils.reportException
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStreamReader
import java.io.PushbackReader
import java.io.Reader
import java.io.StringReader
import java.time.LocalDate
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

data class BackupRestoreProgressUi(
    val title: String,
    val step: String,
    val percent: Int,
    val indeterminate: Boolean,
)

enum class BackupCategory {
    LIBRARY,
    ACCOUNT,
    SETTINGS,
}

data class BackupValidationResult(
    val isValid: Boolean,
    val availableCategories: Set<BackupCategory>,
    val errorMessage: String?,
)

sealed interface ScheduledBackupScreenState {
    data object Loading : ScheduledBackupScreenState

    @Immutable
    data class Success(
        val data: ScheduledBackupUiData,
    ) : ScheduledBackupScreenState

    data object Empty : ScheduledBackupScreenState

    data class Error(
        @StringRes val messageRes: Int,
    ) : ScheduledBackupScreenState
}

@Immutable
data class ScheduledBackupUiData(
    val enabled: Boolean,
    val frequency: ScheduledBackupFrequency,
    val customDateEpochDay: Long?,
    val customDateLabel: String?,
    val directoryName: String?,
    val overwriteExisting: Boolean,
    val showCustomDatePicker: Boolean,
)

internal fun readCsvRecords(reader: Reader): Sequence<List<String>> =
    sequence {
        val pushbackReader = if (reader is PushbackReader) reader else PushbackReader(reader, 1)
        val record = ArrayList<String>(8)
        val field = StringBuilder(64)
        var inQuotes = false

        fun endField() {
            record.add(field.toString())
            field.setLength(0)
        }

        suspend fun SequenceScope<List<String>>.endRecord() {
            endField()
            val anyContent = record.any { it.isNotBlank() }
            if (anyContent) {
                yield(record.toList())
            }
            record.clear()
        }

        while (true) {
            val value = pushbackReader.read()
            if (value == -1) {
                if (field.isNotEmpty() || record.isNotEmpty()) {
                    endRecord()
                }
                break
            }

            val ch = value.toChar()
            when (ch) {
                '"' -> {
                    if (inQuotes) {
                        val next = pushbackReader.read()
                        if (next == '"'.code) {
                            field.append('"')
                        } else {
                            inQuotes = false
                            if (next != -1) pushbackReader.unread(next)
                        }
                    } else {
                        if (field.isEmpty()) {
                            inQuotes = true
                        } else {
                            field.append('"')
                        }
                    }
                }

                ',' -> {
                    if (inQuotes) {
                        field.append(',')
                    } else {
                        endField()
                    }
                }

                '\n' -> {
                    if (inQuotes) {
                        field.append('\n')
                    } else {
                        endRecord()
                    }
                }

                '\r' -> {
                    if (inQuotes) {
                        field.append('\r')
                    } else {
                        val next = pushbackReader.read()
                        if (next != '\n'.code && next != -1) pushbackReader.unread(next)
                        endRecord()
                    }
                }

                else -> {
                    field.append(ch)
                }
            }
        }
    }

@HiltViewModel
class BackupRestoreViewModel
    @Inject
    constructor(
        val database: MusicDatabase,
        private val createBackupUseCase: CreateBackupUseCase,
        observeScheduledBackupSettings: ObserveScheduledBackupSettingsUseCase,
        private val updateScheduledBackup: UpdateScheduledBackupUseCase,
    ) : ViewModel() {
        private val _backupRestoreProgress = MutableStateFlow<BackupRestoreProgressUi?>(null)
        val backupRestoreProgress: StateFlow<BackupRestoreProgressUi?> = _backupRestoreProgress.asStateFlow()

        private val _backupEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val backupEvent: SharedFlow<String> = _backupEvent.asSharedFlow()

        private val _scheduledBackupState = MutableStateFlow<ScheduledBackupScreenState>(ScheduledBackupScreenState.Loading)
        val scheduledBackupState: StateFlow<ScheduledBackupScreenState> = _scheduledBackupState.asStateFlow()

        private val _scheduledBackupEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
        val scheduledBackupEvent: SharedFlow<Int> = _scheduledBackupEvent.asSharedFlow()

        private var scheduledBackupSettings: ScheduledBackupSettings? = null
        private var showCustomDatePicker = false
        private var scheduledBackupUpdateJob: Job? = null
        private var manualBackupJob: Job? = null

        init {
            viewModelScope.launch {
                observeScheduledBackupSettings()
                    .catch {
                        _scheduledBackupState.value =
                            ScheduledBackupScreenState.Error(R.string.scheduled_backup_load_failed)
                    }.collect { settings ->
                        scheduledBackupSettings = settings
                        publishScheduledBackupState()
                    }
            }
        }

        private fun emitProgress(
            title: String,
            step: String,
            percent: Int,
            indeterminate: Boolean,
        ) {
            _backupRestoreProgress.value =
                BackupRestoreProgressUi(
                    title = title,
                    step = step,
                    percent = percent.coerceIn(0, 100),
                    indeterminate = indeterminate,
                )
        }

        fun backup(
            context: Context,
            uri: Uri,
            categories: Set<BackupCategory>,
        ) {
            if (manualBackupJob?.isActive == true) return
            manualBackupJob =
                viewModelScope.launch(Dispatchers.IO) {
                    val title = context.getString(R.string.backup_in_progress)
                    try {
                        createBackupUseCase(
                            uri = uri,
                            categories = categories.mapTo(linkedSetOf()) { BackupArchiveCategory.valueOf(it.name) },
                        ) { progress ->
                            val step =
                                when (progress.step) {
                                    BackupArchiveStep.EXPORT_SETTINGS -> {
                                        context.getString(R.string.backup_step_export_settings)
                                    }

                                    BackupArchiveStep.CHECKPOINT_DATABASE -> {
                                        context.getString(R.string.backup_step_checkpoint_database)
                                    }

                                    BackupArchiveStep.COPY_DATABASE_FILE -> {
                                        context.getString(R.string.backup_step_copying_file, progress.fileName.orEmpty())
                                    }
                                }
                            emitProgress(title, step, progress.percent, progress.indeterminate)
                        }

                        val msg = context.getString(R.string.backup_create_success)
                        _backupEvent.tryEmit(msg)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (exception: Exception) {
                        reportException(exception)
                        val msg = exception.message ?: context.getString(R.string.backup_create_failed)
                        _backupEvent.tryEmit(msg)
                    } finally {
                        _backupRestoreProgress.value = null
                    }
                }
        }

        fun onScheduledBackupEnabledChanged(enabled: Boolean) {
            updateScheduledBackup { updateScheduledBackup.setEnabled(enabled) }
        }

        fun onScheduledBackupFrequencySelected(frequency: ScheduledBackupFrequency) {
            if (frequency == ScheduledBackupFrequency.CUSTOM) {
                showCustomDatePicker = true
                publishScheduledBackupState(
                    scheduledBackupSettings?.copy(frequency = frequency)
                        ?: ScheduledBackupSettings(frequency = frequency),
                )
                return
            }
            updateScheduledBackup { updateScheduledBackup.setFrequency(frequency) }
        }

        fun onScheduledBackupCustomDateSelected(epochDay: Long) {
            showCustomDatePicker = false
            updateScheduledBackup { updateScheduledBackup.setCustomDate(epochDay) }
        }

        fun onScheduledBackupCustomDateDismissed() {
            showCustomDatePicker = false
            publishScheduledBackupState()
        }

        fun onScheduledBackupDirectorySelected(uri: Uri) {
            updateScheduledBackup(
                successMessageRes = R.string.scheduled_backup_directory_saved,
            ) { updateScheduledBackup.setDirectory(uri) }
        }

        fun onScheduledBackupOverwriteChanged(overwriteExisting: Boolean) {
            updateScheduledBackup { updateScheduledBackup.setOverwrite(overwriteExisting) }
        }

        private fun updateScheduledBackup(
            @StringRes successMessageRes: Int? = null,
            update: suspend () -> ScheduledBackupSettings,
        ) {
            scheduledBackupUpdateJob?.cancel()
            scheduledBackupUpdateJob =
                viewModelScope.launch {
                    try {
                        val settings = withContext(Dispatchers.IO) { update() }
                        scheduledBackupSettings = settings
                        publishScheduledBackupState(settings)
                        successMessageRes?.let { _scheduledBackupEvent.emit(it) }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (exception: Exception) {
                        reportException(exception)
                        _scheduledBackupState.value =
                            ScheduledBackupScreenState.Error(R.string.scheduled_backup_update_failed)
                        _scheduledBackupEvent.emit(R.string.scheduled_backup_update_failed)
                    }
                }
        }

        private fun publishScheduledBackupState(settings: ScheduledBackupSettings? = scheduledBackupSettings) {
            if (settings == null && !showCustomDatePicker) {
                _scheduledBackupState.value = ScheduledBackupScreenState.Empty
                return
            }
            val resolved = settings ?: ScheduledBackupSettings(frequency = ScheduledBackupFrequency.CUSTOM)
            val formattedCustomDate =
                resolved.customDateEpochDay?.let { epochDay ->
                    LocalDate
                        .ofEpochDay(epochDay)
                        .format(
                            java.time.format.DateTimeFormatter
                                .ofLocalizedDate(FormatStyle.MEDIUM)
                                .withLocale(Locale.getDefault()),
                        )
                }
            _scheduledBackupState.value =
                ScheduledBackupScreenState.Success(
                    ScheduledBackupUiData(
                        enabled = resolved.enabled,
                        frequency = resolved.frequency,
                        customDateEpochDay = resolved.customDateEpochDay,
                        customDateLabel = formattedCustomDate,
                        directoryName = resolved.directoryName,
                        overwriteExisting = resolved.overwriteExisting,
                        showCustomDatePicker = showCustomDatePicker,
                    ),
                )
        }

        fun restore(
            context: Context,
            uri: Uri,
            categories: Set<BackupCategory>,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                restoreMutex.withLock {
                    val title = context.getString(R.string.restore_in_progress)
                    val stage = File(context.cacheDir, "restore_${java.util.UUID.randomUUID()}")
                    var stagedDatabase: MusicDatabase? = null
                    var stagingDbName: String? = null
                    try {
                        check(stage.mkdirs()) { "Cannot create restore staging directory" }
                        val includeLibrary = BackupCategory.LIBRARY in categories
                        val includeSettings = BackupCategory.SETTINGS in categories
                        val includeAccount = BackupCategory.ACCOUNT in categories
                        emitProgress(title, context.getString(R.string.restore_step_verifying), 0, true)
                        val allowed = setOf(
                            SETTINGS_XML_FILENAME, SETTINGS_FILENAME, InternalDatabase.DB_NAME,
                            "${InternalDatabase.DB_NAME}-wal", "${InternalDatabase.DB_NAME}-shm",
                            "${InternalDatabase.DB_NAME}-journal",
                        )
                        val seen = hashSetOf<String>()
                        var totalBytes = 0L
                        val stream = requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot read backup" }
                        stream.use { input ->
                            input.zipInputStream().use { zip ->
                                var entry = zip.nextEntry
                                val buffer = ByteArray(64 * 1024)
                                while (entry != null) {
                                    val name = entry.name
                                    if (name in allowed && !entry.isDirectory) {
                                        check(seen.add(name)) { "Duplicate backup entry: $name" }
                                        File(stage, name).outputStream().use { output ->
                                            while (true) {
                                                val count = zip.read(buffer)
                                                if (count < 0) break
                                                totalBytes += count
                                                check(totalBytes <= 512L * 1024 * 1024) { "Backup exceeds restore size limit" }
                                                if (name == SETTINGS_FILENAME || name == SETTINGS_XML_FILENAME) {
                                                    check(output.channel.position() + count <= 8L * 1024 * 1024) { "Settings backup too large" }
                                                }
                                                output.write(buffer, 0, count)
                                            }
                                        }
                                    }
                                    zip.closeEntry() // Verify CRC before touching live data.
                                    entry = zip.nextEntry
                                }
                            }
                        }
                        val importedPreferences = when {
                            !includeSettings && !includeAccount -> null
                            SETTINGS_XML_FILENAME in seen -> File(stage, SETTINGS_XML_FILENAME).inputStream().use {
                                parseSettingsFromXml(it)
                            }
                            SETTINGS_FILENAME in seen -> {
                                val job = SupervisorJob()
                                val store = PreferenceDataStoreFactory.create(
                                    scope = CoroutineScope(job + Dispatchers.IO),
                                    produceFile = { File(stage, SETTINGS_FILENAME) },
                                )
                                try { store.data.first() } finally {
                                    job.cancel()
                                    withContext(kotlinx.coroutines.NonCancellable) { job.join() }
                                }
                            }
                            else -> error("Backup missing settings")
                        }
                        if (includeLibrary) {
                            check(InternalDatabase.DB_NAME in seen) { "Backup missing database" }
                            val name = "restore_${java.util.UUID.randomUUID()}.db"
                            stagingDbName = name
                            listOf("", "-wal", "-journal").forEach { suffix ->
                                val file = File(stage, InternalDatabase.DB_NAME + suffix)
                                if (file.exists()) file.copyTo(context.getDatabasePath(name + suffix))
                            }
                            // Room validates/migrates only the staged copy. Never repair or reset a backup.
                            stagedDatabase = InternalDatabase.newInstance(context, name, allowRepair = false)
                        }
                        val oldPreferences = context.dataStore.data.first()
                        var preferencesApplied = false
                        try {
                            importedPreferences?.let { imported ->
                                context.dataStore.edit { prefs ->
                                    imported.asMap().forEach { (key, value) ->
                                        val isAccount = key.name in ACCOUNT_PREF_KEYS
                                        if ((isAccount && includeAccount) || (!isAccount && includeSettings)) {
                                            @Suppress("UNCHECKED_CAST")
                                            prefs[key as Preferences.Key<Any>] = value
                                        }
                                    }
                                }
                                preferencesApplied = true
                            }
                            stagedDatabase?.let { database.restoreFrom(it) }
                        } catch (error: Exception) {
                            if (preferencesApplied) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                    context.dataStore.updateData { oldPreferences }
                                }
                            }
                            throw error
                        }
                        // No shared DB close, process exit, or download-cache replacement is required.
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, R.string.restore_success, Toast.LENGTH_SHORT).show()
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        reportException(error)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, error.message ?: context.getString(R.string.restore_failed), Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        stagedDatabase?.close()
                        stagingDbName?.let { context.deleteDatabase(it) }
                        stage.deleteRecursively()
                        _backupRestoreProgress.value = null
                    }
                }
            }
        }

        private fun parseSettingsFromXml(
            inputStream: java.io.InputStream,
            excludedKeyNames: Set<String> = emptySet(),
        ): Preferences {
            require(inputStream.available() <= 8 * 1024 * 1024) { "Settings backup too large" }
            val content = inputStream.readBytes().toString(Charsets.UTF_8)
            require(content.isNotBlank()) { "Empty settings backup" }

            val parser = android.util.Xml.newPullParser()
            parser.setInput(StringReader(content))

            var rootValidated = false
            var eventType = parser.eventType
            val booleans = LinkedHashMap<String, Boolean>()
            val ints = LinkedHashMap<String, Int>()
            val longs = LinkedHashMap<String, Long>()
            val floats = LinkedHashMap<String, Float>()
            val strings = LinkedHashMap<String, String>()
            val stringSets = LinkedHashMap<String, Set<String>>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val name = parser.name
                    if (!rootValidated) {
                        require(name == "ArchiveTuneBackup" || name == "map") { "Invalid settings backup document" }
                        rootValidated = true
                    }
                    val keyName = parser.getAttributeValue(null, "name")

                    if (keyName != null && keyName !in excludedKeyNames) {
                        when (name) {
                            "boolean" -> {
                                val value = parser.getAttributeValue(null, "value")?.toBooleanStrictOrNull()
                                require(value != null) { "Invalid settings value for $keyName" }
                                booleans[keyName] = value
                            }

                            "int" -> {
                                val value = parser.getAttributeValue(null, "value")?.toIntOrNull()
                                require(value != null) { "Invalid settings value for $keyName" }
                                ints[keyName] = value
                            }

                            "long" -> {
                                val value = parser.getAttributeValue(null, "value")?.toLongOrNull()
                                require(value != null) { "Invalid settings value for $keyName" }
                                longs[keyName] = value
                            }

                            "float" -> {
                                val value = parser.getAttributeValue(null, "value")?.toFloatOrNull()
                                require(value != null && value.isFinite()) { "Invalid settings value for $keyName" }
                                floats[keyName] = value
                            }

                            "string" -> {
                                val value = parser.getAttributeValue(null, "value")
                                if (value != null) {
                                    strings[keyName] = value
                                }
                            }

                            "string-set" -> {
                                val values = LinkedHashSet<String>()
                                while (true) {
                                    val next = parser.next()
                                    if (next == XmlPullParser.START_TAG && parser.name == "item") {
                                        values.add(parser.nextText())
                                        continue
                                    }
                                    if (next == XmlPullParser.END_TAG && parser.name == "string-set") {
                                        break
                                    }
                                    if (next == XmlPullParser.END_DOCUMENT) {
                                        break
                                    }
                                }
                                stringSets[keyName] = values
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            return mutablePreferencesOf().apply {
                val prefs = this
                booleans.forEach { (k, v) -> prefs[booleanPreferencesKey(k)] = v }
                ints.forEach { (k, v) -> prefs[intPreferencesKey(k)] = v }
                longs.forEach { (k, v) -> prefs[longPreferencesKey(k)] = v }
                floats.forEach { (k, v) -> prefs[floatPreferencesKey(k)] = v }
                strings.forEach { (k, v) -> prefs[stringPreferencesKey(k)] = v }
                stringSets.forEach { (k, v) -> prefs[stringSetPreferencesKey(k)] = v }
            }
        }

        private fun normalizeCsvHeaderCell(value: String): String =
            value
                .trim()
                .trimStart('\uFEFF')
                .lowercase()
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")

        suspend fun importPlaylistFromCsv(
            context: Context,
            uri: Uri,
        ): ArrayList<Song> {
            val songs =
                withContext(Dispatchers.IO) {
                    val out = arrayListOf<Song>()

                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val reader = PushbackReader(InputStreamReader(stream, Charsets.UTF_8), 1)
                            val iterator = readCsvRecords(reader).iterator()
                            if (!iterator.hasNext()) return@use

                            val firstRecord = iterator.next()
                            val normalizedHeader = firstRecord.map(::normalizeCsvHeaderCell)

                            val titleIndex =
                                normalizedHeader.indexOfFirst { it == "title" || it == "tracktitle" || it == "songtitle" }
                            val artistIndex =
                                normalizedHeader.indexOfFirst { it == "artist" || it == "artists" || it == "artistname" }

                            val hasHeader = titleIndex >= 0 && artistIndex >= 0
                            val resolvedTitleIndex = if (hasHeader) titleIndex else 0
                            val resolvedArtistIndex = if (hasHeader) artistIndex else 1

                            fun addFromRecord(record: List<String>) {
                                val titleRaw = record.getOrNull(resolvedTitleIndex).orEmpty()
                                val artistRaw = record.getOrNull(resolvedArtistIndex).orEmpty()

                                val title = titleRaw.trim().trimStart('\uFEFF')
                                if (title.isBlank()) return

                                val artistStr = artistRaw.trim()
                                val artists =
                                    artistStr
                                        .split(';', '|')
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .map { ArtistEntity(id = "", name = it) }

                                out.add(
                                    Song(
                                        song = SongEntity(id = "", title = title),
                                        artists = if (artists.isEmpty()) listOf(ArtistEntity("", "")) else artists,
                                    ),
                                )
                            }

                            if (!hasHeader) {
                                addFromRecord(firstRecord)
                            }
                            while (iterator.hasNext()) {
                                addFromRecord(iterator.next())
                            }
                        }
                    }.onFailure {
                        reportException(it)
                    }

                    out
                }

            if (songs.isEmpty()) {
                Toast
                    .makeText(
                        context,
                        "No songs found. Invalid file, or perhaps no song matches were found.",
                        Toast.LENGTH_SHORT,
                    ).show()
            }

            return songs
        }

        suspend fun loadM3UOnline(
            context: Context,
            uri: Uri,
        ): ArrayList<Song> {
            val songs =
                withContext(Dispatchers.IO) {
                    val out = ArrayList<Song>()

                    runCatching {
                        context.applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                            val lines = stream.bufferedReader().readLines()
                            if (lines.firstOrNull()?.startsWith("#EXTM3U") == true) {
                                lines.forEach { rawLine ->
                                    if (rawLine.startsWith("#EXTINF:")) {
                                        val artists =
                                            rawLine
                                                .substringAfter("#EXTINF:")
                                                .substringAfter(',')
                                                .substringBefore(" - ")
                                                .split(';')
                                        val title =
                                            rawLine
                                                .substringAfter("#EXTINF:")
                                                .substringAfter(',')
                                                .substringAfter(" - ")

                                        out.add(
                                            Song(
                                                song = SongEntity(id = "", title = title),
                                                artists = artists.map { ArtistEntity("", it) },
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }.onFailure {
                        reportException(it)
                    }

                    out
                }

            if (songs.isEmpty()) {
                Toast
                    .makeText(
                        context,
                        "No songs found. Invalid file, or perhaps no song matches were found.",
                        Toast.LENGTH_SHORT,
                    ).show()
            }

            return songs
        }

        suspend fun validateBackup(
            context: Context,
            uri: Uri,
        ): BackupValidationResult =
            withContext(Dispatchers.IO) {
                try {
                    val stream =
                        context.applicationContext.contentResolver.openInputStream(uri)
                    if (stream == null) {
                        return@withContext BackupValidationResult(
                            isValid = false,
                            availableCategories = emptySet(),
                            errorMessage = context.getString(R.string.restore_file_not_found),
                        )
                    }
                    stream.use { inputStream ->
                        val zipStream = inputStream.zipInputStream()
                        val entryNames = mutableSetOf<String>()
                        zipStream.use { zip ->
                            var entry = zip.nextEntry
                            while (entry != null) {
                                entryNames.add(entry.name)
                                entry = zip.nextEntry
                            }
                        }
                        if (entryNames.isEmpty()) {
                            return@withContext BackupValidationResult(
                                isValid = false,
                                availableCategories = emptySet(),
                                errorMessage = context.getString(R.string.restore_invalid_file),
                            )
                        }
                        val categories = mutableSetOf<BackupCategory>()
                        val hasSettings = SETTINGS_XML_FILENAME in entryNames || SETTINGS_FILENAME in entryNames
                        val hasDb = entryNames.any { it.startsWith(InternalDatabase.DB_NAME) }
                        if (hasSettings) {
                            categories.add(BackupCategory.SETTINGS)
                            categories.add(BackupCategory.ACCOUNT)
                        }
                        if (hasDb) {
                            categories.add(BackupCategory.LIBRARY)
                        }
                        if (categories.isEmpty()) {
                            return@withContext BackupValidationResult(
                                isValid = false,
                                availableCategories = emptySet(),
                                errorMessage = context.getString(R.string.restore_missing_content),
                            )
                        }
                        BackupValidationResult(
                            isValid = true,
                            availableCategories = categories,
                            errorMessage = null,
                        )
                    }
                } catch (e: Exception) {
                    reportException(e)
                    BackupValidationResult(
                        isValid = false,
                        availableCategories = emptySet(),
                        errorMessage = context.getString(R.string.restore_corrupted),
                    )
                }
            }

        companion object {
            private val restoreMutex = Mutex()
            const val SETTINGS_FILENAME = "settings.preferences_pb"
            const val SETTINGS_XML_FILENAME = BackupArchiveRepository.SETTINGS_XML_FILENAME

            val ACCOUNT_PREF_KEYS: Set<String> = BackupArchiveRepository.ACCOUNT_PREFERENCE_KEYS
        }
    }
