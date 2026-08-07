package com.kuhoo.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.net.URL
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine

class DesktopAudioPlayer : AudioPlayer {
    private val scope = CoroutineScope(Dispatchers.Default + Job())

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

    private var line: SourceDataLine? = null
    private var playJob: Job? = null
    private var isPaused = false

    override fun playTrack(track: TrackInfo) {
        stop()
        _currentTrack.value = track
        _durationMs.value = track.durationMs
        _positionMs.value = 0L

        val streamUrl = track.streamUrl ?: return
        _playbackState.value = PlaybackState.BUFFERING

        playJob = scope.launch {
            try {
                val connection = URL(streamUrl).openConnection()
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                val bufferedIn = BufferedInputStream(connection.getInputStream())

                val rawIn: AudioInputStream = try {
                    AudioSystem.getAudioInputStream(bufferedIn)
                } catch (e: Exception) {
                    _playbackState.value = PlaybackState.ERROR
                    return@launch
                }

                val baseFormat = rawIn.format
                val decodedFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.sampleRate,
                    16,
                    baseFormat.channels,
                    baseFormat.channels * 2,
                    baseFormat.sampleRate,
                    false
                )

                val din: AudioInputStream = AudioSystem.getAudioInputStream(decodedFormat, rawIn)
                val info = DataLine.Info(SourceDataLine::class.java, decodedFormat)
                val dataLine = AudioSystem.getLine(info) as SourceDataLine
                dataLine.open(decodedFormat)
                dataLine.start()
                line = dataLine

                _playbackState.value = PlaybackState.PLAYING

                val buffer = ByteArray(4096)
                var bytesRead: Int

                val startTime = System.currentTimeMillis()
                while (playJob?.isActive == true && din.read(buffer, 0, buffer.size).also { bytesRead = it } != -1) {
                    if (isPaused) {
                        dataLine.stop()
                        while (isPaused && playJob?.isActive == true) {
                            delay(100)
                        }
                        if (playJob?.isActive == true) {
                            dataLine.start()
                        }
                    }

                    if (bytesRead > 0) {
                        dataLine.write(buffer, 0, bytesRead)
                        _positionMs.value = dataLine.microsecondPosition / 1000
                    }
                }

                dataLine.drain()
                dataLine.close()
                din.close()

                if (playJob?.isActive == true) {
                    _playbackState.value = PlaybackState.COMPLETED
                }
            } catch (e: Exception) {
                if (_playbackState.value != PlaybackState.IDLE) {
                    _playbackState.value = PlaybackState.ERROR
                }
            }
        }
    }

    override fun play() {
        if (_playbackState.value == PlaybackState.PAUSED) {
            isPaused = false
            _playbackState.value = PlaybackState.PLAYING
        } else if (_currentTrack.value != null && _playbackState.value == PlaybackState.IDLE) {
            _currentTrack.value?.let { playTrack(it) }
        }
    }

    override fun pause() {
        if (_playbackState.value == PlaybackState.PLAYING) {
            isPaused = true
            _playbackState.value = PlaybackState.PAUSED
        }
    }

    override fun seekTo(positionMs: Long) {
        _positionMs.value = positionMs
    }

    override fun setVolume(volume: Float) {
        _volume.value = volume.coerceIn(0.0f, 1.0f)
        line?.let { l ->
            if (l.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val gainControl = l.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                val min = gainControl.minimum
                val max = gainControl.maximum
                val gain = min + (max - min) * _volume.value
                gainControl.value = gain
            }
        }
    }

    override fun stop() {
        isPaused = false
        playJob?.cancel()
        playJob = null
        line?.stop()
        line?.close()
        line = null
        _playbackState.value = PlaybackState.IDLE
    }

    override fun release() {
        stop()
    }
}

actual fun createAudioPlayer(): AudioPlayer = DesktopAudioPlayer()
