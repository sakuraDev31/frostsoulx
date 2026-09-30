/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.vxs.frostsoulx.home

import android.content.Context
import dev.vxs.frostsoulx.innertube.models.Album
import dev.vxs.frostsoulx.innertube.models.AlbumItem
import dev.vxs.frostsoulx.innertube.models.Artist
import dev.vxs.frostsoulx.innertube.models.ArtistItem
import dev.vxs.frostsoulx.innertube.models.BrowseEndpoint
import dev.vxs.frostsoulx.innertube.models.PlaylistItem
import dev.vxs.frostsoulx.innertube.models.SongItem
import dev.vxs.frostsoulx.innertube.models.WatchEndpoint
import dev.vxs.frostsoulx.innertube.models.YTItem
import dev.vxs.frostsoulx.innertube.pages.HomePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File

object HomePageCache {
    private const val CACHE_FILE_NAME = "home_page_cache.json"

    suspend fun save(context: Context, homePage: HomePage) = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()

            // Chips
            val chipsArray = JSONArray()
            homePage.chips?.forEach { chip ->
                val chipObj = JSONObject()
                chipObj.put("title", chip.title)
                chip.endpoint?.let { ep ->
                    chipObj.put("browseId", ep.browseId)
                    ep.params?.let { chipObj.put("params", it) }
                }
                chip.deselectEndPoint?.let { dep ->
                    chipObj.put("deselectBrowseId", dep.browseId)
                    dep.params?.let { chipObj.put("deselectParams", it) }
                }
                chipsArray.put(chipObj)
            }
            root.put("chips", chipsArray)

            // Continuation
            homePage.continuation?.let { root.put("continuation", it) }

            // Sections
            val sectionsArray = JSONArray()
            homePage.sections.forEach { section ->
                val sectionObj = JSONObject()
                sectionObj.put("title", section.title)
                section.label?.let { sectionObj.put("label", it) }
                section.thumbnail?.let { sectionObj.put("thumbnail", it) }
                section.endpoint?.let { ep ->
                    sectionObj.put("browseId", ep.browseId)
                    ep.params?.let { sectionObj.put("params", it) }
                }

                val itemsArray = JSONArray()
                section.items.forEach { item ->
                    val itemObj = JSONObject()
                    when (item) {
                        is SongItem -> {
                            itemObj.put("type", "song")
                            itemObj.put("id", item.id)
                            itemObj.put("title", item.title)
                            itemObj.put("thumbnail", item.thumbnail)
                            itemObj.put("explicit", item.explicit)
                            item.duration?.let { itemObj.put("duration", it) }
                            item.album?.let { album ->
                                val albumObj = JSONObject()
                                albumObj.put("name", album.name)
                                albumObj.put("id", album.id)
                                itemObj.put("album", albumObj)
                            }
                            val artistsArray = JSONArray()
                            item.artists.forEach { artist ->
                                val artObj = JSONObject()
                                artObj.put("name", artist.name)
                                artist.id?.let { artObj.put("id", it) }
                                artistsArray.put(artObj)
                            }
                            itemObj.put("artists", artistsArray)
                            item.endpoint?.let { we ->
                                val weObj = JSONObject()
                                we.videoId?.let { weObj.put("videoId", it) }
                                we.playlistId?.let { weObj.put("playlistId", it) }
                                we.params?.let { weObj.put("params", it) }
                                itemObj.put("endpoint", weObj)
                            }
                        }
                        is AlbumItem -> {
                            itemObj.put("type", "album")
                            itemObj.put("id", item.id)
                            itemObj.put("browseId", item.browseId)
                            itemObj.put("playlistId", item.playlistId)
                            itemObj.put("title", item.title)
                            itemObj.put("thumbnail", item.thumbnail)
                            itemObj.put("explicit", item.explicit)
                            item.year?.let { itemObj.put("year", it) }
                            val artistsArray = JSONArray()
                            item.artists?.forEach { artist ->
                                val artObj = JSONObject()
                                artObj.put("name", artist.name)
                                artist.id?.let { artObj.put("id", it) }
                                artistsArray.put(artObj)
                            }
                            itemObj.put("artists", artistsArray)
                        }
                        is PlaylistItem -> {
                            itemObj.put("type", "playlist")
                            itemObj.put("id", item.id)
                            itemObj.put("title", item.title)
                            item.thumbnail?.let { itemObj.put("thumbnail", it) }
                            item.songCountText?.let { itemObj.put("songCountText", it) }
                            item.author?.let { author ->
                                val authorObj = JSONObject()
                                authorObj.put("name", author.name)
                                author.id?.let { authorObj.put("id", it) }
                                itemObj.put("author", authorObj)
                            }
                            item.playEndpoint?.let { we ->
                                val weObj = JSONObject()
                                we.playlistId?.let { weObj.put("playlistId", it) }
                                itemObj.put("playEndpoint", weObj)
                            }
                        }
                        is ArtistItem -> {
                            itemObj.put("type", "artist")
                            itemObj.put("id", item.id)
                            itemObj.put("title", item.title)
                            item.thumbnail?.let { itemObj.put("thumbnail", it) }
                            item.channelId?.let { itemObj.put("channelId", it) }
                        }
                    }
                    itemsArray.put(itemObj)
                }
                sectionObj.put("items", itemsArray)
                sectionsArray.put(sectionObj)
            }
            root.put("sections", sectionsArray)

