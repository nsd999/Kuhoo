package com.kuhoo.media

import kotlinx.coroutines.flow.StateFlow

enum class PlaybackState {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    COMPLETED,
    ERROR
}

enum class ItemType {
    SONG, ALBUM, PLAYLIST, ARTIST
}

data class TrackInfo(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val thumbnailUrl: String? = null,
    val streamUrl: String? = null,
    val itemType: ItemType = ItemType.SONG
)

interface AudioPlayer {
    val playbackState: StateFlow<PlaybackState>
    val currentTrack: StateFlow<TrackInfo?>
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val volume: StateFlow<Float>

    fun playTrack(track: TrackInfo)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun stop()
    fun release()
}

expect fun createAudioPlayer(): AudioPlayer
