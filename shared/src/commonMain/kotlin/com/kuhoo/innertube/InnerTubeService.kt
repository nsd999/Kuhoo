package com.kuhoo.innertube

import com.kuhoo.media.ItemType
import com.kuhoo.media.TrackInfo
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.YouTube.SearchFilter
import com.music.innertube.YouTubeExtractor
import com.music.innertube.models.SongItem
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.models.YouTubeClient
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
 *
 * Uses the original vivi-music innertube APIs:
 * - YouTube.home() for home feed
 * - YouTube.explore() for explore page
 * - YouTube.getChartsPage() for charts
 * - YouTube.playlist() for playlist songs
 * - YouTube.album() for album songs
 * - YouTube.artist() for artist pages
 * - YouTube.next() for queue/up-next and lyrics endpoints
 * - YouTube.player() + NewPipeExtractor for stream URLs
 * - YouTube.transcript() for synced lyrics
 * - YouTube.searchSummary() / YouTube.search() for search
 * - YouTube.browse() for mood/genre browsing
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
            // Pre-initialize YouTubeExtractor for stream decryption
            try { YouTubeExtractor.ensureInitialized() } catch (_: Exception) {}
            initialized = true
        }
    }

    // =========================================================================
    // SEARCH
    // =========================================================================

    suspend fun searchTracks(query: String): List<TrackInfo> {
        ensureInitialized()
        return try {
            val result = YouTube.searchSummary(query).getOrNull()
            val songs = result?.summaries
                ?.flatMap { it.items }
                ?.filterIsInstance<SongItem>()
                ?.map { it.toTrackInfo() }
            if (!songs.isNullOrEmpty()) return songs

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

    /**
     * Full search returning all item types (songs, albums, artists, playlists)
     * Uses YouTube.searchSummary() for categorized results.
     */
    suspend fun searchAll(query: String): List<TrackInfo> {
        ensureInitialized()
        return try {
            val result = YouTube.searchSummary(query).getOrNull()
            val summaries = result?.summaries
                ?.flatMap { it.items }
                ?.map { it.toTrackInfo() }
                ?: emptyList()
                
            if (summaries.isNotEmpty()) return summaries

            // Fallback for queries where searchSummary fails
            val searchResult = YouTube.search(query).getOrNull()
            searchResult?.items
                ?.map { it.toTrackInfo() }
                ?: emptyList()
        } catch (e: Exception) {
            println("[KuhooSearchAll] Error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getSearchSuggestions(query: String): List<String> {
        ensureInitialized()
        return try {
            val result = YouTube.searchSuggestions(query).getOrNull()
            result?.queries ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    // =========================================================================
    // HOME FEED
    // =========================================================================

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

    // =========================================================================
    // EXPLORE (New Releases, Moods & Genres, Charts)
    // =========================================================================

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
                            id = mood.endpoint.browseId ?: mood.title,
                            title = mood.title,
                            artist = "",
                            thumbnailUrl = "",
                            streamUrl = "",
                            itemType = ItemType.PLAYLIST
                        )
                    }
                ))
            }

            // Also load charts from the original vivi API
            try {
                val charts = YouTube.getChartsPage().getOrNull()
                charts?.sections?.forEach { section ->
                    sections.add(HomeSection(
                        title = section.title,
                        items = section.items.map { it.toTrackInfo() }
                    ))
                }
            } catch (_: Exception) {}

            sections
        } catch (e: Exception) {
            println("[KuhooExplore] Error: ${e.message}")
            emptyList()
        }
    }

    // =========================================================================
    // BROWSE (Mood & Genre detail) - uses YouTube.browse()
    // =========================================================================

    suspend fun browseMoodOrGenre(browseId: String, params: String?): List<HomeSection> {
        ensureInitialized()
        return try {
            val result = YouTube.browse(browseId, params).getOrNull() ?: return emptyList()
            result.items.map { item ->
                HomeSection(
                    title = item.title ?: "Results",
                    items = item.items.map { it.toTrackInfo() }
                )
            }
        } catch (e: Exception) {
            println("[KuhooBrowse] Error: ${e.message}")
            emptyList()
        }
    }

    // =========================================================================
    // PLAYLIST DETAIL - uses YouTube.playlist()
    // =========================================================================

    suspend fun getPlaylistSongs(playlistId: String): PlaylistDetail {
        ensureInitialized()
        return try {
            val page = YouTube.playlist(playlistId).getOrNull()
                ?: return PlaylistDetail("Playlist", "", emptyList())
            PlaylistDetail(
                title = page.playlist.title,
                thumbnailUrl = page.playlist.thumbnail ?: "",
                songs = page.songs.map { it.toTrackInfo() }
            )
        } catch (e: Exception) {
            println("[KuhooPlaylist] Error fetching playlist $playlistId: ${e.message}")
            PlaylistDetail("Playlist", "", emptyList())
        }
    }

    // =========================================================================
    // ALBUM DETAIL - uses YouTube.album()
    // =========================================================================

    suspend fun getAlbumSongs(browseId: String): AlbumDetail {
        ensureInitialized()
        return try {
            val page = YouTube.album(browseId).getOrNull()
                ?: return AlbumDetail("Album", "", "", null, emptyList())
            AlbumDetail(
                title = page.album.title,
                artist = page.album.artists?.joinToString(", ") { it.name } ?: "Unknown Artist",
                thumbnailUrl = page.album.thumbnail,
                year = page.album.year,
                songs = page.songs.map { it.toTrackInfo() },
                description = page.description
            )
        } catch (e: Exception) {
            println("[KuhooAlbum] Error fetching album $browseId: ${e.message}")
            AlbumDetail("Album", "", "", null, emptyList())
        }
    }

    // =========================================================================
    // ARTIST DETAIL - uses YouTube.artist()
    // =========================================================================

    suspend fun getArtistDetail(browseId: String): ArtistDetail {
        ensureInitialized()
        return try {
            val page = YouTube.artist(browseId).getOrNull()
                ?: return ArtistDetail("Artist", "", "", emptyList())
            ArtistDetail(
                name = page.artist.title,
                thumbnailUrl = page.artist.thumbnail ?: "",
                description = page.description ?: "",
                sections = page.sections.map { section ->
                    HomeSection(
                        title = section.title,
                        items = section.items.map { it.toTrackInfo() }
                    )
                },
                subscriberCount = page.subscriberCountText
            )
        } catch (e: Exception) {
            println("[KuhooArtist] Error fetching artist $browseId: ${e.message}")
            ArtistDetail("Artist", "", "", emptyList())
        }
    }

    // =========================================================================
    // STREAM URL EXTRACTION
    // Uses the original vivi approach:
    //  1. YouTube.player() with IOS client (direct URLs, no signature)
    //  2. YouTube.player() with ANDROID_MUSIC client
    //  3. NewPipeExtractor.newPipePlayer() as fallback
    //  4. Piped API as last resort
    // =========================================================================

    suspend fun getStreamUrl(trackId: String): String {
        if (trackId.length != 11) return ""

        return try {
            // Strategy 1: IOS client (usually gives direct audio URLs)
            val iosResult = YouTube.player(
                videoId = trackId,
                client = YouTubeClient.IOS
            ).getOrNull()

            if (iosResult?.playabilityStatus?.status == "OK") {
                val url = iosResult.streamingData?.adaptiveFormats
                    ?.filter { it.mimeType?.startsWith("audio/") == true }
                    ?.sortedByDescending { it.bitrate }
                    ?.firstNotNullOfOrNull { format ->
                        format.url?.takeIf { it.isNotEmpty() }
                            ?: try { NewPipeExtractor.getStreamUrl(format, trackId) } catch (_: Exception) { null }
                    }
                if (!url.isNullOrEmpty()) return url
            }

            // Strategy 2: ANDROID_MUSIC client
            val androidResult = YouTube.player(
                videoId = trackId,
                client = YouTubeClient.ANDROID_MUSIC
            ).getOrNull()

            if (androidResult?.playabilityStatus?.status == "OK") {
                val url = androidResult.streamingData?.adaptiveFormats
                    ?.filter { it.mimeType?.startsWith("audio/") == true }
                    ?.sortedByDescending { it.bitrate }
                    ?.firstNotNullOfOrNull { format ->
                        format.url?.takeIf { it.isNotEmpty() }
                            ?: try { NewPipeExtractor.getStreamUrl(format, trackId) } catch (_: Exception) { null }
                    }
                if (!url.isNullOrEmpty()) return url
            }

            // Strategy 3: NewPipe full extraction
            try {
                val streams = NewPipeExtractor.newPipePlayer(trackId)
                val audioUrl = streams.firstOrNull()?.second
                if (!audioUrl.isNullOrEmpty()) return audioUrl
            } catch (_: Exception) {}

            // Strategy 4: Piped API as last resort
            pipedFallback(trackId)
        } catch (e: Exception) {
            println("[KuhooStream] All strategies failed for $trackId: ${e.message}")
            pipedFallback(trackId)
        }
    }

    private suspend fun pipedFallback(trackId: String): String {
        return try {
            val response = pipedClient.get("https://pipedapi.kavin.rocks/streams/$trackId")
            if (response.status.value in 200..299) {
                val jsonRes: JsonObject = response.body()
                val audioStreams = jsonRes["audioStreams"]?.jsonArray
                val bestStream = audioStreams
                    ?.mapNotNull { it.jsonObject }
                    ?.filter { it["mimeType"]?.jsonPrimitive?.content?.contains("audio") == true }
                    ?.maxByOrNull { it["bitrate"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0 }
                bestStream?.get("url")?.jsonPrimitive?.content ?: ""
            } else ""
        } catch (_: Exception) { "" }
    }

    // =========================================================================
    // QUEUE / UP NEXT - uses YouTube.next()
    // =========================================================================

    suspend fun getQueue(videoId: String, playlistId: String? = null): List<TrackInfo> {
        ensureInitialized()
        return try {
            val result = YouTube.next(WatchEndpoint(videoId = videoId, playlistId = playlistId)).getOrNull()
            result?.items?.map { it.toTrackInfo() } ?: emptyList()
        } catch (e: Exception) {
            println("[KuhooQueue] Error: ${e.message}")
            emptyList()
        }
    }

    // =========================================================================
    // LYRICS - uses YouTube.transcript() for synced, YouTube.lyrics() for plain
    // =========================================================================

    suspend fun getSyncedLyrics(videoId: String): String? {
        ensureInitialized()
        return try {
            YouTube.transcript(videoId).getOrNull()
        } catch (e: Exception) {
            println("[KuhooLyrics] Transcript failed for $videoId: ${e.message}")
            null
        }
    }

    suspend fun getPlainLyrics(videoId: String): String? {
        ensureInitialized()
        return try {
            val nextResult = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()
            val lyricsEndpoint = nextResult?.lyricsEndpoint ?: return null
            YouTube.lyrics(lyricsEndpoint).getOrNull()
        } catch (e: Exception) {
            println("[KuhooLyrics] Plain lyrics failed for $videoId: ${e.message}")
            null
        }
    }

    // =========================================================================
    // RELATED SONGS - uses YouTube.next() + YouTube.related()
    // =========================================================================

    suspend fun getRelatedSongs(videoId: String): List<TrackInfo> {
        ensureInitialized()
        return try {
            val nextResult = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()
            val relatedEndpoint = nextResult?.relatedEndpoint ?: return emptyList()
            val related = YouTube.related(relatedEndpoint).getOrNull() ?: return emptyList()
            related.songs.map { it.toTrackInfo() } +
                related.albums.map { it.toTrackInfo() } +
                related.playlists.map { it.toTrackInfo() }
        } catch (e: Exception) {
            println("[KuhooRelated] Error: ${e.message}")
            emptyList()
        }
    }
}

