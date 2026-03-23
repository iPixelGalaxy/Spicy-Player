package com.tx24.spicyplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tx24.spicyplayer.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)

    // ── Lyrics ────────────────────────────────────────────────────────────
    val lyricsOffsetMs   = repo.lyricsOffsetMs.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val lyricsFontSize   = repo.lyricsFontSize.stateIn(viewModelScope, SharingStarted.Eagerly, "MEDIUM")

    // ── Audio ─────────────────────────────────────────────────────────────
    val eqPreset         = repo.eqPreset.stateIn(viewModelScope, SharingStarted.Eagerly, "FLAT")
    val bassBoostEnabled = repo.bassBoostEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val bassBoostStrength = repo.bassBoostStrength.stateIn(viewModelScope, SharingStarted.Eagerly, 800)
    val crossfadeDuration = repo.crossfadeDuration.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val gaplessPlayback  = repo.gaplessPlayback.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // ── Appearance ────────────────────────────────────────────────────────
    val appTheme         = repo.appTheme.stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM")
    val materialYou      = repo.materialYou.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val controlsStyle    = repo.controlsStyle.stateIn(viewModelScope, SharingStarted.Eagerly, "EXPRESSIVE")
    val backgroundBlur   = repo.backgroundBlur.stateIn(viewModelScope, SharingStarted.Eagerly, 60)
    val pureBlack        = repo.pureBlack.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val contrastLevel    = repo.contrastLevel.stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    // ── General ───────────────────────────────────────────────────────────
    val keepScreenOn     = repo.keepScreenOn.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val audioFocus       = repo.audioFocus.stateIn(viewModelScope, SharingStarted.Eagerly, "PAUSE")
    val scanDirectory    = repo.scanDirectory.stateIn(viewModelScope, SharingStarted.Eagerly, "/sdcard/Music/")

    // ── Setters ───────────────────────────────────────────────────────────
    fun setLyricsOffsetMs(v: Int)        = viewModelScope.launch { repo.setLyricsOffsetMs(v) }
    fun setLyricsFontSize(v: String)     = viewModelScope.launch { repo.setLyricsFontSize(v) }

    fun setEqPreset(v: String)           = viewModelScope.launch { repo.setEqPreset(v) }
    fun setBassBoost(v: Boolean)         = viewModelScope.launch { repo.setBassBoost(v) }
    fun setBassBoostStrength(v: Int)     = viewModelScope.launch { repo.setBassBoostStrength(v) }
    fun setCrossfadeDuration(v: Int)     = viewModelScope.launch { repo.setCrossfadeDuration(v) }
    fun setGaplessPlayback(v: Boolean)   = viewModelScope.launch { repo.setGaplessPlayback(v) }

    fun setAppTheme(v: String)           = viewModelScope.launch { repo.setAppTheme(v) }
    fun setMaterialYou(v: Boolean)       = viewModelScope.launch { repo.setMaterialYou(v) }
    fun setControlsStyle(v: String)      = viewModelScope.launch { repo.setControlsStyle(v) }
    fun setBackgroundBlur(v: Int)        = viewModelScope.launch { repo.setBackgroundBlur(v) }
    fun setPureBlack(v: Boolean)         = viewModelScope.launch { repo.setPureBlack(v) }
    fun setContrastLevel(v: Float)       = viewModelScope.launch { repo.setContrastLevel(v) }

    fun setKeepScreenOn(v: Boolean)      = viewModelScope.launch { repo.setKeepScreenOn(v) }
    fun setAudioFocus(v: String)         = viewModelScope.launch { repo.setAudioFocus(v) }
    fun setScanDirectory(v: String)      = viewModelScope.launch { repo.setScanDirectory(v) }
}
