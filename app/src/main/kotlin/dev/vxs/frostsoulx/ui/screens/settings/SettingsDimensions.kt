/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.screens.settings

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.vxs.frostsoulx.LocalAnimationsDisabled

object SettingsDimensions {
    val GroupCardCornerRadius = 18.dp
    val BannerCardCornerRadius = 18.dp

    val ScreenHorizontalPadding = 16.dp
    val ScreenBottomPadding = 32.dp
    val SectionSpacing = 24.dp
    val SegmentedGroupHorizontalPadding = 16.dp
    val SegmentedItemGap = 1.dp
    val RowVerticalPadding = 12.dp
    val RowHorizontalPadding = 16.dp

    val RowIconSize = 36.dp
    val RowIconInnerSize = 19.dp
    val BannerIconSize = 44.dp
    val BannerIconInnerSize = 22.dp
    val ChevronSize = 18.dp

    val ProfileCardAvatarSize = 56.dp
    val ProfileCardAvatarIconSize = 28.dp

    val DividerThickness = 0.5.dp
    val DividerStartIndent = 60.dp

    val SectionHeaderBottomPadding = 8.dp
    val SectionHeaderHorizontalPadding = 20.dp
}

object SettingsAnimations {
    val PressScale = 0.97f

    @Composable
    fun <T> pressSpring(): FiniteAnimationSpec<T> =
        if (LocalAnimationsDisabled.current) {
            snap()
        } else {
            spring(stiffness = Spring.StiffnessHigh)
        }
}
