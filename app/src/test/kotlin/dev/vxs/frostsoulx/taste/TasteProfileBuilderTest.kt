package dev.vxs.frostsoulx.taste

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TasteProfileBuilderTest {
    private val now = LocalDateTime.of(2026, 9, 19, 22, 0)

    private fun artist(id: String) = TasteArtistRef(id = id, name = "Artist $id", thumbnailUrl = null)

    private fun play(
        songId: String,
        artistId: String,
        daysAgo: Long,
        hour: Int = 12,
    ) = TastePlay(
        songId = songId,
        artists = listOf(artist(artistId)),
        timestamp = now.minusDays(daysAgo).withHour(hour).withMinute(0),
        playTimeMs = 200_000L,
        durationMs = 220_000L,
        releaseYear = null,
        explicit = false,
        liked = false,
    )

    private fun build(
        plays: List<TastePlay>,
        feedback: List<TasteFeedback> = emptyList(),
    ) = TasteProfileBuilder.build(TasteInput(plays = plays, feedback = feedback, library = emptyList()), now)

    @Test
    fun `empty input yields an unusable profile`() {
        assertFalse(build(emptyList()).isUsable)
    }

    @Test
    fun `most played recent artist gets the highest affinity`() {
        val plays =
            List(20) { index -> play("a-$index", "A", daysAgo = 1) } +
                List(3) { index -> play("b-$index", "B", daysAgo = 1) }

        val profile = build(plays)

        assertEquals(1f, profile.artistAffinity.getValue("A"), 0.001f)
        assertTrue(profile.artistAffinity.getValue("B") < 1f)
        assertEquals("A", profile.topArtists.first().id)
    }

    @Test
    fun `older plays weigh less than recent plays`() {
        val plays =
            List(10) { index -> play("old-$index", "OLD", daysAgo = 120) } +
                List(10) { index -> play("new-$index", "NEW", daysAgo = 1) }

        val profile = build(plays)

        assertTrue(profile.artistAffinity.getValue("NEW") > profile.artistAffinity.getValue("OLD"))
    }

    @Test
    fun `a disliked artist is avoided rather than recommended`() {
        val profile =
            build(
                plays = listOf(play("song", "A", daysAgo = 1)),
                feedback = listOf(TasteFeedback(listOf("A"), TasteFeedbackKind.Dislike, ageDays = 1f)),
            )

        assertTrue("A" in profile.avoidedArtistIds)
        assertFalse("A" in profile.artistAffinity)
    }

    @Test
    fun `exploration needs history longer than the discovery horizon`() {
        val plays = List(12) { index -> play("s-$index", "artist-$index", daysAgo = (index % 10).toLong()) }

        val exploration = build(plays).axes.first { it.id == TasteAxisId.EXPLORATION }

        assertFalse(exploration.hasData)
    }

    @Test
    fun `variety rises with the number of distinct artists`() {
        val narrow = build(List(30) { index -> play("n-$index", "only", daysAgo = 2) })
        val broad = build(List(30) { index -> play("b-$index", "artist-$index", daysAgo = 2) })

        val narrowScore = narrow.axes.first { it.id == TasteAxisId.VARIETY }.score
        val broadScore = broad.axes.first { it.id == TasteAxisId.VARIETY }.score

        assertTrue(broadScore > narrowScore)
    }

    @Test
    fun `known artists outrank unknown ones`() {
        val profile = build(List(8) { index -> play("s-$index", "known", daysAgo = 1) })

        assertNotNull(profile.artistAffinity["known"])
        assertTrue(
            profile.affinity(listOf("known"), durationSeconds = 210, releaseYear = null) >
                profile.affinity(listOf("stranger"), durationSeconds = 210, releaseYear = null),
        )
    }
}
