/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.frostsoul

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import dev.vxs.frostsoulx.R
import coil3.request.CachePolicy
import coil3.size.Size
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.abs

@Composable
fun FSButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    emphasized: Boolean = true,
) {
    val colors = FrostSoulTheme.colors
    val shape = FrostSoulTheme.shapes.pill
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(FrostSoulTheme.spacing.small, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(
                    if (emphasized) {
                        Brush.horizontalGradient(
                            listOf(colors.accent, colors.accentBright),
                        )
                    } else {
                        Brush.horizontalGradient(
                            listOf(colors.surfaceRaised, colors.surface),
                        )
                    },
                    shape,
                ).border(
                    BorderStroke(1.dp, if (emphasized) colors.accentBright.copy(alpha = 0.38f) else colors.outline),
                    shape,
                ).clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(horizontal = FrostSoulTheme.spacing.large, vertical = FrostSoulTheme.spacing.medium),
    ) {
        leading?.invoke()
        FSText(
            text = label,
            style = FrostSoulTheme.typography.label,
            color = if (emphasized) colors.surface else colors.onSurface,
            maxLines = 1,
        )
        trailing?.invoke()
    }
}

@Composable
fun FSIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    icon: @Composable () -> Unit,
) {
    val colors = FrostSoulTheme.colors
    val shape = CircleShape
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(44.dp)
                .clip(shape)
                .background(
                    if (highlighted) colors.accent.copy(alpha = 0.16f) else colors.surfaceGlass,
                    shape,
                ).border(
                    BorderStroke(1.dp, if (highlighted) colors.accent.copy(alpha = 0.52f) else colors.outline.copy(alpha = 0.72f)),
                    shape,
                ).clickable(enabled = enabled, indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    onClick()
                }.frostSoulGlow(if (highlighted) colors.accent else Color.Transparent, alpha = 0.22f),
    ) {
        icon()
    }
}

@Composable
fun FSGlassCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = FrostSoulTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(FrostSoulTheme.spacing.large),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .clip(shape)
                .frostSoulGlass(shape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ).padding(contentPadding),
        content = content,
    )
}

@Composable
fun FSAlbumArt(
    artworkUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = FrostSoulTheme.shapes.medium,
    circular: Boolean = false,
    showGlow: Boolean = false,
) {
    val context = LocalContext.current
    val actualShape = if (circular) CircleShape else shape
    val request = remember(artworkUrl, context) {
        ImageRequest.Builder(context)
            .data(artworkUrl)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .size(Size(768, 768))
            .crossfade(true)
            .build()
    }
    Box(
        modifier =
            modifier
                .clip(actualShape)
                .background(FrostSoulTheme.colors.surfaceRaised, actualShape)
                .then(if (showGlow) Modifier.frostSoulGlow() else Modifier),
    ) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f)),
                        ),
                    ),
        )
    }
}

