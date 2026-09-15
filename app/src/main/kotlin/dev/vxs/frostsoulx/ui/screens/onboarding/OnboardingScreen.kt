/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.screens.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.onboarding.OnboardingEvent
import dev.vxs.frostsoulx.onboarding.OnboardingPageId
import dev.vxs.frostsoulx.onboarding.OnboardingPermissionAction
import dev.vxs.frostsoulx.onboarding.OnboardingPermissionStatus
import dev.vxs.frostsoulx.onboarding.OnboardingScreenState
import dev.vxs.frostsoulx.onboarding.OnboardingUiState
import dev.vxs.frostsoulx.onboarding.OnboardingViewModel
import dev.vxs.frostsoulx.ui.frostsoul.FSButton
import dev.vxs.frostsoulx.ui.frostsoul.FSGlassCard
import dev.vxs.frostsoulx.ui.frostsoul.FSIcon
import dev.vxs.frostsoulx.ui.frostsoul.FSText
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulTheme
import dev.vxs.frostsoulx.ui.frostsoul.frostSoulScreenBackground

@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onPermissionResult()
        }
    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.onPermissionResult()
        }

    LaunchedEffect(context, viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OnboardingEvent.RequestPermission -> permissionLauncher.launch(event.permission)
                OnboardingEvent.OpenInstallPackagesSettings -> {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData("package:${context.packageName}".toUri())
                    runCatching { settingsLauncher.launch(intent) }
                }
            }
        }
    }

    OnboardingScreen(
        state = state,
        onNext = viewModel::onNext,
        onBack = viewModel::onBack,
        onComplete = viewModel::complete,
        onPermissionAction = viewModel::onPermissionAction,
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingScreenState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().frostSoulScreenBackground(),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            OnboardingScreenState.Loading -> {
                FSText(
                    text = "Preparing FrostSoul",
                    color = FrostSoulTheme.colors.onSurfaceMuted,
                    fontSize = 15.sp,
                )
            }
            OnboardingScreenState.Empty -> {
                OnboardingMessage(
                    title = stringResource(R.string.onboarding_empty_title),
                    message = stringResource(R.string.onboarding_empty_subtitle),
                    action = stringResource(R.string.onboarding_finish),
                    onAction = onComplete,
                )
            }
            is OnboardingScreenState.Error -> {
                OnboardingMessage(
                    title = stringResource(state.messageResId),
                    message = stringResource(R.string.onboarding_empty_subtitle),
                    action = stringResource(R.string.onboarding_finish),
                    onAction = onComplete,
                )
            }
            is OnboardingScreenState.Success -> {
                OnboardingPager(
                    uiState = state.uiState,
                    onNext = onNext,
                    onBack = onBack,
                    onComplete = onComplete,
                    onPermissionAction = onPermissionAction,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun OnboardingMessage(
    title: String,
    message: String,
    action: String,
    onAction: () -> Unit,
) {
    FSGlassCard(
        modifier = Modifier.widthIn(max = 420.dp).padding(24.dp),
        contentPadding = PaddingValues(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FSText(title, color = FrostSoulTheme.colors.onSurface, style = FrostSoulTheme.typography.title)
            FSText(message, color = FrostSoulTheme.colors.onSurfaceMuted, style = FrostSoulTheme.typography.body)
            FSButton(label = action, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun OnboardingPager(
    uiState: OnboardingUiState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safePageCount = uiState.pages.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(initialPage = uiState.currentPage.coerceIn(0, safePageCount - 1), pageCount = { safePageCount })
    LaunchedEffect(uiState.currentPage) {
        pagerState.animateScrollToPage(uiState.currentPage.coerceIn(0, safePageCount - 1))
    }

    Column(
        modifier = modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
        ) {
            repeat(safePageCount) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == pagerState.currentPage) 28.dp else 7.dp, 5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (index == pagerState.currentPage) FrostSoulTheme.colors.accentBright
                            else FrostSoulTheme.colors.onSurfaceMuted.copy(alpha = 0.28f),
                        ),
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            userScrollEnabled = true,
        ) { page ->
            val pageModel = uiState.pages.getOrNull(page)
            when (pageModel?.id) {
                OnboardingPageId.WELCOME -> WelcomePage(uiState = uiState)
                OnboardingPageId.PERMISSIONS -> PermissionsPage(
                    permissions = uiState.permissions,
                    onPermissionAction = onPermissionAction,
                )
                null -> WelcomePage(uiState = uiState)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        ) {
            if (pagerState.currentPage > 0) {
                FSButton(label = "Back", onClick = onBack, emphasized = false, modifier = Modifier.weight(1f))
            }
            FSButton(
                label = if (pagerState.currentPage == safePageCount - 1) stringResource(R.string.onboarding_finish) else "Continue",
                onClick = if (pagerState.currentPage == safePageCount - 1) onComplete else onNext,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WelcomePage(uiState: OnboardingUiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().widthIn(max = 500.dp).padding(horizontal = 16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(112.dp).clip(RoundedCornerShape(34.dp)).background(FrostSoulTheme.colors.surfaceRaised),
        ) {
            FSIcon(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = "FrostSoul",
                modifier = Modifier.size(78.dp),
            )
        }
        FSText(
            text = stringResource(R.string.onboarding_welcome_title),
            color = FrostSoulTheme.colors.onSurface,
            style = FrostSoulTheme.typography.display,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 24.dp),
        )
        FSText(
            text = stringResource(R.string.onboarding_welcome_subtitle),
            color = FrostSoulTheme.colors.onSurfaceMuted,
            style = FrostSoulTheme.typography.body,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
        FSText(
            text = "A focused player, synchronized lyrics and a calm listening home.",
            color = FrostSoulTheme.colors.accentBright,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

@Composable
private fun PermissionsPage(
    permissions: List<dev.vxs.frostsoulx.onboarding.OnboardingPermissionUiModel>,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().widthIn(max = 560.dp)) {
        FSText(
            text = stringResource(R.string.onboarding_permissions_title),
            color = FrostSoulTheme.colors.onSurface,
            style = FrostSoulTheme.typography.display,
            fontSize = 26.sp,
        )
        FSText(
            text = stringResource(R.string.onboarding_permissions_subtitle),
            color = FrostSoulTheme.colors.onSurfaceMuted,
            style = FrostSoulTheme.typography.body,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(permissions, key = { it.id.name }) { permission ->
                PermissionRow(permission = permission, onPermissionAction = onPermissionAction)
            }
        }
    }
}

@Composable
private fun PermissionRow(
    permission: dev.vxs.frostsoulx.onboarding.OnboardingPermissionUiModel,
    onPermissionAction: (OnboardingPermissionAction) -> Unit,
) {
    val needsAction = permission.status == OnboardingPermissionStatus.NEEDS_ACTION
    FSGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = FrostSoulTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            FSIcon(
                painter = painterResource(permission.iconResId),
                contentDescription = null,
                tint = FrostSoulTheme.colors.accentBright,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                FSText(permission.titleResId.let { stringResource(it) }, color = FrostSoulTheme.colors.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                FSText(permission.descriptionResId.let { stringResource(it) }, color = FrostSoulTheme.colors.onSurfaceMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
            if (needsAction && permission.action != null) {
                FSButton(label = "Allow", onClick = { onPermissionAction(permission.action) }, emphasized = false)
            } else {
                FSText(
                    text = if (permission.status == OnboardingPermissionStatus.UNAVAILABLE) "Unavailable" else "Ready",
                    color = FrostSoulTheme.colors.onSurfaceMuted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
