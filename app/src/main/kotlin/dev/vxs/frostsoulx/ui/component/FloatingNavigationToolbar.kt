/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.component

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.vxs.frostsoulx.constants.NavigationBarMaxWidth
import dev.vxs.frostsoulx.ui.frostsoul.FSNavigationBar
import dev.vxs.frostsoulx.ui.frostsoul.FSNavigationItem
import dev.vxs.frostsoulx.ui.frostsoul.FSNavigationMoreButton
import dev.vxs.frostsoulx.ui.screens.Screens

private val MoreButtonGap = 10.dp

/**
 * Floating glass nav pill with an optional detached "More" circle on the right.
 *
 * When [showMore] is true the circle springs in and the pill shrinks to make room for it
 * (Library). When it is false the circle disappears and the pill expands to the full row
 * width (Home and the other tabs).
 */
@Composable
fun FloatingNavigationToolbar(
    items: List<Screens>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    isPairedWithMiniPlayer: Boolean = false,
    showMore: Boolean = false,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    moreMenuContent: (@Composable () -> Unit)? = null,
) {
    val navigationItems =
        items.map { screen ->
            FSNavigationItem(
                route = screen.route,
                label = stringResource(screen.titleId),
                activeIcon = screen.iconIdActive,
                inactiveIcon = screen.iconIdInactive,
            )
        }
    val selectedRoute = items.firstOrNull(isSelected)?.route
    val lastSearchClickAt = remember { mutableLongStateOf(0L) }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().widthIn(max = NavigationBarMaxWidth),
        ) {
            FSNavigationBar(
                items = navigationItems,
                selectedRoute = selectedRoute,
                pureBlack = pureBlack,
                pairedWithMiniPlayer = isPairedWithMiniPlayer,
                modifier = Modifier.weight(1f),
                onItemClick = { item, selected ->
                    items.firstOrNull { it.route == item.route }?.let { screen ->
                        val isSearchDoubleTap =
                            screen == Screens.Search &&
                                onSearchItemDoubleClick != null &&
                                SystemClock.uptimeMillis() - lastSearchClickAt.longValue <= ViewConfiguration.getDoubleTapTimeout()
                        lastSearchClickAt.longValue = if (isSearchDoubleTap) 0L else SystemClock.uptimeMillis()
                        if (isSearchDoubleTap) {
                            onSearchItemDoubleClick.invoke()
                        } else {
                            onItemClick(screen, selected)
                        }
                    }
                },
            )
            AnimatedVisibility(
                visible = showMore && onMoreClick != null,
                enter =
                    fadeIn(tween(160)) +
                        scaleIn(
                            initialScale = 0.4f,
                            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
                        ) +
                        expandHorizontally(
                            expandFrom = Alignment.Start,
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                        ),
                exit =
                    fadeOut(tween(120)) +
                        scaleOut(targetScale = 0.4f, animationSpec = tween(180)) +
                        shrinkHorizontally(
                            shrinkTowards = Alignment.Start,
                            animationSpec = tween(220),
                        ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(MoreButtonGap))
                    Box(contentAlignment = Alignment.TopEnd) {
                        FSNavigationMoreButton(
                            onClick = { onMoreClick?.invoke() },
                            pureBlack = pureBlack,
                        )
                        moreMenuContent?.invoke()
                    }
                }
            }
        }
    }
}
