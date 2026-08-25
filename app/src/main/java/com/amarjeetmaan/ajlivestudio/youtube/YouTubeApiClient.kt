package com.amarjeetmaan.ajlivestudio.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

enum class YouTubePrivacy(val apiValue: String, val label: String) {
    PUBLIC("public", "Public"),
    UNLISTED("unlisted", "Unlisted"),
    PRIVATE("private", "Private"),
}

data class YouTubeBroadcastResult(
    val broadcastId: String,
    val streamId: String,
    val ingestUrl: String,   // e.g. rtmp://a.rtmp.youtube.com/live2
    val streamKey: String,
    val watchUrl: String,
)

/**
 * Direct REST calls to YouTube Data API v3 — no google-api-client
 * dependency, just documented, stable JSON endpoints:
 *   POST /youtube/v3/liveBroadcasts
 *   POST /youtube/v3/liveStreams
 *   POST /youtube/v3/liveBroadcasts/bind
 *
 * These three endpoints + their required fields are official, stable
 * YouTube Data API v3 surface — much more predictable to implement
 * correctly without a compile-test than an internal library API.
 */
class YouTubeApiClient(private val accessToken: String) {

    private val client = OkHttpClient()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun createLiveBroadcastAndStream(
        title: String,
        description: String,
        privacy: YouTubePrivacy,
    ): Result<YouTubeBroadcastResult> = withContext(Dispatchers.IO) {
        runCatching {
            val broadcastId = createBroadcast(title, description, privacy)
            val (streamId, ingestUrl, streamKey) = createStream(title)
            bindBroadcastToStream(broadcastId, streamId)
            YouTubeBroadcastResult(
                broadcastId = broadcastId,
                streamId = streamId,
                ingestUrl = ingestUrl,
                streamKey = streamKey,
                watchUrl = "https://www.youtube.com/watch?v=$broadcastId",
            )
        }
    }

    private fun createBroadcast(title: String, description: String, privacy: YouTubePrivacy): String {
        val body = JSONObject().apply {
            put("snippet", JSONObject().apply {
                put("title", title)
                put("description", description)
                // scheduledStartTime is required by the API even for "start now" broadcasts
                put("scheduledStartTime", java.time.Instant.now().toString())
            })
            put("status", JSONObject().apply {
                put("privacyStatus", privacy.apiValue)
            })
            put("contentDetails", JSONObject().apply {
                put("enableAutoStart", true)
                put("enableAutoStop", true)
            })
        }
        val response = post(
            "https://www.googleapis.com/youtube/v3/liveBroadcasts?part=snippet,status,contentDetails",
            body
        )
        return response.getString("id")
    }

    private fun createStream(title: String): Triple<String, String, String> {
        val body = JSONObject().apply {
            put("snippet", JSONObject().apply {
                put("title", "$title — stream")
            })
            put("cdn", JSONObject().apply {
                put("frameRate", "variable")
                put("ingestionType", "rtmp")
                put("resolution", "variable")
            })
        }
        val response = post(
            "https://www.googleapis.com/youtube/v3/liveStreams?part=snippet,cdn",
            body
        )
        val streamId = response.getString("id")
        val ingestion = response.getJSONObject("cdn").getJSONObject("ingestionInfo")
        val ingestUrl = ingestion.getString("ingestionAddress")
        val streamKey = ingestion.getString("streamName")
        return Triple(streamId, ingestUrl, streamKey)
    }

    private fun bindBroadcastToStream(broadcastId: String, streamId: String) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/youtube/v3/liveBroadcasts/bind?id=$broadcastId&part=id,contentDetails&streamId=$streamId")
            .addHeader("Authorization", "Bearer $accessToken")
            .post("".toRequestBody(null))
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Bind failed: ${resp.code} ${resp.body?.string()}")
        }
    }

    private fun post(url: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Request failed: ${resp.code} $text")
            return JSONObject(text)
        }
    }
}
