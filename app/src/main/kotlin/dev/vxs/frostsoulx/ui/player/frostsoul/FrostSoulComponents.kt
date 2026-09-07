/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.ui.player.frostsoul

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.ui.frostsoul.FSIcon
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.isActive

/**
 * How far the tonearm swings off the record when playback stops.
 *
 * Negative = counter-clockwise in Canvas space, which moves the authored down-left arm
 * OUTWARD toward the deck's right edge and onto its rest post.
 *
 * Derivation (all values as fractions of the square deck card, y growing downward):
 *   pivot  = (0.828, 0.176)      playing stylus = (0.663, 0.722)
 *   arm    = (-0.165, 0.546) → span 0.570, bearing atan2(0.546, -0.165) ≈ 106.8°
 *   record = centre (0.5, 0.5), radius 268/330/2 = 0.406, label radius 126/330/2 = 0.191
 * Playing: |stylus - centre| = 0.276, i.e. between the label and the rim → on the grooves.
 * Parked at -22°: bearing 84.8° → stylus (0.880, 0.744), |· - centre| = 0.452 > 0.406, so the
 * headshell clears the rim by ~0.046 of the card (~15.dp at the 330.dp card) and still sits
 * inside the deck. Anything past about -26° would push the tip off the card's right edge.
 */
private const val TonearmParkedDegrees = -22f

@Composable
internal fun FSGlassCard(
    modifier: Modifier = Modifier,
    accent: Color = Color.White,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(FrostSoulSurface.copy(alpha = 0.98f))
                .border(
                    width = 1.dp,
                    color = accent.copy(alpha = 0.16f),
                    shape = shape,
                ),
        content = content,
    )
}

