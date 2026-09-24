/*
 * FrostSoulX taste profile.
 * Built on-device from local listening history; nothing in here leaves the phone.
 */

package dev.vxs.frostsoulx.viewmodels

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.taste.GetTasteProfileUseCase
import dev.vxs.frostsoulx.taste.TasteProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TasteProfileScreenState {
    data object Loading : TasteProfileScreenState

    @Immutable
    data class Success(
        val profile: TasteProfile,
    ) : TasteProfileScreenState

    data object Empty : TasteProfileScreenState

    @Immutable
    data class Error(
        @StringRes val messageResId: Int,
    ) : TasteProfileScreenState
}

@HiltViewModel
class TasteProfileViewModel
    @Inject
    constructor(
        private val getTasteProfile: GetTasteProfileUseCase,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow<TasteProfileScreenState>(TasteProfileScreenState.Loading)
        val state: StateFlow<TasteProfileScreenState> = mutableState.asStateFlow()

        private var loadJob: Job? = null

        init {
            load()
        }

        fun retry() {
            load()
        }

        private fun load() {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    mutableState.value = TasteProfileScreenState.Loading
                    mutableState.value =
                        getTasteProfile(forceRefresh = true).fold(
                            onSuccess = { profile ->
                                if (profile.isUsable) {
                                    TasteProfileScreenState.Success(profile)
                                } else {
                                    TasteProfileScreenState.Empty
                                }
                            },
                            onFailure = { TasteProfileScreenState.Error(R.string.taste_profile_error_message) },
                        )
                }
        }
    }
