package com.kuhoo.db

import com.kuhoo.media.TrackInfo
import com.kuhoo.media.ItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository that wraps KuhooDatabase for managing user's music library.
 * Handles liked songs, recently played, most played, playlists, etc.
 */
class MusicRepository(private val database: KuhooDatabase) {

    // =========================================================================
    // PLAY TRACKING
    // =========================================================================

    /**
     * Record a song play — updates Song table, PlayStat, and RecentlyPlayed.
     */
    suspend fun recordPlay(track: TrackInfo) = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()

        // Upsert song into Song table
        database.kuhooDatabaseQueries.insertSong(
            id = track.id,
            title = track.title,
            artistName = track.artist,
            albumName = track.album,
            durationMs = track.durationMs,
            thumbnailUrl = track.thumbnailUrl,
            audioUrl = track.streamUrl,
            isLiked = 0, // Preserve existing like status
            isDownloaded = 0,
            downloadPath = null,
            addedAt = now
        )

        // Increment play count
        database.kuhooDatabaseQueries.incrementPlayCount(
            songId = track.id,
            lastPlayed = now
        )

        // Update recently played
        database.kuhooDatabaseQueries.insertRecentlyPlayed(
            songId = track.id,
            title = track.title,
            artistName = track.artist,
            albumName = track.album,
            durationMs = track.durationMs,
            thumbnailUrl = track.thumbnailUrl,
            lastPlayedAt = now
        )
    }

    // =========================================================================
    // LIKED SONGS
    // =========================================================================

    suspend fun toggleLike(songId: String): Boolean = withContext(Dispatchers.Default) {
        val song = database.kuhooDatabaseQueries.getSongById(songId).executeAsOneOrNull()
        val newLikeState = if (song?.isLiked == 1L) 0L else 1L
        database.kuhooDatabaseQueries.setLiked(isLiked = newLikeState, id = songId)
        newLikeState == 1L
    }

    suspend fun isLiked(songId: String): Boolean = withContext(Dispatchers.Default) {
        database.kuhooDatabaseQueries.getSongById(songId).executeAsOneOrNull()?.isLiked == 1L
    }

    suspend fun getLikedSongs(): List<TrackInfo> = withContext(Dispatchers.Default) {
        database.kuhooDatabaseQueries.getLikedSongs().executeAsList().map { song ->
            TrackInfo(
                id = song.id,
                title = song.title,
                artist = song.artistName,
                album = song.albumName,
                durationMs = song.durationMs,
                thumbnailUrl = song.thumbnailUrl,
                streamUrl = song.audioUrl,
                itemType = ItemType.SONG
            )
        }
    }

    // =========================================================================
    // RECENTLY PLAYED
    // =========================================================================

    suspend fun getRecentlyPlayed(): List<TrackInfo> = withContext(Dispatchers.Default) {
        database.kuhooDatabaseQueries.getRecentlyPlayed().executeAsList().map { rp ->
            TrackInfo(
                id = rp.songId,
                title = rp.title,
                artist = rp.artistName,
                album = rp.albumName,
                durationMs = rp.durationMs,
                thumbnailUrl = rp.thumbnailUrl,
                itemType = ItemType.SONG
            )
        }
    }

    // =========================================================================
    // MOST PLAYED
    // =========================================================================

    suspend fun getMostPlayed(): List<TrackInfo> = withContext(Dispatchers.Default) {
        val threshold = System.currentTimeMillis() - 48 * 60 * 60 * 1000L // 48 hours ago
        val topIds = database.kuhooDatabaseQueries.getMostPlayedSongIds(threshold).executeAsList()
        topIds.mapNotNull { id ->
            database.kuhooDatabaseQueries.getSongById(id).executeAsOneOrNull()?.let { song ->
                TrackInfo(
                    id = song.id,
                    title = song.title,
                    artist = song.artistName,
                    album = song.albumName,
                    durationMs = song.durationMs,
                    thumbnailUrl = song.thumbnailUrl,
                    streamUrl = song.audioUrl,
                    itemType = ItemType.SONG
                )
            }
        }
    }

    // =========================================================================
    // DOWNLOADED SONGS
    // =========================================================================

    suspend fun getDownloadedSongs(): List<TrackInfo> = withContext(Dispatchers.Default) {
        database.kuhooDatabaseQueries.getDownloadedSongs().executeAsList().map { song ->
            TrackInfo(
                id = song.id,
                title = song.title,
                artist = song.artistName,
                album = song.albumName,
                durationMs = song.durationMs,
                thumbnailUrl = song.thumbnailUrl,
                streamUrl = song.audioUrl,
                itemType = ItemType.SONG
            )
        }
    }

    // =========================================================================
    // TOP ARTISTS (for personalized home feed)
    // =========================================================================

    suspend fun getTopArtists(): List<String> = withContext(Dispatchers.Default) {
        database.kuhooDatabaseQueries.getTopArtists().executeAsList().map { it.artistName }
    }

    // =========================================================================
    // USER PLAYLISTS
    // =========================================================================

    suspend fun getAllPlaylists(): List<Playlist> = withContext(Dispatchers.Default) {
        database.kuhooDatabaseQueries.getAllPlaylists().executeAsList()
    }

    suspend fun createPlaylist(name: String): String = withContext(Dispatchers.Default) {
        val id = "kuhoo_${System.currentTimeMillis()}"
        database.kuhooDatabaseQueries.insertPlaylist(
            id = id,
            name = name,
            createdAt = System.currentTimeMillis(),
            songCount = 0
        )
        id
    }

    suspend fun addToPlaylist(playlistId: String, track: TrackInfo) = withContext(Dispatchers.Default) {
        // Ensure song exists
        database.kuhooDatabaseQueries.insertSong(
            id = track.id, title = track.title, artistName = track.artist,
            albumName = track.album, durationMs = track.durationMs,
            thumbnailUrl = track.thumbnailUrl, audioUrl = track.streamUrl,
            isLiked = 0, isDownloaded = 0, downloadPath = null,
            addedAt = System.currentTimeMillis()
        )
        // Get next position
        val songs = database.kuhooDatabaseQueries.getPlaylistSongs(playlistId).executeAsList()
        database.kuhooDatabaseQueries.insertPlaylistSong(
            playlistId = playlistId,
            songId = track.id,
            position = songs.size.toLong()
        )
    }

    // =========================================================================
    // SEARCH HISTORY
    // =========================================================================

    suspend fun saveSearch(query: String) = withContext(Dispatchers.Default) {
        database.kuhooDatabaseQueries.insertSearch(query, System.currentTimeMillis())
    }

    suspend fun getRecentSearches(): List<String> = withContext(Dispatchers.Default) {
        database.kuhooDatabaseQueries.getRecentSearches().executeAsList().map { it.query }
    }
}
