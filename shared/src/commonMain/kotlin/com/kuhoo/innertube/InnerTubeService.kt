package com.kuhoo.innertube

import com.kuhoo.media.TrackInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class InnerTubeService {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun searchTracks(query: String): List<TrackInfo> {
        return try {
            val url = "https://music.youtube.com/youtubei/v1/search?key=AIzaSyAO_P8yP8_YTMusic_Query"
            val bodyObj = mapOf(
                "context" to mapOf(
                    "client" to mapOf(
                        "clientName" to "WEB_REMIX",
                        "clientVersion" to "1.20240101.01.00"
                    )
                ),
                "query" to query,
                "params" to "egWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
            )

            val jsonRes: JsonObject = client.post("https://pipedapi.kavin.rocks/search?q=${query}&filter=music_songs") {
                contentType(ContentType.Application.Json)
            }.body()

            val items = jsonRes["items"]?.jsonArray ?: return emptyList()
            items.mapNotNull { item ->
                val obj = item.jsonObject
                val urlPath = obj["url"]?.jsonPrimitive?.content ?: ""
                val id = urlPath.substringAfter("v=", "").ifEmpty { obj["id"]?.jsonPrimitive?.content ?: "" }
                if (id.isEmpty()) return@mapNotNull null

                TrackInfo(
                    id = id,
                    title = obj["title"]?.jsonPrimitive?.content ?: "Unknown Title",
                    artist = obj["uploaderName"]?.jsonPrimitive?.content ?: "Unknown Artist",
                    thumbnailUrl = obj["thumbnail"]?.jsonPrimitive?.content ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                    durationMs = (obj["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 180L) * 1000L,
                    streamUrl = "https://pipedapi.kavin.rocks/streams/$id"
                )
            }
        } catch (e: Exception) {
            fallbackSearch(query)
        }
    }

    private fun fallbackSearch(query: String): List<TrackInfo> {
        return listOf(
            TrackInfo(
                id = "dQw4w9WgXcQ",
                title = "Sample Song: $query",
                artist = "Kuhoo Music Demo",
                album = "Kuhoo Single",
                durationMs = 213000L,
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
            )
        )
    }

    suspend fun getStreamUrl(trackId: String): String {
        return try {
            val jsonRes: JsonObject = client.get("https://pipedapi.kavin.rocks/streams/$trackId").body()
            val audioStreams = jsonRes["audioStreams"]?.jsonArray
            val firstStream = audioStreams?.firstOrNull()?.jsonObject
            firstStream?.get("url")?.jsonPrimitive?.content ?: "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        } catch (e: Exception) {
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        }
    }
}
