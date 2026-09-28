package dev.vxs.frostsoulx.recommendation

import kotlin.math.max

/** Immutable inputs for contextual Home chip scoring. */
data class HomeChipSignals(
    val longTerm: Map<String, Float> = emptyMap(),
    val currentSession: Map<String, Float> = emptyMap(),
    val previousSession: Map<String, Float>? = null,
)

data class HomeChipScore(
    val title: String,
    val longTerm: Float?,
    val currentSession: Float?,
    val previousSession: Float?,
    val finalScore: Float,
)

data class HomeSectionCandidate(
    val id: String,
    val title: String,
    val source: String,
    val itemCount: Int,
    val longTermAffinity: Float = 0.5f,
    val currentSessionCompatibility: Float = 0.5f,
    val freshness: Float = 0.5f,
    val novelty: Float = 0.5f,
    val temporalCompatibility: Float = 0.5f,
    val recentEngagement: Float = 0.5f,
    val discoveryValue: Float = 0.5f,
    val repetitionPenalty: Float = 0f,
    val available: Boolean = true,
    val selectionScore: Float = 0f,
)

data class HomeSectionPlan(
    val selected: List<HomeSectionCandidate>,
    val suppressed: List<HomeSectionCandidate>,
)

/**
 * Small, Android-independent planner shared by Home integrations and tests. It owns no UI or
 * network state; callers provide learned/session features and receive immutable decisions.
 */
object HomeIntelligencePlanner {
    private const val ChipLongTermWeight = 0.30f
    private const val ChipSessionWeight = 0.40f
    private const val ChipPreviousWeight = 0.30f

    fun scoreChips(
        candidates: List<String>,
        signals: HomeChipSignals,
        limit: Int = 5,
    ): List<HomeChipScore> {
        val unique = candidates.map(String::trim).filter(String::isNotEmpty).distinctBy(::key)
        if (unique.isEmpty()) return emptyList()
        val longTerm = valuesFor(unique, signals.longTerm)
        val session = valuesFor(unique, signals.currentSession)
        val previous = signals.previousSession?.let { valuesFor(unique, it) }
        val selected = unique.mapIndexed { index, title ->
            val available = buildList {
                longTerm[index]?.let { add(ChipLongTermWeight to it) }
                session[index]?.let { add(ChipSessionWeight to it) }
                previous?.get(index)?.let { add(ChipPreviousWeight to it) }
            }
            val denominator = available.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(0.001f)
            HomeChipScore(
                title = title,
                longTerm = longTerm[index],
                currentSession = session[index],
                previousSession = previous?.get(index),
                finalScore = (available.sumOf { (it.first * it.second).toDouble() }.toFloat() / denominator)
                    .coerceIn(0f, 1f),
            )
        }.sortedWith(compareByDescending<HomeChipScore> { it.finalScore }.thenBy { key(it.title) })
        return diversifyChips(selected, limit)
    }

    fun planSections(
        candidates: List<HomeSectionCandidate>,
        maxSections: Int = 8,
    ): HomeSectionPlan {
        if (candidates.isEmpty()) return HomeSectionPlan(emptyList(), emptyList())
        val scored = candidates
            .filter { it.available && it.itemCount > 0 }
            .map { it to sectionScore(it) }
            .sortedWith(compareByDescending<Pair<HomeSectionCandidate, Float>> { it.second }.thenBy { key(it.first.id) })
        val selected = mutableListOf<HomeSectionCandidate>()
        val sourceCounts = mutableMapOf<String, Int>()
        val artistLikeCount = mutableSetOf<String>()
        for ((candidate, score) in scored) {
            if (score < 0.18f && selected.size >= 3) continue
            val sourceCount = sourceCounts[candidate.source] ?: 0
            val normalizedTitle = key(candidate.title)
            val sameTitle = selected.any { key(it.title) == normalizedTitle }
            if (sourceCount >= 2 || sameTitle) continue
            // Keep the feed varied: no more than two consecutive sections from one source.
            if (selected.takeLast(2).count { it.source == candidate.source } >= 2) continue
            selected += candidate.copy(selectionScore = score)
            sourceCounts[candidate.source] = sourceCount + 1
            artistLikeCount += normalizedTitle
            if (selected.size >= maxSections.coerceAtLeast(1)) break
        }
        if (selected.isEmpty()) {
            scored.firstOrNull()?.let { (candidate, score) -> selected += candidate.copy(selectionScore = score) }
        }
        val selectedIds = selected.mapTo(hashSetOf()) { it.id }
        val suppressed = candidates.filterNot { it.id in selectedIds }
        return HomeSectionPlan(selected, suppressed)
    }

    private fun sectionScore(candidate: HomeSectionCandidate): Float {
        val raw =
            0.24f * candidate.longTermAffinity.coerceIn(0f, 1f) +
                0.22f * candidate.currentSessionCompatibility.coerceIn(0f, 1f) +
                0.12f * candidate.freshness.coerceIn(0f, 1f) +
                0.14f * candidate.novelty.coerceIn(0f, 1f) +
                0.10f * candidate.temporalCompatibility.coerceIn(0f, 1f) +
                0.08f * candidate.recentEngagement.coerceIn(0f, 1f) +
                0.10f * candidate.discoveryValue.coerceIn(0f, 1f)
        return (raw - 0.18f * candidate.repetitionPenalty.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }

    private fun valuesFor(
        titles: List<String>,
        source: Map<String, Float>,
    ): List<Float?> {
        if (source.isEmpty()) return List(titles.size) { null }
        val normalized = source.mapKeys { key(it.key) }
        val raw = titles.map { title ->
            val titleKey = key(title)
            normalized[titleKey] ?: normalized.entries
                .filter { (sourceKey, _) -> sourceKey.isNotEmpty() && (titleKey.contains(sourceKey) || sourceKey.contains(titleKey)) }
                .maxByOrNull { it.value }?.value
        }
        val present = raw.filterNotNull()
        if (present.isEmpty()) return List(titles.size) { null }
        val minValue = present.minOrNull() ?: 0f
        val maxValue = present.maxOrNull() ?: minValue
        val range = (maxValue - minValue).coerceAtLeast(0.0001f)
        return raw.map { it?.let { value -> ((value - minValue) / range).coerceIn(0f, 1f) } }
    }

    private fun diversifyChips(scores: List<HomeChipScore>, limit: Int): List<HomeChipScore> {
        val result = mutableListOf<HomeChipScore>()
        for (score in scores) {
            val tokens = tokens(score.title)
            if (result.any { overlap(tokens, tokens(it.title)) >= 0.67f }) continue
            result += score
            if (result.size >= limit.coerceIn(1, 5)) break
        }
        return result
    }

    private fun overlap(left: Set<String>, right: Set<String>): Float {
        if (left.isEmpty() || right.isEmpty()) return 0f
        return left.intersect(right).size.toFloat() / max(left.size, right.size).toFloat()
    }

    private fun tokens(value: String): Set<String> = key(value).split(' ').filter(String::isNotBlank).toSet()

    private fun key(value: String): String = value.lowercase().replace(Regex("[^a-z0-9 ]"), " ").trim().replace(Regex("\\s+"), " ")
}
