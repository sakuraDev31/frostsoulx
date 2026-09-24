/*
 * FrostSoulX taste profile.
 * Built on-device from local listening history; nothing in here leaves the phone.
 */

package dev.vxs.frostsoulx.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import dev.vxs.frostsoulx.LocalPlayerAwareWindowInsets
import dev.vxs.frostsoulx.R
import dev.vxs.frostsoulx.taste.ArtistAffinity
import dev.vxs.frostsoulx.taste.TasteAxisId
import dev.vxs.frostsoulx.taste.TasteAxisScore
import dev.vxs.frostsoulx.taste.TasteProfile
import dev.vxs.frostsoulx.ui.component.IconButton
import dev.vxs.frostsoulx.ui.frostsoul.FSEmptyState
import dev.vxs.frostsoulx.ui.frostsoul.FSLoading
import dev.vxs.frostsoulx.ui.frostsoul.FrostSoulTheme
import dev.vxs.frostsoulx.ui.premium.PremiumCard
import dev.vxs.frostsoulx.ui.premium.PremiumTopBar
import dev.vxs.frostsoulx.ui.utils.backToMain
import dev.vxs.frostsoulx.viewmodels.TasteProfileScreenState
import dev.vxs.frostsoulx.viewmodels.TasteProfileViewModel
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TasteProfileScreen(
    navController: NavController,
    viewModel: TasteProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PremiumTopBar(
                title = stringResource(R.string.taste_profile_title),
                eyebrow = "FROSTSOULX",
                modifier = Modifier.statusBarsPadding(),
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val current = state) {
            TasteProfileScreenState.Loading ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    FSLoading()
                }

            TasteProfileScreenState.Empty ->
                FSEmptyState(
                    title = stringResource(R.string.taste_profile_empty_title),
                    message = stringResource(R.string.taste_profile_empty_message),
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )

            is TasteProfileScreenState.Error ->
                FSEmptyState(
                    title = stringResource(R.string.taste_profile_title),
                    message = stringResource(current.messageResId),
                    actionLabel = stringResource(R.string.taste_profile_retry),
                    onAction = viewModel::retry,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )

            is TasteProfileScreenState.Success ->
                TasteProfileContent(
                    profile = current.profile,
                    topPadding = innerPadding.calculateTopPadding(),
                )
        }
    }
}

@Composable
private fun TasteProfileContent(
    profile: TasteProfile,
    topPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ),
        contentPadding =
            PaddingValues(
                start = SettingsDimensions.ScreenHorizontalPadding,
                end = SettingsDimensions.ScreenHorizontalPadding,
                top = topPadding + 8.dp,
                bottom = SettingsDimensions.ScreenBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(FrostSoulTheme.spacing.large),
    ) {
        item(key = "summary", contentType = "taste_card") {
            TasteSummaryCard(profile = profile)
        }
        if (profile.axes.count { it.hasData } >= MinAxesForRadar) {
            item(key = "radar", contentType = "taste_card") {
                TasteRadarCard(axes = profile.axes)
            }
            item(key = "axes", contentType = "taste_card") {
                TasteAxesCard(axes = profile.axes)
            }
        }
        if (profile.topArtists.isNotEmpty()) {
            item(key = "artists", contentType = "taste_card") {
                TopArtistsCard(artists = profile.topArtists)
            }
        }
        if (profile.playCount > 0) {
            item(key = "when", contentType = "taste_card") {
                ListeningTimeCard(hourlyShare = profile.hourlyShare, weekdayShare = profile.weekdayShare)
            }
            item(key = "fingerprint", contentType = "taste_card") {
                FingerprintCard(profile = profile)
            }
        }
        item(key = "footer", contentType = "taste_footer") {
            Text(
                text = stringResource(R.string.taste_profile_footer),
                style = FrostSoulTheme.typography.bodyMuted,
                color = FrostSoulTheme.colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = FrostSoulTheme.spacing.large),
            )
        }
    }
}

