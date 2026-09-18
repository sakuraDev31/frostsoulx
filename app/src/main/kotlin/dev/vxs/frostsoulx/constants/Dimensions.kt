/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.constants

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val CONTENT_TYPE_HEADER = 0
const val CONTENT_TYPE_LIST = 1
const val CONTENT_TYPE_SONG = 2
const val CONTENT_TYPE_ARTIST = 3
const val CONTENT_TYPE_ALBUM = 4
const val CONTENT_TYPE_PLAYLIST = 5

val NavigationBarHorizontalPadding = 12.dp
val NavigationBarBottomPadding = 10.dp
val NavigationBarMaxWidth = 420.dp
// QQ reference APK exposes 58dip normal and 70dip expanded bottom-navigation heights.
// The shell adds system-bar padding separately, so the content bar remains 58dp.
val NavigationBarHeight = 58.dp
// The reconstructed QQ-style collapsed row is 72dp; keep shell insets and sheet anchors aligned.
val MiniPlayerHeight = 68.dp
val MiniPlayerPeekHeight = 106.dp
val MiniPlayerBottomSpacing = 0.dp
val QueuePeekHeight = 64.dp
val AppBarHeight = 64.dp

val ListItemHeight = 64.dp
val SuggestionItemHeight = 52.dp
val SearchFilterHeight = 48.dp
val ListThumbnailSize = 56.dp
val SmallGridThumbnailHeight = 104.dp
val GridThumbnailHeight = 128.dp
val AlbumThumbnailSize = 144.dp

val ThumbnailCornerRadius = 10.dp
val GridThumbnailCornerRadius = 8.dp

// The reference phone shell uses a compact 16dp content inset around the player.
val PlayerHorizontalPadding = 16.dp

val NavigationBarAnimationSpec =
    spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )

val BottomSheetAnimationSpec =
    spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

val BottomSheetSoftAnimationSpec =
    spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )
