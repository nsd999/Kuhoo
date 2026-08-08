package com.kuhoo.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import java.net.URI

class DesktopAudioPlayer : AudioPlayer {
    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    override val playbackState: StateFlow<PlaybackState> = _playbackState

    private val _currentTrack = MutableStateFlow<TrackInfo?>(null)
    override val currentTrack: StateFlow<TrackInfo?> = _currentTrack

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs

    private val _volume = MutableStateFlow(1.0f)
    override val volume: StateFlow<Float> = _volume

    private var mediaPlayer: MediaPlayer? = null

    init {
        try {
            Platform.startup {}
        } catch (e: IllegalStateException) {
            // Toolkit already initialized
        }
    }

    override fun playTrack(track: TrackInfo) {
        stop()
        _currentTrack.value = track
        _durationMs.value = track.durationMs
        _positionMs.value = 0L

        val streamUrl = track.streamUrl ?: return
        _playbackState.value = PlaybackState.BUFFERING

        Platform.runLater {
            try {
                val media = Media(URI(streamUrl).toString())
                mediaPlayer = MediaPlayer(media).apply {
                    volume = _volume.value.toDouble()
                    
                    setOnReady {
                        _durationMs.value = media.duration.toMillis().toLong()
                        play()
                    }
                    
                    setOnPlaying {
                        _playbackState.value = PlaybackState.PLAYING
                    }
                    
                    setOnPaused {
                        _playbackState.value = PlaybackState.PAUSED
                    }
                    
                    setOnEndOfMedia {
                        _playbackState.value = PlaybackState.COMPLETED
                        stop()
                    }
                    
                    setOnError {
                        println("MediaPlayer Error: ${error.message}")
                        _playbackState.value = PlaybackState.ERROR
                    }
                    
                    currentTimeProperty().addListener { _, _, newValue ->
                        _positionMs.value = newValue.toMillis().toLong()
                    }
                }
            } catch (e: Exception) {
                println("Media Initialization Error: ${e.message}")
                _playbackState.value = PlaybackState.ERROR
            }
        }
    }

    override fun play() {
        Platform.runLater {
            mediaPlayer?.play()
        }
    }

    override fun pause() {
        Platform.runLater {
            mediaPlayer?.pause()
        }
    }

    override fun seekTo(positionMs: Long) {
        Platform.runLater {
            mediaPlayer?.seek(javafx.util.Duration(positionMs.toDouble()))
        }
    }

    override fun setVolume(volume: Float) {
        _volume.value = volume.coerceIn(0.0f, 1.0f)
        Platform.runLater {
            mediaPlayer?.volume = _volume.value.toDouble()
        }
    }

    override fun stop() {
        Platform.runLater {
            mediaPlayer?.stop()
            mediaPlayer?.dispose()
            mediaPlayer = null
            _playbackState.value = PlaybackState.IDLE
        }
    }

    override fun release() {
        stop()
    }
}

actual fun createAudioPlayer(): AudioPlayer = DesktopAudioPlayer()