@Composable
private fun TasteSummaryCard(
    profile: TasteProfile,
    modifier: Modifier = Modifier,
) {
    val traitNames = profile.dominantTraits.map { trait -> stringResource(trait.traitRes()) }
    val headline =
        if (traitNames.isEmpty()) {
            stringResource(R.string.taste_profile_trait_none)
        } else {
            traitNames.joinToString(separator = " · ")
        }
    val strength = (profile.confidence * 100f).roundToInt()

    PremiumCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.taste_profile_headline_label),
            style = FrostSoulTheme.typography.overline,
            color = FrostSoulTheme.colors.onSurfaceMuted,
        )
        Spacer(Modifier.height(FrostSoulTheme.spacing.small))
        Text(
            text = headline,
            style = FrostSoulTheme.typography.title,
            color = FrostSoulTheme.colors.onSurface,
        )
        Spacer(Modifier.height(FrostSoulTheme.spacing.large))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.taste_profile_strength),
                style = FrostSoulTheme.typography.label,
                color = FrostSoulTheme.colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.taste_profile_percent, strength),
                style = FrostSoulTheme.typography.label,
                color = FrostSoulTheme.colors.onSurfaceMuted,
            )
        }
        Spacer(Modifier.height(FrostSoulTheme.spacing.small))
        TasteBar(fraction = profile.confidence)
        Spacer(Modifier.height(FrostSoulTheme.spacing.small))
        Text(
            text = stringResource(R.string.taste_profile_plays_analysed, profile.playCount),
            style = FrostSoulTheme.typography.bodyMuted,
            color = FrostSoulTheme.colors.onSurfaceMuted,
        )
        if (profile.confidence < LearningThreshold) {
            Spacer(Modifier.height(FrostSoulTheme.spacing.small))
            Text(
                text = stringResource(R.string.taste_profile_learning_hint),
                style = FrostSoulTheme.typography.bodyMuted,
                color = FrostSoulTheme.colors.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun TasteRadarCard(
    axes: List<TasteAxisScore>,
    modifier: Modifier = Modifier,
) {
    val labels = axes.map { axis -> stringResource(axis.id.labelRes()) }
    val allTime = axes.map { axis -> if (axis.hasData) axis.score else 0f }
    val recentScores = axes.map { axis -> axis.recentScore }
    val recent = if (recentScores.count { it != null } >= MinAxesForRadar) recentScores.map { it ?: 0f } else null
    val summary =
        axes
            .mapIndexed { index, axis ->
                stringResource(
                    R.string.taste_profile_axis_summary,
                    labels[index],
                    (allTime[index] * 100f).roundToInt(),
                )
            }.joinToString(separator = ", ")
    val description = stringResource(R.string.taste_profile_radar_content_description, summary)

    PremiumCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.taste_profile_radar_title),
            style = FrostSoulTheme.typography.sectionTitle,
            color = FrostSoulTheme.colors.onSurface,
        )
        Text(
            text = stringResource(R.string.taste_profile_radar_subtitle),
            style = FrostSoulTheme.typography.bodyMuted,
            color = FrostSoulTheme.colors.onSurfaceMuted,
        )
        Spacer(Modifier.height(FrostSoulTheme.spacing.medium))
        TasteRadarChart(
            labels = labels,
            allTime = allTime,
            recent = recent,
            contentDescription = description,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(FrostSoulTheme.spacing.medium))
        Row(
            horizontalArrangement = Arrangement.spacedBy(FrostSoulTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendDot(color = FrostSoulTheme.colors.accentBright, label = stringResource(R.string.taste_profile_legend_all_time))
            if (recent != null) {
                LegendDot(color = FrostSoulTheme.colors.onSurface, label = stringResource(R.string.taste_profile_legend_recent))
            }
        }
    }
}

@Composable
private fun LegendDot(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(FrostSoulTheme.spacing.small))
        Text(
            text = label,
            style = FrostSoulTheme.typography.label,
            color = FrostSoulTheme.colors.onSurfaceMuted,
        )
    }
}

/**
 * Spider graph. The filled polygon is the all-time profile, the dashed outline the last 30 days.
 * Purely a renderer: every value arrives normalised to 0..1.
 */
