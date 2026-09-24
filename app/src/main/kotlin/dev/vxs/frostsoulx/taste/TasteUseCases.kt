/*
 * FrostSoulX taste profile.
 * Built on-device from local listening history; nothing in here leaves the phone.
 */

package dev.vxs.frostsoulx.taste

import javax.inject.Inject

class GetTasteProfileUseCase
    @Inject
    constructor(
        private val repository: TasteProfileRepository,
    ) {
        /** Returns the cached profile when it is fresh, otherwise (or when [forceRefresh]) rebuilds it. */
        suspend operator fun invoke(forceRefresh: Boolean = false): Result<TasteProfile> =
            repository.profile(forceRefresh = forceRefresh)
    }
