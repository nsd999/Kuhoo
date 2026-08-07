package com.kuhoo.innertube

import com.kuhoo.media.TrackInfo
import com.music.innertube.YouTube
import com.music.innertube.YouTube.SearchFilter
import com.music.innertube.models.SongItem
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.HomePage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Service that wraps the real innertube YouTube Music API
 * for use in the Kuhoo shared module.
 */
class InnerTubeService {

    private val pipedClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }

    private var initialized = false

    private suspend fun ensureInitialized() {
        if (!initialized) {
            try {
                YouTube.refreshVisitorData()
            } catch (_: Exception) {}
            initialized = true
        }
    }

    suspend fun searchTracks(query: String): List<TrackInfo> {
        ensureInitialized()
        return try {
            // Try searchSummary first (returns categorized results)
            val result = YouTube.searchSummary(query).getOrNull()
            val songs = result?.summaries
                ?.flatMap { it.items }
                ?.filterIsInstance<SongItem>()
                ?.map { it.toTrackInfo() }
            if (!songs.isNullOrEmpty()) return songs

            // Fallback: use filtered search for songs
            val searchResult = YouTube.search(query, SearchFilter.FILTER_SONG).getOrNull()
            searchResult?.items
                ?.filterIsInstance<SongItem>()
                ?.map { it.toTrackInfo() }
                ?: emptyList()
        } catch (e: Exception) {
            println("[KuhooSearch] Error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getHomeSections(): List<HomeSection> {
        ensureInitialized()
        return try {
            val homePage = YouTube.home().getOrNull() ?: return emptyList()
            homePage.sections.map { section ->
                HomeSection(
                    title = section.title,
                    items = section.items.map { it.toTrackInfo() }
                )
            }
        } catch (e: Exception) {
            println("[KuhooHome] Error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getExploreSections(): List<HomeSection> {
        ensureInitialized()
        return try {
            val explorePage = YouTube.explore().getOrNull() ?: return emptyList()
            val sections = mutableListOf<HomeSection>()

            if (explorePage.newReleaseAlbums.isNotEmpty()) {
                sections.add(HomeSection(
                    title = "New Releases",
                    items = explorePage.newReleaseAlbums.map { it.toTrackInfo() }
                ))
            }
            if (explorePage.moodAndGenres.isNotEmpty()) {
                sections.add(HomeSection(
                    title = "Moods & Genres",
                    items = explorePage.moodAndGenres.map { mood ->
                        TrackInfo(
                            id = mood.title,
                            title = mood.title,
                            artist = "",
                            thumbnailUrl = "",
                            streamUrl = ""
                        )
                    }
                ))
            }
            sections
        } catch (e: Exception) {
            println("[KuhooExplore] Error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getStreamUrl(trackId: String): String {
        // Use Piped API for stream extraction (more reliable for desktop)
        // Note: Piped only works with video IDs (11 chars), not album/playlist IDs
        if (trackId.length != 11) return ""
        
        return try {
            val response = pipedClient.get("https://pipedapi.kavin.rocks/streams/$trackId")
            if (response.status.value in 200..299) {
                val jsonRes: JsonObject = response.body()
                val audioStreams = jsonRes["audioStreams"]?.jsonArray
                // Pick highest quality audio stream
                val bestStream = audioStreams
                    ?.mapNotNull { it.jsonObject }
                    ?.filter { it["mimeType"]?.jsonPrimitive?.content?.contains("audio") == true }
                    ?.maxByOrNull { it["bitrate"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0 }
                bestStream?.get("url")?.jsonPrimitive?.content
                    ?: audioStreams?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                    ?: ""
            } else {
                fallbackStreamExtraction(trackId)
            }
        } catch (e: Exception) {
            println("[KuhooStream] Piped failed for $trackId: ${e.message}")
            fallbackStreamExtraction(trackId)
        }
    }

    private suspend fun fallbackStreamExtraction(trackId: String): String {
        return try {
            val response = pipedClient.get("https://watchapi.whatever.social/streams/$trackId")
            if (response.status.value in 200..299) {
                val jsonRes: JsonObject = response.body()
                jsonRes["audioStreams"]?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content ?: ""
            } else {
                ""
            }
        } catch (_: Exception) { "" }
    }

    suspend fun getSearchSuggestions(query: String): List<String> {
        ensureInitialized()
        return try {
            val result = YouTube.searchSuggestions(query).getOrNull()
            result?.queries ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

data class HomeSection(
    val title: String,
    val items: List<TrackInfo>
)

// Extension functions to convert innertube models to Kuhoo TrackInfo
fun SongItem.toTrackInfo(): TrackInfo {
    return TrackInfo(
        id = id,
        title = title,
        artist = artists.joinToString(", ") { it.name },
        album = album?.name,
        durationMs = (duration?.toLong() ?: 180L) * 1000L,
        thumbnailUrl = thumbnail,
        streamUrl = ""
    )
}

fun YTItem.toTrackInfo(): TrackInfo {
    return when (this) {
        is SongItem -> toTrackInfo()
        is AlbumItem -> TrackInfo(
            id = browseId,
            title = title,
            artist = artists?.joinToString(", ") { it.name } ?: "Unknown Artist",
            album = title,
            thumbnailUrl = thumbnail,
            streamUrl = ""
        )
        is ArtistItem -> TrackInfo(
            id = id,
            title = title,
            artist = title,
            thumbnailUrl = thumbnail ?: "",
            streamUrl = ""
        )
        is PlaylistItem -> TrackInfo(
            id = id,
            title = title,
            artist = author?.name ?: "Playlist",
            thumbnailUrl = thumbnail ?: "",
            streamUrl = ""
        )
    }
}
