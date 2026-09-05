/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.vxs.frostsoulx.LocalPlayerAwareWindowInsets
import dev.vxs.frostsoulx.LocalPlayerConnection
import dev.vxs.frostsoulx.home.HomeAction
import dev.vxs.frostsoulx.home.HomeScreenState
import dev.vxs.frostsoulx.ui.component.ExpressivePullToRefreshBox
import dev.vxs.frostsoulx.ui.component.LocalMenuState
import dev.vxs.frostsoulx.ui.frostsoul.FSEmptyState
import dev.vxs.frostsoulx.ui.frostsoul.FSLoading
import dev.vxs.frostsoulx.viewmodels.HomeViewModel

@Composable
fun HomeScreen(
    navController: NavController,
    headerScrollConnection: NestedScrollConnection? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val localView = LocalView.current

    DisposableEffect(localView) {
        val window = (localView.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.let {
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            it.hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.statusBars())
        }
    }
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry
            ?.savedStateHandle
            ?.getStateFlow("scrollToTop", false)
            ?.collectAsStateWithLifecycle()
    val uiState = (screenState as? HomeScreenState.Success)?.uiState

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(uiState?.homePage?.continuation) {
        val continuation = uiState?.homePage?.continuation ?: return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = lazyListState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
            lastVisibleIndex != null && lastVisibleIndex >= layoutInfo.totalItemsCount - 3
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) viewModel.onAction(HomeAction.LoadMore(continuation))
        }
    }

    val selectedChip = uiState?.selectedChip
    if (selectedChip != null) {
        BackHandler { viewModel.onAction(HomeAction.SelectChip(null)) }
    }
    LaunchedEffect(uiState?.showCategoryChips, selectedChip) {
        if (uiState?.showCategoryChips == false && selectedChip != null) {
            viewModel.onAction(HomeAction.SelectChip(null))
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(
                    if (headerScrollConnection != null) Modifier.nestedScroll(headerScrollConnection) else Modifier,
                ),
    ) {
        when (val state = screenState) {
            HomeScreenState.Loading -> FrostSoulHomeLoading()
            HomeScreenState.Empty -> FrostSoulHomeEmpty(onRetry = { viewModel.onAction(HomeAction.Refresh) })
            is HomeScreenState.Error -> FrostSoulHomeError(onRetry = { viewModel.onAction(HomeAction.Refresh) })
            is HomeScreenState.Success -> {
                ExpressivePullToRefreshBox(
                    isRefreshing = state.uiState.isRefreshing,
                    onRefresh = { viewModel.onAction(HomeAction.Refresh) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    FrostSoulHomeFeed(
                        uiState = state.uiState,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        navController = navController,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                        scope = scope,
                        lazyListState = lazyListState,
                        onAction = viewModel::onAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun FrostSoulHomeLoading() {
    Box(
        contentAlignment = androidx.compose.ui.Alignment.Center,
        modifier = Modifier.fillMaxSize().padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
    ) {
        FSLoading()
    }
}

@Composable
private fun FrostSoulHomeEmpty(onRetry: () -> Unit) {
    FSEmptyState(
        title = "Your home is ready for music",
        message = "Refresh to reconnect your library and recommendations.",
        actionLabel = "Refresh",
        onAction = onRetry,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun FrostSoulHomeError(onRetry: () -> Unit) {
    FSEmptyState(
        title = "We could not load your listening home",
        message = "Check your connection, then refresh to restore your recommendations.",
        actionLabel = "Try Again",
        onAction = onRetry,
        modifier = Modifier.fillMaxSize(),
    )
}
