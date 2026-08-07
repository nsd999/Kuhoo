package com.kuhoo.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.w3c.dom.Audio

class WebAudioPlayer : AudioPlayer {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var audioElement: Audio? = null
    private var positionTicker: Job? = null

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

    override fun playTrack(track: TrackInfo) {
        stop()
        _currentTrack.value = track
        _durationMs.value = track.durationMs
        _positionMs.value = 0L

        val streamUrl = track.streamUrl ?: return
        _playbackState.value = PlaybackState.BUFFERING

        val audio = Audio(streamUrl)
        audioElement = audio
        audio.volume = _volume.value.toDouble()

        audio.oncanplay = { _ ->
            _playbackState.value = PlaybackState.PLAYING
            audio.play()
            startPositionTicker()
            null
        }

        audio.onended = { _ ->
            _playbackState.value = PlaybackState.COMPLETED
            stopPositionTicker()
            null
        }

        audio.onerror = { _, _, _, _, _ ->
            _playbackState.value = PlaybackState.ERROR
            stopPositionTicker()
            null
        }

        audio.load()
    }

    private fun startPositionTicker() {
        stopPositionTicker()
        positionTicker = scope.launch {
            while (_playbackState.value == PlaybackState.PLAYING) {
                audioElement?.let { audio ->
                    _positionMs.value = (audio.currentTime * 1000).toLong()
                    if (!audio.duration.isNaN()) {
                        _durationMs.value = (audio.duration * 1000).toLong()
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopPositionTicker() {
        positionTicker?.cancel()
        positionTicker = null
    }

    override fun play() {
        audioElement?.play()
        _playbackState.value = PlaybackState.PLAYING
        startPositionTicker()
    }

    override fun pause() {
        audioElement?.pause()
        _playbackState.value = PlaybackState.PAUSED
        stopPositionTicker()
    }

    override fun seekTo(positionMs: Long) {
        audioElement?.currentTime = positionMs / 1000.0
        _positionMs.value = positionMs
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        _volume.value = clamped
        audioElement?.volume = clamped.toDouble()
    }

    override fun stop() {
        audioElement?.pause()
        audioElement = null
        stopPositionTicker()
        _playbackState.value = PlaybackState.IDLE
    }

    override fun release() {
        stop()
    }
}

actual fun createAudioPlayer(): AudioPlayer = WebAudioPlayer()
