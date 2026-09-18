/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.together

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.constants.TogetherAllowGuestsToAddTracksKey
import dev.vxs.frostsoulx.constants.TogetherAllowGuestsToControlPlaybackKey
import dev.vxs.frostsoulx.constants.TogetherDefaultPortKey
import dev.vxs.frostsoulx.constants.TogetherDisplayNameKey
import dev.vxs.frostsoulx.constants.TogetherLastJoinLinkKey
import dev.vxs.frostsoulx.constants.TogetherRequireHostApprovalToJoinKey
import dev.vxs.frostsoulx.constants.TogetherWelcomeShownKey
import dev.vxs.frostsoulx.playback.MusicService
import dev.vxs.frostsoulx.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

enum class MusicTogetherConnectionMode {
    LAN,
    ONLINE,
}

data class MusicTogetherPreferences(
    val displayName: String,
    val port: Int,
    val allowGuestsToAddTracks: Boolean,
    val allowGuestsToControlPlayback: Boolean,
    val requireHostApprovalToJoin: Boolean,
    val lastJoinLink: String,
    val welcomeShown: Boolean,
)

data class MusicTogetherSnapshot(
    val preferences: MusicTogetherPreferences,
    val sessionState: TogetherSessionState,
)

@Singleton
class MusicTogetherRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val serviceFlow = MutableStateFlow<MusicService?>(null)
        private var pendingStartRequest: StartSessionRequest? = null

        private data class StartSessionRequest(
            val mode: MusicTogetherConnectionMode,
            val displayName: String,
            val port: Int,
            val settings: TogetherRoomSettings,
        )

        val preferences: Flow<MusicTogetherPreferences> =
            context.dataStore.data
                .map { preferences ->
                    MusicTogetherPreferences(
                        displayName =
                            preferences[TogetherDisplayNameKey]
                                ?: Build.MODEL?.takeIf { it.isNotBlank() }
                                ?: context.getString(R.string.app_name),
                        port = preferences[TogetherDefaultPortKey] ?: 42117,
                        allowGuestsToAddTracks = preferences[TogetherAllowGuestsToAddTracksKey] ?: true,
                        allowGuestsToControlPlayback = preferences[TogetherAllowGuestsToControlPlaybackKey] ?: false,
                        requireHostApprovalToJoin = preferences[TogetherRequireHostApprovalToJoinKey] ?: false,
                        lastJoinLink = preferences[TogetherLastJoinLinkKey] ?: "",
                        welcomeShown = preferences[TogetherWelcomeShownKey] ?: false,
                    )
                }.distinctUntilChanged()

        @OptIn(ExperimentalCoroutinesApi::class)
        val sessionState: Flow<TogetherSessionState> =
            serviceFlow.flatMapLatest { service ->
                service?.togetherSessionState ?: flowOf(TogetherSessionState.Idle)
            }

        fun attachService(service: MusicService?) {
            serviceFlow.value = service
            if (service != null) {
                pendingStartRequest?.also { request ->
                    pendingStartRequest = null
                    dispatchStartSession(service, request)
                }
            }
        }

        private fun dispatchStartSession(
            service: MusicService,
            request: StartSessionRequest,
        ) {
            when (request.mode) {
                MusicTogetherConnectionMode.LAN -> {
                    service.startTogetherHost(
                        port = request.port,
                        displayName = request.displayName,
                        settings = request.settings,
                    )
                }
                MusicTogetherConnectionMode.ONLINE -> {
                    service.startTogetherOnlineHost(
                        displayName = request.displayName,
                        settings = request.settings,
                    )
                }
            }
        }

        suspend fun setDisplayName(displayName: String) {
            context.dataStore.edit { preferences ->
                preferences[TogetherDisplayNameKey] = displayName
            }
        }

        suspend fun setPort(port: Int) {
            context.dataStore.edit { preferences ->
                preferences[TogetherDefaultPortKey] = port
            }
        }

        suspend fun setAllowGuestsToAddTracks(value: Boolean) {
            context.dataStore.edit { preferences ->
                preferences[TogetherAllowGuestsToAddTracksKey] = value
            }
        }

        suspend fun setAllowGuestsToControlPlayback(value: Boolean) {
            context.dataStore.edit { preferences ->
                preferences[TogetherAllowGuestsToControlPlaybackKey] = value
            }
        }

        suspend fun setRequireHostApprovalToJoin(value: Boolean) {
            context.dataStore.edit { preferences ->
                preferences[TogetherRequireHostApprovalToJoinKey] = value
            }
        }

        suspend fun setLastJoinLink(value: String) {
            context.dataStore.edit { preferences ->
                preferences[TogetherLastJoinLinkKey] = value
            }
        }

        suspend fun setWelcomeShown(value: Boolean) {
            context.dataStore.edit { preferences ->
                preferences[TogetherWelcomeShownKey] = value
            }
        }

        fun startSession(
            mode: MusicTogetherConnectionMode,
            displayName: String,
            port: Int,
            settings: TogetherRoomSettings,
        ) {
            val request =
                StartSessionRequest(
                    mode = mode,
                    displayName = displayName,
                    port = port,
                    settings = settings,
                )
            val service = serviceFlow.value
            if (service == null) {
                // The settings screen can render before the player service binding completes.
                // Do not silently drop the user's click; replay it as soon as the service attaches.
                pendingStartRequest = request
                return
            }
            dispatchStartSession(service, request)
        }

        fun joinSession(
            mode: MusicTogetherConnectionMode,
            rawInput: String,
            displayName: String,
        ) {
            val service = serviceFlow.value ?: return
            when (mode) {
                MusicTogetherConnectionMode.LAN -> service.joinTogether(rawInput, displayName)
                MusicTogetherConnectionMode.ONLINE -> service.joinTogetherOnline(rawInput, displayName)
            }
        }

        fun leaveSession() {
            serviceFlow.value?.leaveTogether()
        }

        fun updateSettings(settings: TogetherRoomSettings) {
            serviceFlow.value?.updateTogetherSettings(settings)
        }

        fun approveParticipant(
            participantId: String,
            approved: Boolean,
        ) {
            serviceFlow.value?.approveTogetherParticipant(participantId, approved)
        }

        fun kickParticipant(participantId: String) {
            serviceFlow.value?.kickTogetherParticipant(participantId)
        }

        fun banParticipant(participantId: String) {
            serviceFlow.value?.banTogetherParticipant(participantId)
        }

        fun transferHostOwnership(participantId: String) {
            serviceFlow.value?.transferTogetherHostOwnership(participantId)
        }
    }