@Composable
private fun TasteRadarChart(
    labels: List<String>,
    allTime: List<Float>,
    recent: List<Float>?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }
    val textMeasurer = rememberTextMeasurer()
    val gridColor = FrostSoulTheme.colors.outline.copy(alpha = 0.4f)
    val accent = FrostSoulTheme.colors.accentBright
    val recentColor = FrostSoulTheme.colors.onSurface
    val labelStyle = FrostSoulTheme.typography.label.copy(color = FrostSoulTheme.colors.onSurfaceMuted)

    Canvas(
        modifier =
            modifier
                .aspectRatio(1f)
                .semantics { this.contentDescription = contentDescription },
    ) {
        val count = labels.size
        if (count < MinAxesForRadar) return@Canvas
        val horizontalMargin = RadarHorizontalMargin.toPx()
        val verticalMargin = RadarVerticalMargin.toPx()
        val radius = min(size.width / 2f - horizontalMargin, size.height / 2f - verticalMargin).coerceAtLeast(0f)
        val center = Offset(size.width / 2f, size.height / 2f)
        val angleOf = { index: Int -> (-PI / 2.0 + 2.0 * PI * index / count).toFloat() }
        val pointAt = { index: Int, fraction: Float ->
            val angle = angleOf(index)
            Offset(center.x + cos(angle) * radius * fraction, center.y + sin(angle) * radius * fraction)
        }
        val polygon = { values: List<Float> ->
            Path().apply {
                values.forEachIndexed { index, value ->
                    val point = pointAt(index, (value * reveal.value).coerceIn(0f, 1f))
                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
                close()
            }
        }
        val hairline = 1.dp.toPx()

        for (ring in 1..RadarRings) {
            val ringPath =
                Path().apply {
                    for (index in 0 until count) {
                        val point = pointAt(index, ring / RadarRings.toFloat())
                        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                    close()
                }
            drawPath(path = ringPath, color = gridColor, style = Stroke(width = hairline))
        }
        for (index in 0 until count) {
            drawLine(color = gridColor, start = center, end = pointAt(index, 1f), strokeWidth = hairline)
        }

        val allTimePath = polygon(allTime)
        drawPath(path = allTimePath, color = accent.copy(alpha = 0.28f))
        drawPath(path = allTimePath, color = accent, style = Stroke(width = 2.dp.toPx()))
        allTime.forEachIndexed { index, value ->
            drawCircle(color = accent, radius = 3.dp.toPx(), center = pointAt(index, (value * reveal.value).coerceIn(0f, 1f)))
        }

        if (recent != null) {
            drawPath(
                path = polygon(recent),
                color = recentColor.copy(alpha = 0.9f),
                style =
                    Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())),
                    ),
            )
        }

        val gap = 8.dp.toPx()
        val maxLabelWidth = (horizontalMargin - gap).toInt().coerceAtLeast(1)
        labels.forEachIndexed { index, label ->
            val layout =
                textMeasurer.measure(
                    text = label,
                    style = labelStyle,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = maxLabelWidth),
                )
            val angle = angleOf(index)
            val anchor = pointAt(index, 1f)
            val dx = cos(angle)
            val dy = sin(angle)
            val x =
                when {
                    dx > SideThreshold -> anchor.x + gap
                    dx < -SideThreshold -> anchor.x - gap - layout.size.width
                    else -> anchor.x - layout.size.width / 2f
                }
            val y =
                when {
                    dy > SideThreshold -> anchor.y + gap
                    dy < -SideThreshold -> anchor.y - gap - layout.size.height
                    else -> anchor.y - layout.size.height / 2f
                }
            drawText(textLayoutResult = layout, topLeft = Offset(x, y))
        }
    }
}

