package com.tx24.spicyplayer.player

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DefaultDataSource

/**
 * A wrapper around ExoPlayer to manage audio playback.
 * Handles playlist preparation, resource management, and real-time audio effects
 * (Equalizer, Bass Boost) via Android AudioEffect API.
 */
class AudioPlayer(private val context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    init {
        // Simple default setup
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            /* handleAudioFocus = */ true
        )
    }

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    // ── Audio session setup ───────────────────────────────────────────────────

    /**
     * (Re-)attaches AudioEffect instances to the current audio session.
     * Must be called whenever a new media item starts playing so the session ID is valid.
     */
    fun attachEffects() {
        val sessionId = player.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return

        try {
            equalizer?.release()
            equalizer = Equalizer(0, sessionId).apply { enabled = true }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Equalizer init failed", e)
        }

        try {
            bassBoost?.release()
            bassBoost = BassBoost(0, sessionId).apply { enabled = true }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "BassBoost init failed", e)
        }
    }

    // ── Equalizer ─────────────────────────────────────────────────────────────

    /**
     * Applies an EQ preset by name: FLAT, BASS_BOOST, TREBLE, VOCAL, CUSTOM.
     * CUSTOM is a no-op (user manages bands via EqualizerScreen directly).
     */
    fun applyEqPreset(preset: String) {
        val eq = equalizer ?: return
        if (!eq.enabled) eq.enabled = true

        // Band levels in milliBels for 5-band EQ: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz
        val levels: List<Short> = when (preset) {
            "BASS"       -> listOf(600, 400,    0, -200, -200)
            "TREBLE"     -> listOf(-200, -200,  0,  400,  600)
            "VOCAL"      -> listOf(-100,    0, 300,  300,    0)
            "FLAT"       -> listOf(   0,    0,   0,    0,    0)
            else         -> return  // CUSTOM — leave as-is
        }

        val numBands = eq.numberOfBands.toInt()
        for (i in 0 until minOf(numBands, levels.size)) {
            try {
                eq.setBandLevel(i.toShort(), levels[i])
            } catch (e: Exception) {
                Log.e("AudioPlayer", "EQ setBandLevel($i) failed", e)
            }
        }
    }

    /**
     * Applies raw per-band gains (in dB, −12..+12) to the equalizer.
     * Used when the user manually adjusts sliders in EqualizerScreen.
     */
    fun applyEqBands(gainsDb: List<Float>) {
        val eq = equalizer ?: return
        val numBands = eq.numberOfBands.toInt()
        for (i in 0 until minOf(numBands, gainsDb.size)) {
            // Convert dB to milliBels (1 dB = 100 mB)
            val mb = (gainsDb[i] * 100).toInt().toShort()
            try { eq.setBandLevel(i.toShort(), mb) } catch (_: Exception) {}
        }
    }

    // ── Bass Boost ────────────────────────────────────────────────────────────

    fun setBassBoost(enabled: Boolean, strength: Int) {
        bassBoost?.enabled = enabled
        if (enabled) {
            try {
                bassBoost?.setStrength(strength.toShort())
            } catch (e: Exception) {
                Log.e("AudioPlayer", "BassBoost setStrength failed", e)
            }
        }
    }

    // ── Playlist ──────────────────────────────────────────────────────────────

    /**
     * Prepares a playlist from a list of URIs (file paths).
     * Uses a [ConcatenatingMediaSource] to support gapless playback for FLAC files.
     *
     * @param uris List of absolute file paths to the audio files.
     * @param playWhenReady Whether to start playback immediately after preparation.
     */
    fun preparePlaylist(uris: List<String>, playWhenReady: Boolean = true) {
        val dataSourceFactory = DefaultDataSource.Factory(context)
        val mediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory)

        val concatenatingMediaSource = ConcatenatingMediaSource()
        for (uri in uris) {
            val mediaItem = MediaItem.fromUri(uri)
            val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
            concatenatingMediaSource.addMediaSource(mediaSource)
        }

        player.setMediaSource(concatenatingMediaSource)
        player.prepare()
        player.playWhenReady = playWhenReady

        // Attach effects after prepare so the audio session ID is valid
        player.postDelayed({ attachEffects() }, 200)
    }

    /**
     * Updates the audio focus handling policy of the player.
     * @param mode "PAUSE" or "DUCK"
     */
    fun updateAudioFocus(mode: String) {
        val handleFocus = mode == "PAUSE"
        val attributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        
        player.setAudioAttributes(attributes, handleFocus)
    }

    /**
     * Releases the audio player and all audio effect resources.
     */
    fun release() {
        equalizer?.release()
        equalizer = null
        bassBoost?.release()
        bassBoost = null
        player.release()
    }
}

// Extension to post a delayed action on the player's application thread
private fun ExoPlayer.postDelayed(action: () -> Unit, delayMs: Long) {
    applicationLooper.let {
        android.os.Handler(it).postDelayed(action, delayMs)
    }
}
