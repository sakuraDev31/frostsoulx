package dev.vxs.frostsoulx.ui.screens.settings.spatial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vxs.frostsoulx.R

@Composable
fun EffectsTab(
    activeEffectId: String?,
    favoriteIds: Set<String>,
    onEffectUse: (SpatialEffect) -> Unit,
    onEffectOpen: (SpatialEffect) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var selectedCategory by remember { mutableStateOf(EffectCategory.ALL) }
    var selectedSort by remember { mutableStateOf(EffectSortOption.RECOMMENDED) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // Filter & Sort: Favorites always sort first within the current filter
    val filteredEffects = remember(selectedCategory, selectedSort, favoriteIds) {
        val base = if (selectedCategory == EffectCategory.ALL) {
            BuiltInSpatialEffects
        } else {
            BuiltInSpatialEffects.filter { it.category == selectedCategory }
        }

        val sorted = when (selectedSort) {
            EffectSortOption.RECOMMENDED -> base
            EffectSortOption.ALPHABETICAL -> base.sortedBy { it.name }
        }

        // Favorites first
        sorted.sortedByDescending { it.id in favoriteIds }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(SpatialAudioTokens.RowGap),
    ) {
        // Horizontal scrollable filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = SpatialAudioTokens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(SpatialAudioTokens.ChipGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EffectCategory.entries.forEach { category ->
                val selected = category == selectedCategory
                FilterChip(
                    selected = selected,
                    onClick = { selectedCategory = category },
                    label = { Text(category.label) },
                    shape = SpatialAudioTokens.PillShape,
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        borderWidth = if (selected) SpatialAudioTokens.StrokeOutline else SpatialAudioTokens.StrokeHairline,
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = SpatialAudioTokens.SurfaceContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }

        // Count on left ("8 effects"), Sort on right ("Recommended" / "A–Z")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpatialAudioTokens.ScreenPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${filteredEffects.size} ${if (filteredEffects.size == 1) "effect" else "effects"}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box {
                Row(
                    modifier = Modifier
                        .bouncyClick { sortMenuExpanded = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Sort: ${selectedSort.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        painter = painterResource(R.drawable.expand_more),
                        contentDescription = "Change sort",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                ) {
                    EffectSortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                selectedSort = option
                                sortMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }

        // List of Effects (One card style, 20dp radius, stable keys)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = SpatialAudioTokens.ScreenPadding,
                end = SpatialAudioTokens.ScreenPadding,
                top = 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(SpatialAudioTokens.RowGap),
        ) {
            items(
                items = filteredEffects,
                key = { it.id },
            ) { effect ->
                val isActive = effect.id == activeEffectId
                val isFavorite = effect.id in favoriteIds

                EffectRowCard(
                    effect = effect,
                    isActive = isActive,
                    isFavorite = isFavorite,
                    onUseClick = { onEffectUse(effect) },
                    onCardClick = { onEffectOpen(effect) },
                    onFavoriteClick = { onToggleFavorite(effect.id) },
                )
            }
        }
    }
}

@Composable
private fun EffectRowCard(
    effect: SpatialEffect,
    isActive: Boolean,
    isFavorite: Boolean,
    onUseClick: () -> Unit,
    onCardClick: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    SpatialCard(
        isActive = isActive,
        onClick = onCardClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpatialAudioTokens.RowGap),
        ) {
            // Leading 48dp tonal circle with outlined icon
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = if (isActive) MaterialTheme.colorScheme.primaryContainer else SpatialAudioTokens.SurfaceContainerHigh,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(effect.iconRes),
                        contentDescription = null,
                        tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Name (title) & one-line description
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = effect.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (effect.isNew) {
                        Surface(
                            shape = SpatialAudioTokens.PillShape,
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = "NEW",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = effect.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Trailing favorite heart button
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    painter = painterResource(if (isFavorite) R.drawable.favorite else R.drawable.favorite_border),
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Trailing Use / Using pill button
            if (isActive) {
                Button(
                    onClick = onUseClick,
                    shape = SpatialAudioTokens.PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text(
                        text = "Using ✓",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onUseClick,
                    shape = SpatialAudioTokens.PillShape,
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = SpatialAudioTokens.StrokeHairline,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text(
                        text = "Use",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