@Composable
internal fun FSIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
    buttonSize: Dp = if (compact) 42.dp else 50.dp,
    iconSize: Dp = if (compact) 20.dp else 23.dp,
    showContainer: Boolean = true,
    forceWhite: Boolean = false,
    dimBackdrop: Boolean = true,
    tintOverride: Color? = null,
) {
    val isLightTheme = FrostSoulTheme.colors.background.luminance() > 0.5f
    // Containerless icons (showContainer = false) sit directly on the artwork / ambient-blur
    // backdrop, which stays dark-ish regardless of the app's light/dark theme setting. Only
    // let the theme flip the tint to a dark color when there is an actual background chip
    // behind the icon (showContainer = true) that also flips color for contrast — otherwise
    // always keep it near-white so it never disappears in light theme. See FS-BUG-LIGHTMODE.
    val baseTint =
        if (isLightTheme && showContainer) FrostSoulTheme.colors.onSurface else Color.White
    val iconTint =
        tintOverride ?: if (forceWhite) {
            Color.White
        } else {
            when {
                !enabled -> baseTint.copy(alpha = 0.28f)
                active -> FrostSoulTheme.colors.accentBright
                else -> baseTint
            }
        }
    val background =
        if (active) {
            baseTint.copy(alpha = if (isLightTheme && showContainer) 0.10f else 0.14f)
        } else {
            if (isLightTheme && showContainer) FrostSoulTheme.colors.surfaceRaised else Color.White.copy(alpha = 0.08f)
        }
    val buttonModifier = modifier.size(buttonSize)
    val styledModifier =
        if (forceWhite) {
            buttonModifier
        } else if (showContainer) {
            buttonModifier
                .clip(CircleShape)
                .background(background)
                .border(1.dp, iconTint.copy(alpha = if (active) 0.52f else 0.15f), CircleShape)
        } else if (dimBackdrop) {
            // No chip behind the icon, so give it a faint dark scrim disc instead — enough to
            // guarantee contrast over bright artwork or a light-themed backdrop without
            // looking like a full button, matching the reference UI's soft icon shadowing.
            buttonModifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = if (active) 0.0f else 0.16f))
        } else {
            buttonModifier
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            styledModifier
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).semantics { this.contentDescription = contentDescription },
    ) {
        FSIcon(
            painter = painter,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
internal fun FSAlbumArt(
    artworkUrl: String?,
    title: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    // A cancellable Animatable is essential here. An infinite transition keeps advancing while
    // paused, so snapshotting its value on pause makes resume jump to a later angle. Cancelling
    // this animation preserves the exact in-flight value; the next play starts from that value.
    val rotation = remember(artworkUrl) { Animatable(0f) }
    LaunchedEffect(artworkUrl, isPlaying) {
        if (isPlaying) {
            while (isActive) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = 26_000, easing = LinearEasing),
                )
            }
        }
    }
    // Playing → the stylus tracks a groove in the record's outer band (angle 0 = the resting
    // geometry authored below). Off → the arm swings OUTWARD to the right and parks on its
    // rest post, clear of the record, exactly like the reference deck.
    //
    // Canvas rotate() is clockwise-positive, and the authored arm already points down-left
    // (~7 o'clock) from its top-right pivot, so clockwise would drag it further LEFT across
    // the label. Parking therefore needs a NEGATIVE (counter-clockwise) angle — this is the
    // bug that used to swing the arm the wrong way and drop the stylus onto the artwork.
    val tonearmAngle by animateFloatAsState(
        targetValue = if (isPlaying) 0f else TonearmParkedDegrees,
        animationSpec = tween(durationMillis = 560),
        label = "fs-tonearm-angle",
    )

    if (compact) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(FrostSoulSurfaceElevated),
        ) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = "Album artwork for $title",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (artworkUrl.isNullOrBlank()) {
                FSIcon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = FrostSoulOnSurfaceMuted,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        return
    }

    val cardShape = RoundedCornerShape(26.dp)
    val cardSize = PlayerLayoutTokens.TurntableCardSize.value
    val platterFraction = PlayerLayoutTokens.TurntablePlatterSize.value / cardSize
    val labelFraction = PlayerLayoutTokens.TurntableLabelSize.value / cardSize
    val labelArtFraction = PlayerLayoutTokens.TurntableLabelArtSize.value / cardSize

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .shadow(elevation = 18.dp, shape = cardShape, clip = false)
            .clip(cardShape)
            .background(
                // Deck plate: pushed much darker than before so the silver record and the
                // tonearm read with real contrast against the body (was a washed mid-grey).
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1B1B1F), Color(0xFF0B0B0E), Color(0xFF040406)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.055f), cardShape),
    ) {
        // Top-left key light on the plate, then a vignette that sinks the corners. Together
        // they give the flat card a machined, slightly domed metal feel.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.055f), Color.Transparent),
                ),
            ),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    center = center,
                    radius = size.minDimension * 0.72f,
                ),
            )
        }

        // A soft platter well separates the record from the deck without adding another hard ring.
        Box(
            modifier = Modifier
                .fillMaxSize(platterFraction + 0.035f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF14151A), Color(0xFF050507), Color.Black.copy(alpha = 0.92f)),
                    ),
                ),
        )

        // Spinning record: sized relative to the deck so it never overflows the card.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize(platterFraction)
                .graphicsLayer { rotationZ = rotation.value },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val platterRadius = size.minDimension / 2f
                val labelRadius = platterRadius *
                    (PlayerLayoutTokens.TurntableLabelSize.value / PlayerLayoutTokens.TurntablePlatterSize.value)

                // Metallic platter body: a cool graphite/silver material rather than a flat
                // black disc. The fixed colour stops are intentional; they form the reference's
                // precomputed metal response without allocating or animating a new brush per
                // frame.
                drawCircle(
                    brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0xFF56606B),
                            0.16f to Color(0xFFB9C1CA),
                            0.31f to Color(0xFF707B87),
                            0.48f to Color(0xFFD5DAE0),
                            0.66f to Color(0xFF626D79),
                            0.82f to Color(0xFF9EA8B3),
                            1.00f to Color(0xFF454E59),
                        ),
                        start = Offset(0f, platterRadius * 0.12f),
                        end = Offset(size.width, platterRadius * 0.92f),
                    ),
                    radius = platterRadius,
                    center = center,
                )

                // Precalculated anisotropic reflection inside the rotating disc. It is subtle
                // enough to keep the grooves readable, but gives the platter the brushed-metal
                // sweep visible in the reference instead of a painted white arc.
                drawCircle(
                    brush = Brush.sweepGradient(
                        0.00f to Color.Transparent,
                        0.10f to Color.White.copy(alpha = 0.16f),
                        0.18f to Color.Transparent,
                        0.43f to Color.Transparent,
                        0.52f to Color.White.copy(alpha = 0.11f),
                        0.62f to Color.Transparent,
                        0.84f to Color.Transparent,
                        0.91f to Color.Black.copy(alpha = 0.13f),
                        1.00f to Color.Transparent,
                        center = center,
                    ),
                    radius = platterRadius * 0.84f,
                    center = center,
                    style = Stroke(width = platterRadius * 0.16f),
                )

                // Fine concentric grooves. Low contrast and crowding toward the rim, so the
                // surface reads as a pressing rather than as drawn-on rings.
                val grooveInner = labelRadius + 2.dp.toPx()
                val grooveOuter = platterRadius * 0.972f
                val grooveCount = 58
                for (index in 0 until grooveCount) {
                    val t = index / (grooveCount - 1f)
                    // eased(t) = t(2 - t): spacing shrinks as it approaches the rim.
                    val eased = t * (2f - t)
                    val ringRadius = grooveInner + (grooveOuter - grooveInner) * eased
                    drawCircle(
                        color = Color.White.copy(alpha = 0.026f + 0.024f * (1f - t)),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = 0.6.dp.toPx()),
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.20f),
                        radius = ringRadius + 0.6.dp.toPx(),
                        center = center,
                        style = Stroke(width = 0.6.dp.toPx()),
                    )
                }

                // Wider matte bands that stand in for the gaps between pressed tracks.
                for (band in listOf(0.42f, 0.63f, 0.82f)) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.30f),
                        radius = grooveInner + (grooveOuter - grooveInner) * band,
                        center = center,
                        style = Stroke(width = 1.6.dp.toPx()),
                    )
                }

                // Rim: bright outer lip over a dark bevel so the disc has thickness.
                drawCircle(
                    color = Color.Black.copy(alpha = 0.55f),
                    radius = platterRadius - 1.dp.toPx(),
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.13f),
                    radius = platterRadius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
                // Shadow the grooves cast onto the paper label edge.
                drawCircle(
                    color = Color.Black.copy(alpha = 0.42f),
                    radius = labelRadius + 1.5.dp.toPx(),
                    center = center,
                    style = Stroke(width = 3.dp.toPx()),
                )
            }

            // Paper label pressed onto the record, with the artwork clipped to a circle inside
            // it — the thumbnail now lives *within* the circular area instead of floating as a
            // square polaroid over the disc.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize(labelFraction / platterFraction)
                    .clip(CircleShape)
                    .background(Color(0xFFF6F3EC)),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize(labelArtFraction / labelFraction)
                        .clip(CircleShape)
                        .background(FrostSoulSurfaceElevated),
                ) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = "Album artwork for $title",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (artworkUrl.isNullOrBlank()) {
                        FSIcon(
                            painter = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            tint = FrostSoulOnSurfaceMuted,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                // Inner edge line where the paper label meets the artwork.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, Color.Black.copy(alpha = 0.22f), CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(PlayerLayoutTokens.TurntableSpindleSize)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFF4F5F7), Color(0xFF888D95), Color(0xFF26282D)),
                            ),
                        )
                        .border(0.5.dp, Color.White.copy(alpha = 0.38f), CircleShape),
                )
            }

        }

        // Anisotropic gloss, deliberately OUTSIDE the rotating layer: on a real deck the
        // reflection is cast by the room light, so it stays put while the record spins under
        // it. Clipping it to the groove annulus keeps the label crisp. This replaces the old
        // hard white arc that rotated with the disc and looked painted on.
        Box(
            modifier = Modifier.fillMaxSize(platterFraction).clip(CircleShape),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val platterRadius = size.minDimension / 2f
                val labelRadius = platterRadius *
                    (PlayerLayoutTokens.TurntableLabelSize.value / PlayerLayoutTokens.TurntablePlatterSize.value)
                val bandWidth = platterRadius - labelRadius
                val bandRadius = labelRadius + bandWidth / 2f
                // Two cool sweeps and one warm one, stroked over the groove band only.
                drawCircle(
                    brush = Brush.sweepGradient(
                        0.00f to Color.Transparent,
                        0.10f to Color.White.copy(alpha = 0.10f),
                        0.20f to Color.Transparent,
                        0.52f to Color.Transparent,
                        0.60f to Color.White.copy(alpha = 0.075f),
                        0.70f to Color.Transparent,
                        1.00f to Color.Transparent,
                        center = center,
                    ),
                    radius = bandRadius,
                    center = center,
                    style = Stroke(width = bandWidth),
                )
                drawCircle(
                    brush = Brush.sweepGradient(
                        0.00f to Color.Transparent,
                        0.30f to Color(0xFFE8CCA4).copy(alpha = 0.06f),
                        0.40f to Color.Transparent,
                        0.80f to Color.Transparent,
                        0.90f to Color(0xFFBCD2E6).copy(alpha = 0.05f),
                        1.00f to Color.Transparent,
                        center = center,
                    ),
                    radius = bandRadius,
                    center = center,
                    style = Stroke(width = bandWidth),
                )
            }
        }

        // ── Tonearm ──────────────────────────────────────────────────────────────────────
        // Geometry is written as fractions of the deck card, so the whole assembly scales with
        // whatever width the player page hands us. Two states, matching the reference deck:
        //   playing → the stylus sits on the GROOVES (never over the label artwork);
        //   stopped → the arm swings OUTWARD to the right and parks on its rest post.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val deck = size.minDimension
            val pivot = Offset(size.width * 0.838f, size.height * 0.168f)
            // Playing-state stylus target: in the outer third of the groove band, lower-right.
            // The shorter reach keeps the arm proportional on narrow phones instead of stretched.
            val playingNeedle = Offset(size.width * 0.668f, size.height * 0.692f)
            val armVector = playingNeedle - pivot
            val armSpan = hypot(armVector.x.toDouble(), armVector.y.toDouble()).toFloat()
            if (armSpan <= 0f) return@Canvas
            val armUnit = Offset(armVector.x / armSpan, armVector.y / armSpan)
            // Screen-space perpendicular pointing outward, toward the deck's right edge.
            val armNormal = Offset(armUnit.y, -armUnit.x)
            val armDegrees = (atan2(armUnit.y.toDouble(), armUnit.x.toDouble()) * 180.0 / PI).toFloat()

            val headshellSize = PlayerLayoutTokens.TurntableHeadshellSize.toPx()
            val joint = playingNeedle - armUnit * (deck * 0.052f)
            val jointVector = joint - pivot

            fun onArm(t: Float, bow: Float): Offset = Offset(
                pivot.x + jointVector.x * t + armNormal.x * bow * deck,
                pivot.y + jointVector.y * t + armNormal.y * bow * deck,
            )

            fun rotatedAround(point: Offset, about: Offset, degrees: Float): Offset {
                val radians = degrees * PI.toFloat() / 180f
                val dx = point.x - about.x
                val dy = point.y - about.y
                val c = cos(radians)
                val s = sin(radians)
                return Offset(about.x + dx * c - dy * s, about.y + dx * s + dy * c)
            }

            // Rest post: derived from the parked arm angle, so the headshell always lands on it.
            val pegCenter = rotatedAround(playingNeedle, pivot, TonearmParkedDegrees)
            drawCircle(
                color = Color.Black.copy(alpha = 0.45f),
                radius = PlayerLayoutTokens.TurntableRestPegSize.toPx() * 0.78f,
                center = pegCenter,
            )
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF6C6C76), Color(0xFF232329)),
                    start = Offset(pegCenter.x, pegCenter.y - PlayerLayoutTokens.TurntableRestPegSize.toPx()),
                    end = Offset(pegCenter.x, pegCenter.y + PlayerLayoutTokens.TurntableRestPegSize.toPx()),
                ),
                radius = PlayerLayoutTokens.TurntableRestPegSize.toPx() / 2f,
                center = pegCenter,
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.55f),
                radius = PlayerLayoutTokens.TurntableRestPegSize.toPx() * 0.24f,
                center = pegCenter,
            )

            // Static mount block the arm is bolted onto.
            val baseRadius = PlayerLayoutTokens.TurntableTonearmBaseSize.toPx() / 2f
            drawCircle(
                color = Color.Black.copy(alpha = 0.50f),
                radius = baseRadius * 1.06f,
                center = Offset(pivot.x, pivot.y + 2.dp.toPx()),
            )
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF3A3A42), Color(0xFF121216)),
                    start = Offset(pivot.x - baseRadius, pivot.y - baseRadius),
                    end = Offset(pivot.x + baseRadius, pivot.y + baseRadius),
                ),
                radius = baseRadius,
                center = pivot,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = baseRadius,
                center = pivot,
                style = Stroke(width = 1.dp.toPx()),
            )

            withTransform({ rotate(tonearmAngle, pivot) }) {
                val tubeBrush = Brush.linearGradient(
                    colors = listOf(Color(0xFFB9BAC2), Color(0xFF6E6F78), Color(0xFF2A2A30)),
                    start = onArm(0f, -0.010f),
                    end = onArm(1f, 0.012f),
                )

                // Counterweight barrel hanging off the back of the arm.
                val weightCenter = pivot - armUnit * (deck * 0.058f)
                val weightLength = PlayerLayoutTokens.TurntableCounterweightSize.toPx() * 1.5f
                val weightWidth = PlayerLayoutTokens.TurntableCounterweightSize.toPx()
                drawLine(
                    brush = tubeBrush,
                    start = pivot,
                    end = weightCenter,
                    strokeWidth = 3.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                withTransform({ rotate(armDegrees - 90f, weightCenter) }) {
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFCFD0D8), Color(0xFF7A7B85), Color(0xFF31313A)),
                            start = Offset(weightCenter.x - weightWidth / 2f, weightCenter.y),
                            end = Offset(weightCenter.x + weightWidth / 2f, weightCenter.y),
                        ),
                        topLeft = Offset(
                            weightCenter.x - weightWidth / 2f,
                            weightCenter.y - weightLength / 2f,
                        ),
                        size = Size(weightWidth, weightLength),
                        cornerRadius = CornerRadius(weightWidth * 0.32f),
                    )
                    // Machined rings on the weight.
                    for (ridge in -1..1) {
                        val ridgeY = weightCenter.y + ridge * weightLength * 0.24f
                        drawLine(
                            color = Color.Black.copy(alpha = 0.32f),
                            start = Offset(weightCenter.x - weightWidth / 2f, ridgeY),
                            end = Offset(weightCenter.x + weightWidth / 2f, ridgeY),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }

                // Main tube: a gentle J, straight off the pivot then curving back to the
                // headshell, drawn as shadow + body + specular highlight.
                val tubePath = Path().apply {
                    val c1 = onArm(0.34f, 0.006f)
                    val c2 = onArm(0.76f, 0.062f)
                    moveTo(pivot.x, pivot.y)
                    cubicTo(c1.x, c1.y, c2.x, c2.y, joint.x, joint.y)
                }
                val tubeShadowPath = Path().apply {
                    val o = armNormal * (1.6.dp.toPx())
                    val c1 = onArm(0.34f, 0.006f) + o
                    val c2 = onArm(0.76f, 0.062f) + o
                    moveTo(pivot.x + o.x, pivot.y + o.y)
                    cubicTo(c1.x, c1.y, c2.x, c2.y, joint.x + o.x, joint.y + o.y)
                }
                val tubeHighlightPath = Path().apply {
                    val o = armNormal * (-1.3.dp.toPx())
                    val c1 = onArm(0.34f, 0.006f) + o
                    val c2 = onArm(0.76f, 0.062f) + o
                    moveTo(pivot.x + o.x, pivot.y + o.y)
                    cubicTo(c1.x, c1.y, c2.x, c2.y, joint.x + o.x, joint.y + o.y)
                }
                drawPath(
                    path = tubeShadowPath,
                    color = Color.Black.copy(alpha = 0.45f),
                    style = Stroke(width = 5.8.dp.toPx(), cap = StrokeCap.Round),
                )
                drawPath(
                    path = tubePath,
                    brush = tubeBrush,
                    style = Stroke(width = 4.6.dp.toPx(), cap = StrokeCap.Round),
                )
                drawPath(
                    path = tubeHighlightPath,
                    color = Color.White.copy(alpha = 0.30f),
                    style = Stroke(width = 0.9.dp.toPx(), cap = StrokeCap.Round),
                )

                // Pivot bearing on top of the tube root.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF4E4E58), Color(0xFF0E0E12)),
                        center = pivot,
                        radius = PlayerLayoutTokens.TurntableTonearmMountSize.toPx() / 2f,
                    ),
                    radius = PlayerLayoutTokens.TurntableTonearmMountSize.toPx() / 2f,
                    center = pivot,
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f),
                    radius = PlayerLayoutTokens.TurntableTonearmMountSize.toPx() / 2f,
                    center = pivot,
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawCircle(
                    color = Color(0xFF17171C),
                    radius = PlayerLayoutTokens.TurntableTonearmMountSize.toPx() * 0.22f,
                    center = pivot,
                )

                // Headshell + cartridge block at the tip, aligned with the arm.
                val headCenter = Offset(
                    (joint.x + playingNeedle.x) / 2f,
                    (joint.y + playingNeedle.y) / 2f,
                )
                withTransform({ rotate(armDegrees - 90f, headCenter) }) {
                    val headWidth = headshellSize
                    val headLength = headshellSize * 1.85f
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.42f),
                        topLeft = Offset(
                            headCenter.x - headWidth / 2f + 1.2.dp.toPx(),
                            headCenter.y - headLength / 2f + 1.2.dp.toPx(),
                        ),
                        size = Size(headWidth, headLength),
                        cornerRadius = CornerRadius(headWidth * 0.42f),
                    )
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF43434C), Color(0xFF141418)),
                            start = Offset(headCenter.x - headWidth / 2f, headCenter.y),
                            end = Offset(headCenter.x + headWidth / 2f, headCenter.y),
                        ),
                        topLeft = Offset(
                            headCenter.x - headWidth / 2f,
                            headCenter.y - headLength / 2f,
                        ),
                        size = Size(headWidth, headLength),
                        cornerRadius = CornerRadius(headWidth * 0.42f),
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.22f),
                        start = Offset(headCenter.x - headWidth * 0.28f, headCenter.y - headLength * 0.36f),
                        end = Offset(headCenter.x - headWidth * 0.28f, headCenter.y + headLength * 0.30f),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                // Stylus: a short spike off the headshell, lit only while it tracks a groove.
                val stylusTip = playingNeedle + armUnit * (deck * 0.012f)
                drawLine(
                    color = Color(0xFF0E0E12),
                    start = playingNeedle,
                    end = stylusTip,
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = if (isPlaying) Color(0xFFD9F3F7) else Color(0xFF7C848A),
                    radius = 2.6.dp.toPx(),
                    center = stylusTip,
                )
                if (isPlaying) {
                    drawCircle(
                        color = Color(0xFFD9F3F7).copy(alpha = 0.20f),
                        radius = 6.5.dp.toPx(),
                        center = stylusTip,
                    )
                }
            }
        }

        // Source badge in the deck corner.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp).size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF08080A))
                .border(1.dp, Color.White.copy(alpha = 0.07f), CircleShape),
        ) {
            FSIcon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = "Audio source",
                tint = FrostSoulTheme.colors.accentBright,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
internal fun FSSeekbar(
    progress: Float,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color.White,
    isEnabled: Boolean = durationMs > 0L,
    onDraggingChanged: (Boolean) -> Unit = {},
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var dragProgress by remember { mutableFloatStateOf(progress) }
    var isDragging by remember { mutableStateOf(false) }
    val visibleProgress = (if (isDragging) dragProgress else progress).coerceIn(0f, 1f)
    val targetDuration = durationMs.coerceAtLeast(1L)

    fun seekAt(x: Float) {
        if (!isEnabled || containerSize.width == 0) return
        val fraction = (x / containerSize.width.toFloat()).coerceIn(0f, 1f)
        dragProgress = fraction
        onSeek((targetDuration * fraction).toLong())
    }

    Box(
        modifier =
            modifier
                .height(30.dp)
                .fillMaxWidth()
                .onSizeChanged { containerSize = it }
                .pointerInput(isEnabled, targetDuration) {
                    detectTapGestures { offset -> seekAt(offset.x) }
                }.pointerInput(isEnabled, targetDuration) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            onDraggingChanged(true)
                            seekAt(offset.x)
                        },
                        onDragEnd = {
                            isDragging = false
                            onDraggingChanged(false)
                        },
                        onDragCancel = {
                            isDragging = false
                            onDraggingChanged(false)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            seekAt(change.position.x)
                        },
                    )
                }.semantics { contentDescription = "Playback progress" },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackHeight = 5.dp.toPx()
            val y = this.size.height / 2f
            val trackStart = Offset(0f, y)
            val trackEnd = Offset(this.size.width, y)
            val activeEnd = Offset(this.size.width * visibleProgress, y)
            drawLine(
                color = Color.White.copy(alpha = 0.16f),
                start = trackStart,
                end = trackEnd,
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = accent,
                start = trackStart,
                end = activeEnd,
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = Color.White,
                radius = if (isDragging) 7.dp.toPx() else 5.dp.toPx(),
                center = activeEnd,
            )
        }
    }
}

@Composable
internal fun FSTopBar(
    selectedPage: Int,
    pageOffsetFraction: Float,
    pageCount: Int,
    onPageSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        FSIconButton(
            painter = painterResource(R.drawable.expand_less),
            contentDescription = "Collapse player",
            onClick = onDismiss,
            compact = true,
        )
        Spacer(Modifier.width(12.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1f),
        ) {
            FrostSoulPagerDots(
                pageCount = pageCount,
                selectedPage = selectedPage,
                selectedPageOffsetFraction = pageOffsetFraction,
                onPageSelected = onPageSelected,
            )
        }
    }
}