            val file = File(context.filesDir, CACHE_FILE_NAME)
            file.writeText(root.toString())
        } catch (e: Exception) {
            Timber.w(e, "Failed to save HomePage to cache")
        }
    }

    suspend fun load(context: Context): HomePage? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, CACHE_FILE_NAME)
            if (!file.exists()) return@withContext null
            val content = file.readText()
            if (content.isBlank()) return@withContext null
            val root = JSONObject(content)

            val continuation = root.optString("continuation").takeIf { it.isNotBlank() }

            val chipsList = mutableListOf<HomePage.Chip>()
            val chipsArray = root.optJSONArray("chips")
            if (chipsArray != null) {
                for (i in 0 until chipsArray.length()) {
                    val chipObj = chipsArray.getJSONObject(i)
                    val title = chipObj.getString("title")
                    val browseId = chipObj.optString("browseId").takeIf { it.isNotBlank() }
                    val params = chipObj.optString("params").takeIf { it.isNotBlank() }
                    val endpoint = browseId?.let { BrowseEndpoint(browseId = it, params = params) }
                    val deselectBrowseId = chipObj.optString("deselectBrowseId").takeIf { it.isNotBlank() }
                    val deselectParams = chipObj.optString("deselectParams").takeIf { it.isNotBlank() }
                    val deselectEndpoint = deselectBrowseId?.let { BrowseEndpoint(browseId = it, params = deselectParams) }
                    chipsList += HomePage.Chip(title, endpoint, deselectEndpoint)
                }
            }

            val sectionsList = mutableListOf<HomePage.Section>()
            val sectionsArray = root.optJSONArray("sections")
            if (sectionsArray != null) {
                for (i in 0 until sectionsArray.length()) {
                    val secObj = sectionsArray.getJSONObject(i)
                    val title = secObj.getString("title")
                    val label = secObj.optString("label").takeIf { it.isNotBlank() }
                    val thumbnail = secObj.optString("thumbnail").takeIf { it.isNotBlank() }
                    val browseId = secObj.optString("browseId").takeIf { it.isNotBlank() }
                    val params = secObj.optString("params").takeIf { it.isNotBlank() }
                    val endpoint = browseId?.let { BrowseEndpoint(browseId = it, params = params) }

                    val itemsList = mutableListOf<YTItem>()
                    val itemsArray = secObj.optJSONArray("items")
                    if (itemsArray != null) {
                        for (j in 0 until itemsArray.length()) {
                            val itemObj = itemsArray.getJSONObject(j)
                            when (itemObj.optString("type")) {
                                "song" -> {
                                    val id = itemObj.getString("id")
                                    val songTitle = itemObj.getString("title")
                                    val itemThumb = itemObj.optString("thumbnail", "")
                                    val explicit = itemObj.optBoolean("explicit", false)
                                    val duration = if (itemObj.has("duration")) itemObj.getInt("duration") else null
                                    val album = itemObj.optJSONObject("album")?.let {
                                        Album(it.getString("name"), it.getString("id"))
                                    }
                                    val artists = mutableListOf<Artist>()
                                    val artArr = itemObj.optJSONArray("artists")
                                    if (artArr != null) {
                                        for (k in 0 until artArr.length()) {
                                            val aObj = artArr.getJSONObject(k)
                                            artists += Artist(aObj.getString("name"), aObj.optString("id").takeIf { it.isNotBlank() })
                                        }
                                    }
                                    val endpoint = itemObj.optJSONObject("endpoint")?.let {
                                        WatchEndpoint(
                                            videoId = it.optString("videoId").takeIf { v -> v.isNotBlank() },
                                            playlistId = it.optString("playlistId").takeIf { p -> p.isNotBlank() },
                                            params = it.optString("params").takeIf { p -> p.isNotBlank() },
                                        )
                                    }
                                    itemsList += SongItem(
                                        id = id,
                                        title = songTitle,
                                        artists = artists,
                                        album = album,
                                        duration = duration,
                                        thumbnail = itemThumb,
                                        explicit = explicit,
                                        endpoint = endpoint,
                                    )
                                }
                                "album" -> {
                                    val id = itemObj.getString("id")
                                    val browseId = itemObj.optString("browseId", id)
                                    val playlistId = itemObj.optString("playlistId", "")
                                    val albumTitle = itemObj.getString("title")
                                    val itemThumb = itemObj.optString("thumbnail", "")
                                    val explicit = itemObj.optBoolean("explicit", false)
                                    val year = if (itemObj.has("year")) itemObj.getInt("year") else null
                                    val artists = mutableListOf<Artist>()
                                    val artArr = itemObj.optJSONArray("artists")
                                    if (artArr != null) {
                                        for (k in 0 until artArr.length()) {
                                            val aObj = artArr.getJSONObject(k)
                                            artists += Artist(aObj.getString("name"), aObj.optString("id").takeIf { it.isNotBlank() })
                                        }
                                    }
                                    itemsList += AlbumItem(
                                        browseId = browseId,
                                        playlistId = playlistId,
                                        id = id,
                                        title = albumTitle,
                                        artists = artists.ifEmpty { null },
                                        year = year,
                                        thumbnail = itemThumb,
                                        explicit = explicit,
                                    )
                                }
                                "playlist" -> {
                                    val id = itemObj.getString("id")
                                    val playlistTitle = itemObj.getString("title")
                                    val itemThumb = itemObj.optString("thumbnail").takeIf { it.isNotBlank() }
                                    val songCountText = itemObj.optString("songCountText").takeIf { it.isNotBlank() }
                                    val author = itemObj.optJSONObject("author")?.let {
                                        Artist(it.getString("name"), it.optString("id").takeIf { a -> a.isNotBlank() })
                                    }
                                    val playEndpoint = itemObj.optJSONObject("playEndpoint")?.let {
                                        WatchEndpoint(playlistId = it.optString("playlistId").takeIf { p -> p.isNotBlank() })
                                    }
                                    itemsList += PlaylistItem(
                                        id = id,
                                        title = playlistTitle,
                                        author = author,
                                        songCountText = songCountText,
                                        thumbnail = itemThumb,
                                        playEndpoint = playEndpoint,
                                        shuffleEndpoint = null,
                                        radioEndpoint = null,
                                    )
                                }
                                "artist" -> {
                                    val id = itemObj.getString("id")
                                    val artistTitle = itemObj.getString("title")
                                    val itemThumb = itemObj.optString("thumbnail").takeIf { it.isNotBlank() }
                                    val channelId = itemObj.optString("channelId").takeIf { it.isNotBlank() }
                                    itemsList += ArtistItem(
                                        id = id,
                                        title = artistTitle,
                                        thumbnail = itemThumb,
                                        channelId = channelId,
                                        shuffleEndpoint = null,
                                        radioEndpoint = null,
                                    )
                                }
                            }
                        }
                    }
                    sectionsList += HomePage.Section(
                        title = title,
                        label = label,
                        thumbnail = thumbnail,
                        endpoint = endpoint,
                        items = itemsList,
                    )
                }
            }

            if (sectionsList.isEmpty() && chipsList.isEmpty()) null
            else HomePage(chips = chipsList.ifEmpty { null }, sections = sectionsList, continuation = continuation)
        } catch (e: Exception) {
            Timber.w(e, "Failed to load HomePage from cache")
            null
        }
    }
}
