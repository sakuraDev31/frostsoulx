/*
 * Optional global music knowledge layer.
 * Local cache remains the source of truth when offline or when no API key is configured.
 */
package dev.vxs.frostsoulx.recommendation

import dev.vxs.frostsoulx.db.MusicDatabase
import dev.vxs.frostsoulx.db.entities.RecommendationKnowledgeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface MusicKnowledgeProvider {
    suspend fun similarTracks(
        artist: String,
        track: String,
        limit: Int = 20,
        forceRefresh: Boolean = false,
    ): KnowledgeResult

    suspend fun tags(artist: String, track: String, forceRefresh: Boolean = false): KnowledgeResult
}

data class KnowledgeResult(
    val provider: String,
    val sourceKey: String,
    val values: List<KnowledgeValue>,
    val fromCache: Boolean,
    val stale: Boolean = false,
    val unavailable: Boolean = false,
)

data class KnowledgeValue(
    val artist: String,
    val track: String? = null,
    val score: Float? = null,
    val tags: List<String> = emptyList(),
)

@Singleton
class LastFmKnowledgeProvider @Inject constructor(
    private val database: MusicDatabase,
) : MusicKnowledgeProvider {
    private val client = OkHttpClient()
    private val requestLocks = ConcurrentHashMap<String, Mutex>()

    /** API key is supplied by the caller so credentials never enter the recommendation database. */
    suspend fun similarTracks(
        apiKey: String?,
        artist: String,
        track: String,
        limit: Int = 20,
        forceRefresh: Boolean = false,
    ): KnowledgeResult = fetch(
        apiKey = apiKey,
        knowledgeType = "similar-track",
        lookupKey = key(artist, track),
        forceRefresh = forceRefresh,
    ) { json ->
        val items = json.optJSONObject("similartracks")?.optJSONArray("track") ?: JSONArray()
        buildList {
            for (index in 0 until minOf(items.length(), limit.coerceIn(1, 100))) {
                val item = items.optJSONObject(index) ?: continue
                add(
                    KnowledgeValue(
                        artist = item.optJSONObject("artist")?.optString("name").orEmpty(),
                        track = item.optString("name").takeIf { it.isNotBlank() },
                        score = item.optDouble("match", Double.NaN).toFloatOrNull(),
                    ),
                )
            }
        }
    }

    override suspend fun similarTracks(
        artist: String,
        track: String,
        limit: Int,
        forceRefresh: Boolean,
    ): KnowledgeResult = similarTracks(null, artist, track, limit, forceRefresh)

    suspend fun tags(
        apiKey: String?,
        artist: String,
        track: String,
        forceRefresh: Boolean = false,
    ): KnowledgeResult = fetch(
        apiKey = apiKey,
        knowledgeType = "track-tags",
        lookupKey = key(artist, track),
        forceRefresh = forceRefresh,
    ) { json ->
        val items = json.optJSONObject("toptags")?.optJSONArray("tag") ?: JSONArray()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                if (name.isNotEmpty()) add(KnowledgeValue(artist = artist, track = track, tags = listOf(name)))
            }
        }
    }

    override suspend fun tags(artist: String, track: String, forceRefresh: Boolean): KnowledgeResult =
        tags(null, artist, track, forceRefresh)

    private suspend fun fetch(
        apiKey: String?,
        knowledgeType: String,
        lookupKey: String,
        forceRefresh: Boolean,
        parse: (JSONObject) -> List<KnowledgeValue>,
    ): KnowledgeResult = withContext(Dispatchers.IO) {
        val provider = "lastfm"
        val now = System.currentTimeMillis()
        val cached = database.recommendationKnowledge(provider, knowledgeType, lookupKey)
        if (!forceRefresh && cached != null) {
            val values = decode(cached.payload)
            if (cached.expiresAtMs > now) return@withContext KnowledgeResult(provider, lookupKey, values, fromCache = true)
            if (apiKey.isNullOrBlank()) return@withContext KnowledgeResult(provider, lookupKey, values, fromCache = true, stale = true, unavailable = true)
        }
        if (apiKey.isNullOrBlank()) return@withContext KnowledgeResult(provider, lookupKey, decode(cached?.payload), fromCache = cached != null, stale = cached != null, unavailable = true)

        val lock = requestLocks.computeIfAbsent("$knowledgeType:$lookupKey") { Mutex() }
        lock.withLock {
            val rechecked = database.recommendationKnowledge(provider, knowledgeType, lookupKey)
            if (!forceRefresh && rechecked != null && rechecked.expiresAtMs > System.currentTimeMillis()) {
                return@withLock KnowledgeResult(provider, lookupKey, decode(rechecked.payload), fromCache = true)
            }
            val response = runCatching {
                val url = "https://ws.audioscrobbler.com/2.0/".toHttpUrl().newBuilder()
                    .addQueryParameter("method", if (knowledgeType == "similar-track") "track.getsimilar" else "track.gettoptags")
                    .addQueryParameter("api_key", apiKey)
                    .addQueryParameter("artist", artistFromKey(lookupKey))
                    .addQueryParameter("track", trackFromKey(lookupKey))
                    .addQueryParameter("format", "json")
                    .build()
                client.newCall(Request.Builder().url(url).get().build()).execute().use { call ->
                    if (!call.isSuccessful) error("Last.fm HTTP ${call.code}")
                    JSONObject(call.body?.string().orEmpty())
                }
            }.getOrNull()
            if (response == null) {
                return@withLock KnowledgeResult(provider, lookupKey, decode(rechecked?.payload), fromCache = rechecked != null, stale = rechecked != null, unavailable = true)
            }
            val values = parse(response)
            val payload = encode(values)
            database.upsertRecommendationKnowledge(
                RecommendationKnowledgeEntity(provider, knowledgeType, lookupKey, payload, now, now + CacheTtlMs),
            )
            KnowledgeResult(provider, lookupKey, values, fromCache = false)
        }
    }

    private fun key(artist: String, track: String): String = "${artist.trim().lowercase()}\u0000${track.trim().lowercase()}"
    private fun artistFromKey(key: String): String = key.substringBefore('\u0000')
    private fun trackFromKey(key: String): String = key.substringAfter('\u0000')

    private fun encode(values: List<KnowledgeValue>): String = JSONArray().apply {
        values.forEach { value ->
            put(JSONObject().apply {
                put("artist", value.artist)
                value.track?.let { put("track", it) }
                value.score?.let { put("score", it) }
                put("tags", JSONArray(value.tags))
            })
        }
    }.toString()

    private fun decode(payload: String?): List<KnowledgeValue> {
        if (payload.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(payload)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val tags = item.optJSONArray("tags")?.let { tagsArray ->
                        buildList { for (tagIndex in 0 until tagsArray.length()) add(tagsArray.optString(tagIndex)) }
                    }.orEmpty()
                    add(KnowledgeValue(item.optString("artist"), item.optString("track").takeIf { it.isNotBlank() }, item.optDouble("score", Double.NaN).toFloatOrNull(), tags))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun Double.toFloatOrNull(): Float? = if (isNaN() || isInfinite()) null else toFloat()

    private companion object { const val CacheTtlMs = 7L * 24L * 60L * 60L * 1_000L }
}
