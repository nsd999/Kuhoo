package com.kuhoo.innertube

import com.kuhoo.media.TrackInfo
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.SearchFilter
import com.music.innertube.pages.HomePage

/**
 * Service that wraps the real innertube YouTube Music API
 * for use in the Kuhoo shared module.
 */
class InnerTubeService {

    suspend fun searchTracks(query: String): List<TrackInfo> {
        return try {
            val result = YouTube.searchSummary(query).getOrNull()
            val items = result?.summaries?.flatMap { it.items }?.filterIsInstance<SongItem>()
            items?.map { it.toTrackInfo() } ?: emptyList()
        } catch (e: Exception) {
            try {
                // Fallback: use regular search
                val result = YouTube.search(query, SearchFilter.FILTER_SONG).getOrNull()
                result?.items?.filterIsInstance<SongItem>()?.map { it.toTrackInfo() } ?: emptyList()
            } catch (e2: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getHomeSections(): List<HomeSection> {
        return try {
            val homePage = YouTube.home().getOrNull() ?: return emptyList()
            homePage.sections.map { section ->
                HomeSection(
                    title = section.title,
                    items = section.items.map { it.toTrackInfo() }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getStreamUrl(trackId: String): String {
        return try {
            // Use NewPipe extractor for stream URLs (no API key needed)
            val streams = YouTube.getNewPipeStreamUrls(trackId)
            // Pick the best audio stream (highest bitrate)
            streams.maxByOrNull { it.first }?.second
                ?: "https://pipedapi.kavin.rocks/streams/$trackId"
        } catch (e: Exception) {
            // Fallback to Piped API
            "https://pipedapi.kavin.rocks/streams/$trackId"
        }
    }

    suspend fun getSearchSuggestions(query: String): List<String> {
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
        streamUrl = "https://pipedapi.kavin.rocks/streams/$id"
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
