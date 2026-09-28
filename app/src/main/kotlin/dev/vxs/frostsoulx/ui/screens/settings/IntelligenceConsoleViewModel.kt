package dev.vxs.frostsoulx.ui.screens.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.vxs.frostsoulx.recommendation.IntelligenceSnapshot
import dev.vxs.frostsoulx.recommendation.OfflineRecommendationEngine
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class IntelligenceConsoleViewModel @Inject constructor(
    engine: OfflineRecommendationEngine,
) : ViewModel() {
    val snapshot: StateFlow<IntelligenceSnapshot> = engine.telemetry
}
