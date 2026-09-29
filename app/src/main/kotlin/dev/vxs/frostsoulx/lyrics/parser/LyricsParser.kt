/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.lyrics.parser

import dev.vxs.frostsoulx.lyrics.core.LyricsDocument
import dev.vxs.frostsoulx.lyrics.core.LyricsFormat
import dev.vxs.frostsoulx.lyrics.core.LyricsLine
import dev.vxs.frostsoulx.lyrics.core.LyricsTrack
import dev.vxs.frostsoulx.lyrics.core.LyricsWord

object LyricsParser {
    private val lineTime = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordTime = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val yrcLine = Regex("""\[(\d{1,10}),(\d{1,10})](.*)""")
    private val yrcWord = Regex("""\((\d{1,10}),(\d{1,10})(?:,\d{1,10})?\)""")
    private val offset = Regex("""(?im)^\[offset:([+-]?\d+)]\s*$""")
    // Capture the complete attribute block first. The previous expression mixed
    // catch-all groups with optional begin/end groups, so attribute order could make
    // begin/end silently null and force the line onto the synthetic fallback clock.
    private val ttmlParagraph = Regex("""<p\b([^>]*)>(.*?)</p>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ttmlSpan = Regex("""<span\b([^>]*)>(.*?)</span>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ttmlAttribute = Regex("""\b(begin|end)\s*=\s*[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE)
    private val xmlTags = Regex("""<[^>]+>""")
    private val metadataLine = Regex("""^\[(ar|al|ti|by|re|ve|length):""", RegexOption.IGNORE_CASE)

    fun parse(
        songId: String,
        originalText: String,
        translationText: String? = null,
        romanizationText: String? = null,
        source: String? = null,
        artworkKey: String? = null,
    ): LyricsDocument {
        val original = parseTrack(originalText)
        val translation = translationText?.takeIf(String::isNotBlank)?.let(::parseTrack)
        val romanization = romanizationText?.takeIf(String::isNotBlank)?.let(::parseTrack)
        val aligned = alignVariants(original.lines, translation?.lines.orEmpty(), romanization?.lines.orEmpty())
        return LyricsDocument(
            songId = songId,
            format = original.format,
            original = LyricsTrack(lines = aligned),
            translation = translation?.let { LyricsTrack(lines = it.lines) },
            romanization = romanization?.let { LyricsTrack(lines = it.lines) },
            offsetMs = original.offsetMs,
            source = source,
            artworkKey = artworkKey,
        )
    }

    fun parseTrack(raw: String): ParsedLyricsTrack {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isBlank()) return ParsedLyricsTrack(LyricsFormat.Plain, 0L, emptyList())
        return when {
            text.contains("<tt", ignoreCase = true) || text.contains("<body", ignoreCase = true) -> parseTtml(text)
            yrcLine.containsMatchIn(text) -> parseYrc(text)
            lineTime.containsMatchIn(text) -> parseLrc(text)
            else -> parsePlain(text)
        }
    }

    private fun parsePlain(text: String): ParsedLyricsTrack {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotBlank).mapIndexed { index, value ->
            val start = index * PlainLineDurationMs
            LyricsLine(startMs = start.toLong(), endMs = (start + PlainLineDurationMs).toLong(), text = value)
        }.toList()
        return ParsedLyricsTrack(LyricsFormat.Plain, 0L, lines)
    }

    private fun parseLrc(text: String): ParsedLyricsTrack {
        val offsetMs = offset.find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()?.coerceIn(-30_000L, 30_000L) ?: 0L
        val parsed = mutableListOf<RawLine>()
        text.lineSequence().forEach { rawLine ->
            if (metadataLine.containsMatchIn(rawLine)) return@forEach
            val timestamps = lineTime.findAll(rawLine).map { parseClock(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.toList()
            if (timestamps.isEmpty()) return@forEach
            val content = lineTime.replace(rawLine, "").trim()
            timestamps.forEach { timestamp ->
                parsed += RawLine(startMs = timestamp, body = content)
            }
        }
        val format = if (parsed.any { wordTime.containsMatchIn(it.body) }) LyricsFormat.EnhancedLrc else LyricsFormat.Lrc
        return ParsedLyricsTrack(format, offsetMs, finalizeLines(parsed.sortedBy { it.startMs }, format))
    }

    private fun parseYrc(text: String): ParsedLyricsTrack {
        val lines =
            text.lineSequence().mapNotNull { source ->
                val match = yrcLine.matchEntire(source.trim()) ?: return@mapNotNull null
                val start = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val duration = match.groupValues[2].toLongOrNull()?.coerceAtLeast(1L) ?: DefaultLineDurationMs
                val words = parseYrcWords(match.groupValues[3], start, duration)
                LyricsLine(
                    startMs = start,
                    endMs = start + duration,
                    text = words.joinToString(separator = "") { it.text }.ifBlank { match.groupValues[3].trim() },
                    words = words,
                )
            }.sortedBy { it.startMs }.toList()
        return ParsedLyricsTrack(LyricsFormat.Yrc, 0L, lines)
    }

    private fun parseYrcWords(
        body: String,
        lineStartMs: Long,
        lineDurationMs: Long,
    ): List<LyricsWord> {
        val markers = yrcWord.findAll(body).toList()
        if (markers.isEmpty()) return emptyList()
        return markers.mapIndexedNotNull { index, marker ->
            val start = marker.groupValues[1].toLongOrNull() ?: return@mapIndexedNotNull null
            val duration = marker.groupValues[2].toLongOrNull()?.coerceAtLeast(1L) ?: 1L
            val textStart = marker.range.last + 1
            val textEnd = markers.getOrNull(index + 1)?.range?.first ?: body.length
            val wordText = body.substring(textStart, textEnd)
            LyricsWord(
                text = wordText,
                startMs = start.coerceAtLeast(lineStartMs),
                endMs = (start + duration).coerceAtMost(lineStartMs + lineDurationMs),
            )
        }
    }

    private fun parseTtml(text: String): ParsedLyricsTrack {
        val lines =
            ttmlParagraph.findAll(text).mapIndexedNotNull { index, match ->
                val attributes = match.groupValues[1]
                val start = parseTtmlAttribute(attributes, "begin") ?: index * DefaultLineDurationMs
                val end = parseTtmlAttribute(attributes, "end") ?: start + DefaultLineDurationMs
                val body = match.groupValues[2]
                val words =
                    ttmlSpan.findAll(body).mapNotNull { span ->
                        val spanAttributes = span.groupValues[1]
                        val wordStart = parseTtmlAttribute(spanAttributes, "begin") ?: return@mapNotNull null
                        val wordEnd = parseTtmlAttribute(spanAttributes, "end") ?: wordStart + MinimumWordDurationMs
                        LyricsWord(
                            text = stripXml(span.groupValues[2]),
                            startMs = wordStart,
                            endMs = wordEnd.coerceAtLeast(wordStart + MinimumWordDurationMs),
                        )
                    }.toList()
                LyricsLine(
                    startMs = start.coerceAtLeast(0L),
                    endMs = end.coerceAtLeast(start + MinimumWordDurationMs),
                    text = stripXml(body),
                    words = words,
                )
            }.sortedBy { it.startMs }.toList()
        return ParsedLyricsTrack(LyricsFormat.Ttml, 0L, lines)
    }

    private fun finalizeLines(
        rawLines: List<RawLine>,
        format: LyricsFormat,
        offsetMs: Long = 0L,
    ): List<LyricsLine> =
        rawLines.mapIndexed { index, raw ->
            val nextStart = rawLines.getOrNull(index + 1)?.startMs
            val end = (nextStart ?: raw.startMs + DefaultLineDurationMs).coerceAtLeast(raw.startMs + MinimumWordDurationMs)
            val words = if (format == LyricsFormat.EnhancedLrc) parseEnhancedWords(raw.body, raw.startMs, end, offsetMs) else emptyList()
            LyricsLine(
                startMs = raw.startMs,
                endMs = end,
                text = if (words.isEmpty()) raw.body else words.joinToString(separator = "") { it.text }.trim(),
                words = words,
                isInstrumental = raw.body.trim().matches(InstrumentalMarker),
            )
        }.filter { it.text.isNotBlank() || it.words.isNotEmpty() }

    private fun parseEnhancedWords(
        body: String,
        lineStartMs: Long,
        lineEndMs: Long,
        offsetMs: Long,
    ): List<LyricsWord> {
        val markers = wordTime.findAll(body).toList()
        if (markers.isEmpty()) return emptyList()
        return markers.mapIndexed { index, marker ->
            val start = (parseClock(marker.groupValues[1], marker.groupValues[2], marker.groupValues[3]) + offsetMs).coerceAtLeast(lineStartMs)
            val end =
                markers
                    .getOrNull(index + 1)
                    ?.let { parseClock(it.groupValues[1], it.groupValues[2], it.groupValues[3]) + offsetMs }
                    ?.coerceAtLeast(start + MinimumWordDurationMs)
                    ?: lineEndMs
            val textStart = marker.range.last + 1
            val textEnd = markers.getOrNull(index + 1)?.range?.first ?: body.length
            LyricsWord(
                text = body.substring(textStart, textEnd),
                startMs = start,
                endMs = end.coerceAtMost(lineEndMs).coerceAtLeast(start + MinimumWordDurationMs),
            )
        }.filter { it.text.isNotBlank() }
    }

    private fun alignVariants(
        original: List<LyricsLine>,
        translations: List<LyricsLine>,
        romanizations: List<LyricsLine>,
    ): List<LyricsLine> =
        original.mapIndexed { index, line ->
            line.copy(
                translation = translations.nearestText(line.startMs, index),
                romanization = romanizations.nearestText(line.startMs, index),
            )
        }

    private fun List<LyricsLine>.nearestText(
        timestampMs: Long,
        fallbackIndex: Int,
    ): String? {
        getOrNull(fallbackIndex)?.text?.takeIf(String::isNotBlank)?.let { return it }
        return minByOrNull { kotlin.math.abs(it.startMs - timestampMs) }
            ?.takeIf { kotlin.math.abs(it.startMs - timestampMs) <= VariantAlignmentToleranceMs }
            ?.text
            ?.takeIf(String::isNotBlank)
    }

    private fun parseClock(
        minute: String,
        second: String,
        fraction: String,
    ): Long {
        val base = (minute.toLongOrNull() ?: 0L) * 60_000L + (second.toLongOrNull() ?: 0L) * 1_000L
        val fractional =
            when (fraction.length) {
                0 -> 0L
                1 -> (fraction.toLongOrNull() ?: 0L) * 100L
                2 -> (fraction.toLongOrNull() ?: 0L) * 10L
                else -> (fraction.take(3).toLongOrNull() ?: 0L)
            }
        return base + fractional
    }

    private fun parseTtmlClock(value: String): Long? {
        val input = value.trim()
        if (input.isBlank()) return null
        input.removeSuffix("ms").toLongOrNull()?.let { return if (input.endsWith("ms")) it else null }
        input.removeSuffix("s").toDoubleOrNull()?.let { return (it * 1_000.0).toLong() }
        val parts = input.split(':')
        if (parts.size == 3) {
            val hour = parts[0].toLongOrNull() ?: return null
            val minute = parts[1].toLongOrNull() ?: return null
            val seconds = parts[2].toDoubleOrNull() ?: return null
            return hour * 3_600_000L + minute * 60_000L + (seconds * 1_000.0).toLong()
        }
        return null
    }

    private fun parseTtmlAttribute(attributes: String, name: String): Long? {
        val value = ttmlAttribute.findAll(attributes)
            .firstOrNull { it.groupValues[1].equals(name, ignoreCase = true) }
            ?.groupValues
            ?.getOrNull(2)
            ?: return null
        return parseTtmlClock(value)
    }

    private fun stripXml(value: String): String =
        xmlTags.replace(value, "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").trim()

    data class ParsedLyricsTrack(
        val format: LyricsFormat,
        val offsetMs: Long,
        val lines: List<LyricsLine>,
    )

    private data class RawLine(
        val startMs: Long,
        val body: String,
    )

    private const val PlainLineDurationMs = 2_000
    private const val DefaultLineDurationMs = 3_500L
    private const val MinimumWordDurationMs = 1L
    private const val VariantAlignmentToleranceMs = 1_500L
    private val InstrumentalMarker = Regex("""^\s*(?:\[?instrumental]?|♪+|♫+)\s*$""", RegexOption.IGNORE_CASE)
}