@Composable
private fun TasteAxesCard(
    axes: List<TasteAxisScore>,
    modifier: Modifier = Modifier,
) {
    PremiumCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.taste_profile_axes_title),
            style = FrostSoulTheme.typography.sectionTitle,
            color = FrostSoulTheme.colors.onSurface,
        )
        axes.forEach { axis ->
            Spacer(Modifier.height(FrostSoulTheme.spacing.medium))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(axis.id.labelRes()),
                    style = FrostSoulTheme.typography.label,
                    color = FrostSoulTheme.colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text =
                        if (axis.hasData) {
                            stringResource(R.string.taste_profile_percent, (axis.score * 100f).roundToInt())
                        } else {
                            stringResource(R.string.taste_profile_axis_no_data)
                        },
                    style = FrostSoulTheme.typography.label,
                    color = FrostSoulTheme.colors.onSurfaceMuted,
                )
            }
            Spacer(Modifier.height(FrostSoulTheme.spacing.micro))
            TasteBar(fraction = if (axis.hasData) axis.score else 0f)
            Spacer(Modifier.height(FrostSoulTheme.spacing.micro))
            Text(
                text = stringResource(axis.id.descriptionRes()),
                style = FrostSoulTheme.typography.bodyMuted,
                color = FrostSoulTheme.colors.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun TopArtistsCard(
    artists: List<ArtistAffinity>,
    modifier: Modifier = Modifier,
) {
    PremiumCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.taste_profile_top_artists_title),
            style = FrostSoulTheme.typography.sectionTitle,
            color = FrostSoulTheme.colors.onSurface,
        )
        Text(
            text = stringResource(R.string.taste_profile_top_artists_subtitle),
            style = FrostSoulTheme.typography.bodyMuted,
            color = FrostSoulTheme.colors.onSurfaceMuted,
        )
        artists.forEach { artist ->
            Spacer(Modifier.height(FrostSoulTheme.spacing.medium))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = artist.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(FrostSoulTheme.colors.surfaceRaised),
                )
                Spacer(Modifier.size(FrostSoulTheme.spacing.medium))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = artist.name,
                            style = FrostSoulTheme.typography.label,
                            color = FrostSoulTheme.colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.taste_profile_percent, (artist.share * 100f).roundToInt()),
                            style = FrostSoulTheme.typography.label,
                            color = FrostSoulTheme.colors.onSurfaceMuted,
                        )
                    }
                    Spacer(Modifier.height(FrostSoulTheme.spacing.micro))
                    TasteBar(fraction = artist.share)
                }
            }
        }
    }
}