// =========================================================================
// DATA MODELS
// =========================================================================

data class HomeSection(
    val title: String,
    val items: List<TrackInfo>
)

data class PlaylistDetail(
    val title: String,
    val thumbnailUrl: String,
    val songs: List<TrackInfo>
)

data class AlbumDetail(
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val year: Int?,
    val songs: List<TrackInfo>,
    val description: String? = null
)

data class ArtistDetail(
    val name: String,
    val thumbnailUrl: String,
    val description: String,
    val sections: List<HomeSection>,
    val subscriberCount: String? = null
)

// =========================================================================
// EXTENSION FUNCTIONS (YTItem -> TrackInfo)
// =========================================================================

fun SongItem.toTrackInfo(): TrackInfo {
    return TrackInfo(
        id = id,
        title = title,
        artist = artists.joinToString(", ") { it.name },
        album = album?.name,
        durationMs = (duration?.toLong() ?: 180L) * 1000L,
        thumbnailUrl = thumbnail,
        streamUrl = "",
        itemType = ItemType.SONG
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
            streamUrl = "",
            itemType = ItemType.ALBUM
        )
        is ArtistItem -> TrackInfo(
            id = id,
            title = title,
            artist = title,
            thumbnailUrl = thumbnail ?: "",
            streamUrl = "",
            itemType = ItemType.ARTIST
        )
        is PlaylistItem -> TrackInfo(
            id = id,
            title = title,
            artist = author?.name ?: "Playlist",
            thumbnailUrl = thumbnail ?: "",
            streamUrl = "",
            itemType = ItemType.PLAYLIST
        )
    }
}