@Composable
fun FSListItem(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = FrostSoulTheme.colors
    val shape = FrostSoulTheme.shapes.medium
    Row(
        horizontalArrangement = Arrangement.spacedBy(FrostSoulTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clip(shape)
                .background(if (isActive) colors.accent.copy(alpha = 0.12f) else Color.Transparent, shape)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ).padding(horizontal = FrostSoulTheme.spacing.medium, vertical = FrostSoulTheme.spacing.small),
    ) {
        FSAlbumArt(
            artworkUrl = artworkUrl,
            contentDescription = null,
            modifier = Modifier.size(54.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            FSText(
                text = title,
                color = if (isActive) colors.accentBright else colors.onSurface,
                style = FrostSoulTheme.typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                FSText(
                    text = it,
                    color = colors.onSurfaceMuted,
                    style = FrostSoulTheme.typography.bodyMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun FSAlbumCard(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    badge: String? = null,
    width: Dp = 154.dp,
    artworkAspectRatio: Float = 1f,
    showPlayOverlay: Boolean = false,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(FrostSoulTheme.spacing.small),
        modifier = modifier.width(width).clip(FrostSoulTheme.shapes.medium).clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onClick,
        ),
    ) {
        Box {
            FSAlbumArt(
                artworkUrl = artworkUrl,
                contentDescription = title,
                showGlow = true,
                modifier = Modifier.fillMaxWidth().aspectRatio(artworkAspectRatio),
            )
            if (showPlayOverlay) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.92f)),
                ) {
                    FSIcon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = "Play $title",
                        tint = Color(0xFF001416),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            badge?.let {
                FSText(
                    text = it,
                    color = Color(0xFF001416),
                    style = FrostSoulTheme.typography.overline,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(FrostSoulTheme.spacing.small)
                            .clip(FrostSoulTheme.shapes.pill)
                            .background(FrostSoulTheme.colors.accentBright)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        FSText(
            text = title,
            color = FrostSoulTheme.colors.onSurface,
            style = FrostSoulTheme.typography.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            FSText(
                text = it,
                color = FrostSoulTheme.colors.onSurfaceMuted,
                style = FrostSoulTheme.typography.bodyMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

@Composable
fun FSArtistCard(
    name: String,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FrostSoulTheme.spacing.small),
        modifier = modifier.width(128.dp).clip(FrostSoulTheme.shapes.medium).clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onClick,
        ),
    ) {
        FSAlbumArt(
            artworkUrl = artworkUrl,
            contentDescription = name,
            circular = true,
            showGlow = true,
            modifier = Modifier.size(112.dp),
        )
        FSText(
            text = name,
            color = FrostSoulTheme.colors.onSurface,
            style = FrostSoulTheme.typography.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        subtitle?.let {
            FSText(
                text = it,
                color = FrostSoulTheme.colors.onSurfaceMuted,
                style = FrostSoulTheme.typography.bodyMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun FSSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(FrostSoulTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(horizontal = FrostSoulTheme.spacing.page),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            eyebrow?.let {
                FSText(
                    text = it.uppercase(),
                    color = FrostSoulTheme.colors.accent,
                    style = FrostSoulTheme.typography.overline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FSText(
                text = title,
                color = FrostSoulTheme.colors.onSurface,
                style = FrostSoulTheme.typography.sectionTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (actionLabel != null && onAction != null) {
            FSText(
                text = actionLabel,
                color = FrostSoulTheme.colors.accentBright,
                style = FrostSoulTheme.typography.label,
                modifier = Modifier.clip(FrostSoulTheme.shapes.pill).clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onAction,
                ).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/** Low-profile metadata badge for codec, quality, and queue information. */
@Composable
fun MinimalistMetadataChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = FrostSoulTheme.colors
    val shape = RoundedCornerShape(6.dp)
    FSText(
        text = text,
        color = colors.onSurfaceMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        modifier =
            modifier
                .clip(shape)
                .background(colors.surfaceGlass.copy(alpha = 0.06f), shape)
                .border(width = 0.5.dp, color = colors.onSurfaceMuted.copy(alpha = 0.24f), shape = shape)
                .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
fun FSChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FrostSoulTheme.colors
    val shape = FrostSoulTheme.shapes.pill
    FSText(
        text = label,
        style = FrostSoulTheme.typography.label,
        color = if (selected) Color(0xFF001416) else colors.onSurfaceMuted,
        maxLines = 1,
        modifier =
            modifier
                .clip(shape)
                .background(if (selected) colors.accentBright else colors.surfaceGlass, shape)
                .border(BorderStroke(1.dp, if (selected) colors.accentBright else colors.outline), shape)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
fun FSBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var dragOffset by remember { mutableFloatStateOf(0f) }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = FrostSoulTheme.motion.expressive)) + scaleIn(initialScale = 0.96f),
        exit = fadeOut(animationSpec = tween(durationMillis = FrostSoulTheme.motion.standard)) + scaleOut(targetScale = 0.98f),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(FrostSoulTheme.colors.scrim).clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        ) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(FrostSoulTheme.shapes.extraLarge)
                        .frostSoulGlass(FrostSoulTheme.shapes.extraLarge)
                        .graphicsLayer { translationY = dragOffset.coerceAtLeast(0f) }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, amount -> dragOffset = (dragOffset + amount).coerceAtLeast(0f) },
                                onDragEnd = {
                                    if (dragOffset > 160f) onDismiss()
                                    dragOffset = 0f
                                },
                            )
                        }.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
                content = content,
            )
        }
    }
}

@Immutable
data class FSNavigationItem(
    val route: String,
    val label: String,
    @DrawableRes val activeIcon: Int,
    @DrawableRes val inactiveIcon: Int,
)

@Composable
fun FSNavigationBar(
    items: List<FSNavigationItem>,
    selectedRoute: String?,
    onItemClick: (FSNavigationItem, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pairedWithMiniPlayer: Boolean = false,
    onCenterClick: (() -> Unit)? = null,
) {
    val homeSelected = selectedRoute == "home"
    val selectedTint = if (homeSelected) Color(0xFFFFE4AD) else FrostSoulTheme.colors.accentBright
    val shape = if (pairedWithMiniPlayer) {
        androidx.compose.foundation.shape.RoundedCornerShape(12.dp, 12.dp, 28.dp, 28.dp)
    } else {
        FrostSoulTheme.shapes.extraLarge
    }
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .height(60.dp)
                .clip(shape)
                .then(if (homeSelected) Modifier.background(Color(0xFF080A0F), shape) else Modifier.frostSoulGlass(shape))
                .padding(horizontal = FrostSoulTheme.spacing.small, vertical = 4.dp),
    ) {
        items.forEachIndexed { index, item ->
            if (onCenterClick != null && index == 2) {
                FrostSoulCenterNavigationAction(onClick = onCenterClick)
            }
            val selected = selectedRoute == item.route
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(FrostSoulTheme.shapes.medium)
                        .background(Brush.verticalGradient(listOf(
                            if (selected) selectedTint.copy(alpha = 0.06f) else Color.Transparent,
                            if (selected) selectedTint.copy(alpha = 0.18f) else Color.Transparent,
                        )))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onItemClick(item, selected) }
                        .padding(vertical = 4.dp),
            ) {
                FSIcon(
                    painter = painterResource(if (selected) item.activeIcon else item.inactiveIcon),
                    contentDescription = item.label,
                    tint = if (selected) selectedTint else FrostSoulTheme.colors.onSurfaceMuted,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(2.dp))
                FSText(
                    text = item.label,
                    color = if (selected) selectedTint else FrostSoulTheme.colors.onSurfaceMuted,
                    style = FrostSoulTheme.typography.overline,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FrostSoulCenterNavigationAction(onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "frostsoul-center-navigation-glow")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.34f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Reverse),
        label = "frostsoul-center-navigation-glow-alpha",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(58.dp)
            .fillMaxSize()
            .padding(horizontal = 4.dp)
            .clip(CircleShape)
            .background(FrostSoulTheme.colors.accent.copy(alpha = glowAlpha), CircleShape)
            .border(BorderStroke(1.dp, FrostSoulTheme.colors.accent.copy(alpha = 0.62f)), CircleShape)
            .frostSoulGlow(FrostSoulTheme.colors.accentBright, alpha = glowAlpha)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
    ) {
        FSIcon(
            painter = painterResource(R.drawable.about_appbar),
            contentDescription = "Open FrostSoul player",
            tint = FrostSoulTheme.colors.accentBright,
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
fun FSTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = FrostSoulTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = FrostSoulTheme.typography.body.copy(color = colors.onSurface),
        keyboardOptions = keyboardOptions,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(FrostSoulTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .clip(FrostSoulTheme.shapes.medium)
                        .frostSoulGlass(FrostSoulTheme.shapes.medium)
                        .padding(horizontal = FrostSoulTheme.spacing.medium, vertical = 13.dp),
            ) {
                leading?.invoke()
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isBlank()) {
                        FSText(
                            text = placeholder,
                            color = colors.onSurfaceMuted,
                            style = FrostSoulTheme.typography.body,
                        )
                    }
                    innerTextField()
                }
                trailing?.invoke()
            }
        },
    )
}

@Composable
fun FSDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
) {
    if (!visible) return
    Dialog(onDismissRequest = onDismissRequest) {
        FSGlassCard(
            modifier = modifier.widthIn(max = 420.dp),
            shape = FrostSoulTheme.shapes.extraLarge,
        ) {
            FSText(title, style = FrostSoulTheme.typography.title, color = FrostSoulTheme.colors.onSurface)
            Spacer(Modifier.height(FrostSoulTheme.spacing.small))
            FSText(message, style = FrostSoulTheme.typography.body, color = FrostSoulTheme.colors.onSurfaceMuted)
            Spacer(Modifier.height(FrostSoulTheme.spacing.section))
            Row(horizontalArrangement = Arrangement.spacedBy(FrostSoulTheme.spacing.small), modifier = Modifier.fillMaxWidth()) {
                if (dismissLabel != null) {
                    FSButton(
                        label = dismissLabel,
                        onClick = onDismissRequest,
                        emphasized = false,
                        modifier = Modifier.weight(1f),
                    )
                }
                FSButton(
                    label = confirmLabel,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun FSSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    FSGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = FrostSoulTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = FrostSoulTheme.spacing.large, vertical = FrostSoulTheme.spacing.medium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FSText(
                text = message,
                style = FrostSoulTheme.typography.body,
                color = FrostSoulTheme.colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                FSButton(label = actionLabel, onClick = onAction, emphasized = false)
            }
        }
    }
}

@Composable
fun FSLoading(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    val transition: InfiniteTransition = rememberInfiniteTransition(label = "frostSoulLoading")
    val accentColor = FrostSoulTheme.colors.accentBright
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "frostSoulLoadingRotation",
    )
    Canvas(modifier = modifier.size(size).rotate(rotation)) {
        drawArc(
            color = accentColor,
            startAngle = 18f,
            sweepAngle = 282f,
            useCenter = false,
            style = Stroke(width = this.size.minDimension * 0.11f, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun FSEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(FrostSoulTheme.spacing.hero),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(88.dp).clip(CircleShape).background(FrostSoulTheme.colors.accent.copy(alpha = 0.12f)).frostSoulGlow(),
        ) { icon?.invoke() }
        Spacer(Modifier.height(FrostSoulTheme.spacing.large))
        FSText(title, color = FrostSoulTheme.colors.onSurface, style = FrostSoulTheme.typography.title)
        Spacer(Modifier.height(FrostSoulTheme.spacing.small))
        FSText(
            text = message,
            color = FrostSoulTheme.colors.onSurfaceMuted,
            style = FrostSoulTheme.typography.body,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(FrostSoulTheme.spacing.section))
            FSButton(label = actionLabel, onClick = onAction)
        }
    }
}