@Composable
private fun ListeningTimeCard(
    hourlyShare: List<Float>,
    weekdayShare: List<Float>,
    modifier: Modifier = Modifier,
) {
    val peakHour = hourlyShare.indices.maxByOrNull { hourlyShare[it] }
    val peakLabel =
        peakHour?.takeIf { hourlyShare[it] > 0f }?.let { hour ->
            remember(hour) { LocalTime.of(hour, 0).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) }
        }
    val dayLabels =
        remember {
            DayOfWeek.values().map { day -> day.getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
        }

    PremiumCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.taste_profile_when_title),
            style = FrostSoulTheme.typography.sectionTitle,
            color = FrostSoulTheme.colors.onSurface,
        )
        if (peakLabel != null) {
            Text(
                text = stringResource(R.string.taste_profile_peak_hour, peakLabel),
                style = FrostSoulTheme.typography.bodyMuted,
                color = FrostSoulTheme.colors.onSurfaceMuted,
            )
        }
        Spacer(Modifier.height(FrostSoulTheme.spacing.medium))
        MiniBars(values = hourlyShare, modifier = Modifier.fillMaxWidth().height(72.dp))
        Spacer(Modifier.height(FrostSoulTheme.spacing.large))
        MiniBars(values = weekdayShare, modifier = Modifier.fillMaxWidth().height(48.dp))
        Spacer(Modifier.height(FrostSoulTheme.spacing.micro))
        Row(modifier = Modifier.fillMaxWidth()) {
            dayLabels.forEach { day ->
                Text(
                    text = day,
                    style = FrostSoulTheme.typography.label,
                    color = FrostSoulTheme.colors.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Decorative bar histogram; the peak bar is highlighted. Values are shares in 0..1. */
@Composable
private fun MiniBars(
    values: List<Float>,
    modifier: Modifier = Modifier,
) {
    val barColor = FrostSoulTheme.colors.accentMuted
    val peakColor = FrostSoulTheme.colors.accentBright
    Canvas(modifier = modifier) {
        val peak = values.maxOrNull() ?: 0f
        if (values.isEmpty() || peak <= 0f) return@Canvas
        val slot = size.width / values.size
        val barWidth = slot * 0.62f
        values.forEachIndexed { index, value ->
            val barHeight = (value / peak * size.height).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                color = if (value == peak) peakColor else barColor,
                topLeft = Offset(index * slot + (slot - barWidth) / 2f, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
private fun FingerprintCard(
    profile: TasteProfile,
    modifier: Modifier = Modifier,
) {
    val unknown = stringResource(R.string.taste_profile_stat_unknown)
    val stats =
        listOf(
            stringResource(R.string.taste_profile_stat_avg_length) to
                if (profile.averageDurationSeconds > 0) {
                    stringResource(
                        R.string.taste_profile_duration_format,
                        profile.averageDurationSeconds / 60,
                        profile.averageDurationSeconds % 60,
                    )
                } else {
                    unknown
                },
            stringResource(R.string.taste_profile_stat_artists) to profile.distinctArtists.toString(),
            stringResource(R.string.taste_profile_stat_session) to
                (profile.averageSessionMinutes?.let { stringResource(R.string.taste_profile_minutes, it) } ?: unknown),
            stringResource(R.string.taste_profile_stat_liked) to
                stringResource(R.string.taste_profile_percent, (profile.likedShare * 100f).roundToInt()),
            stringResource(R.string.taste_profile_stat_explicit) to
                stringResource(R.string.taste_profile_percent, (profile.explicitShare * 100f).roundToInt()),
            stringResource(R.string.taste_profile_stat_era) to (profile.medianReleaseYear?.toString() ?: unknown),
            stringResource(R.string.taste_profile_stat_drift) to
                (profile.tasteShift?.let { stringResource(R.string.taste_profile_percent, (it * 100f).roundToInt()) } ?: unknown),
        )

    PremiumCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.taste_profile_fingerprint_title),
            style = FrostSoulTheme.typography.sectionTitle,
            color = FrostSoulTheme.colors.onSurface,
        )
        stats.chunked(2).forEach { row ->
            Spacer(Modifier.height(FrostSoulTheme.spacing.medium))
            Row(horizontalArrangement = Arrangement.spacedBy(FrostSoulTheme.spacing.medium)) {
                row.forEach { (label, value) ->
                    StatTile(label = label, value = value, modifier = Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(FrostSoulTheme.shapes.small)
                .background(FrostSoulTheme.colors.surfaceRaised)
                .padding(FrostSoulTheme.spacing.medium),
    ) {
        Text(
            text = value,
            style = FrostSoulTheme.typography.title,
            color = FrostSoulTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = FrostSoulTheme.typography.bodyMuted,
            color = FrostSoulTheme.colors.onSurfaceMuted,
        )
    }
}

@Composable
private fun TasteBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = FrostSoulTheme.colors.accentBright,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(FrostSoulTheme.colors.surfaceRaised),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(color),
        )
    }
}

@StringRes
private fun TasteAxisId.labelRes(): Int =
    when (this) {
        TasteAxisId.EXPLORATION -> R.string.taste_profile_axis_exploration
        TasteAxisId.VARIETY -> R.string.taste_profile_axis_variety
        TasteAxisId.REPLAY -> R.string.taste_profile_axis_replay
        TasteAxisId.PATIENCE -> R.string.taste_profile_axis_patience
        TasteAxisId.ENDURANCE -> R.string.taste_profile_axis_endurance
        TasteAxisId.NIGHT_OWL -> R.string.taste_profile_axis_night_owl
        TasteAxisId.FRESHNESS -> R.string.taste_profile_axis_freshness
    }

@StringRes
private fun TasteAxisId.descriptionRes(): Int =
    when (this) {
        TasteAxisId.EXPLORATION -> R.string.taste_profile_axis_exploration_desc
        TasteAxisId.VARIETY -> R.string.taste_profile_axis_variety_desc
        TasteAxisId.REPLAY -> R.string.taste_profile_axis_replay_desc
        TasteAxisId.PATIENCE -> R.string.taste_profile_axis_patience_desc
        TasteAxisId.ENDURANCE -> R.string.taste_profile_axis_endurance_desc
        TasteAxisId.NIGHT_OWL -> R.string.taste_profile_axis_night_owl_desc
        TasteAxisId.FRESHNESS -> R.string.taste_profile_axis_freshness_desc
    }

@StringRes
private fun TasteAxisId.traitRes(): Int =
    when (this) {
        TasteAxisId.EXPLORATION -> R.string.taste_profile_trait_exploration
        TasteAxisId.VARIETY -> R.string.taste_profile_trait_variety
        TasteAxisId.REPLAY -> R.string.taste_profile_trait_replay
        TasteAxisId.PATIENCE -> R.string.taste_profile_trait_patience
        TasteAxisId.ENDURANCE -> R.string.taste_profile_trait_endurance
        TasteAxisId.NIGHT_OWL -> R.string.taste_profile_trait_night_owl
        TasteAxisId.FRESHNESS -> R.string.taste_profile_trait_freshness
    }

private const val MinAxesForRadar = 3
private const val LearningThreshold = 0.4f
private const val RadarRings = 4
private const val SideThreshold = 0.3f
private val RadarHorizontalMargin = 72.dp
private val RadarVerticalMargin = 32.dp
