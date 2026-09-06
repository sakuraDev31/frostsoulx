/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.component

import android.os.SystemClock
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
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
import dev.vxs.frostsoulx.ui.screens.Screens

private val NavigationItemsMaxWidth = 170.dp

@Composable
fun FloatingNavigationToolbar(
    items: List<Screens>,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    isPairedWithMiniPlayer: Boolean = false,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onSearchItemDoubleClick: (() -> Unit)? = null,
    onCenterClick: (() -> Unit)? = null,
    overflowContent: (@Composable () -> Unit)? = null,
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
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            FSNavigationBar(
                items = navigationItems,
                selectedRoute = selectedRoute,
                pureBlack = pureBlack,
                pairedWithMiniPlayer = isPairedWithMiniPlayer,
                modifier = Modifier.weight(1f).widthIn(max = minOf(NavigationBarMaxWidth, NavigationItemsMaxWidth)),
                onCenterClick = onCenterClick,
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
            overflowContent?.invoke()
        }
    }
}
