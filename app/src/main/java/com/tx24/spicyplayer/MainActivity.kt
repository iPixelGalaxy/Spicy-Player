package com.tx24.spicyplayer

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.util.Log
import java.text.Normalizer
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import android.provider.DocumentsContract
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import com.tx24.spicyplayer.models.Line
import com.tx24.spicyplayer.models.NowPlayingData
import com.tx24.spicyplayer.models.NowPlayingMetadata
import com.tx24.spicyplayer.parser.TtmlLyricsParser
import com.tx24.spicyplayer.player.AudioPlayer
import com.tx24.spicyplayer.ui.canvas.DynamicBackgroundView
import com.tx24.spicyplayer.ui.canvas.SpicyLyricsView
import com.tx24.spicyplayer.ui.components.NowPlayingHeader
import com.tx24.spicyplayer.ui.components.NowPlayingControls
import com.tx24.spicyplayer.ui.settings.EqualizerScreen
import com.tx24.spicyplayer.ui.settings.SettingsScreen
import com.tx24.spicyplayer.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

enum class AppScreen {
    NOW_PLAYING, SETTINGS, EQUALIZER
}

class MainActivity : ComponentActivity() {
    private lateinit var audioPlayer: AudioPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        
        audioPlayer = AudioPlayer(this)
        setContent {
            SpicyPlayerApp(audioPlayer)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioPlayer.release()
    }

    fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpicyPlayerApp(audioPlayer: AudioPlayer) {
    val coroutineScope = rememberCoroutineScope()

    // ── Core playback state ────────────────────────────────────────────────
    var lines by remember { mutableStateOf<List<Line>>(emptyList()) }
    var currentTimeMs by remember { mutableStateOf(0L) }
    var currentDurationMs by remember { mutableLongStateOf(1L) }
    var isPlaying by remember { mutableStateOf(false) }
    var coverArtBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var trackName by remember { mutableStateOf("") }
    var artistName by remember { mutableStateOf("") }
    var currentSongIndex by remember { mutableIntStateOf(-1) }
    var songPairs by remember { mutableStateOf<List<Pair<File, File?>>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    // ── Queue & controls state ─────────────────────────────────────────────
    // playQueue: empty = no explicit queue (sequential through songPairs)
    var playQueue by remember { mutableStateOf<List<Pair<File, File?>>>(emptyList()) }
    var playQueueIndex by remember { mutableIntStateOf(0) }
    var loopMode by remember { mutableIntStateOf(0) }   // 0=off 1=all 2=one
    var showMenu by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf(AppScreen.NOW_PLAYING) }
    var eqNavigationSource by remember { mutableStateOf(AppScreen.NOW_PLAYING) }
    var controlsCollapsed by remember { mutableStateOf(false) }

    // ── Dynamic color from cover art ──────────────────────────────────────
    var dynamicPrimaryColor by remember { mutableStateOf(Color(0xFF6650A4)) }
    var currentBitrate by remember { mutableStateOf("") }
    var currentFormat by remember { mutableStateOf("") }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var lastSongStartTime by remember { mutableLongStateOf(0L) }

    val context = LocalContext.current
    val appLogo = remember { BitmapFactory.decodeResource(context.resources, R.drawable.logo) }

    // ── Settings ──────────────────────────────────────────────────────────
    val settingsVm: SettingsViewModel = viewModel()
    val appTheme           by settingsVm.appTheme.collectAsStateWithLifecycle()
    val materialYou        by settingsVm.materialYou.collectAsStateWithLifecycle()
    val pureBlack          by settingsVm.pureBlack.collectAsStateWithLifecycle()
    val keepScreenOn       by settingsVm.keepScreenOn.collectAsStateWithLifecycle()
    val audioFocusSetting  by settingsVm.audioFocus.collectAsStateWithLifecycle()
    val controlsStyle      by settingsVm.controlsStyle.collectAsStateWithLifecycle()
    val blurIntensity      by settingsVm.backgroundBlur.collectAsStateWithLifecycle()
    val eqPreset           by settingsVm.eqPreset.collectAsStateWithLifecycle()
    val bassBoostEnabled   by settingsVm.bassBoostEnabled.collectAsStateWithLifecycle()
    val lyricsFontSizeStr  by settingsVm.lyricsFontSize.collectAsStateWithLifecycle()
    val lyricsOffsetMs     by settingsVm.lyricsOffsetMs.collectAsStateWithLifecycle()
    val bassBoostStrength  by settingsVm.bassBoostStrength.collectAsStateWithLifecycle()
    
    // ── Apply dynamic audio settings ──────────────────────────────────────
    LaunchedEffect(audioFocusSetting) {
        audioPlayer.updateAudioFocus(audioFocusSetting)
    }

    // ── Back handling ─────────────────────────────────────────────────────
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    
    BackHandler {
        if (currentScreen == AppScreen.EQUALIZER) {
            currentScreen = eqNavigationSource
        } else if (currentScreen != AppScreen.NOW_PLAYING) {
            currentScreen = AppScreen.NOW_PLAYING
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                (context as? ComponentActivity)?.finish()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Derive typed values
    val lyricsFontScale = when (lyricsFontSizeStr) { "SMALL" -> 0.80f; "LARGE" -> 1.25f; else -> 1.0f }
    val systemDark = isSystemInDarkTheme()
    val isDark = when (appTheme) { "LIGHT" -> false; "DARK" -> true; else -> systemDark }

    // ── Immersive mode persistence ─────────────────────────────────────────
    LaunchedEffect(showMenu) {
        if (showMenu) {
            (context as? MainActivity)?.hideSystemBars()
        }
    }

    LaunchedEffect(coverArtBitmap) {
        val bmp = coverArtBitmap ?: appLogo
        if (bmp != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val palette = Palette.from(bmp).generate()
                val rgb = palette.getVibrantColor(
                    palette.getMutedColor(
                        palette.getDominantColor(0xFF6650A4.toInt())
                    )
                )
                launch(Dispatchers.Main) { dynamicPrimaryColor = Color(rgb) }
            }
        } else {
            dynamicPrimaryColor = Color(0xFF6650A4)
        }
    }

    // ── Color scheme: Material You override + pure black + theme ─────────
    val colorScheme = remember(dynamicPrimaryColor, materialYou, isDark, pureBlack) {
        val base = when {
            materialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            else -> {
                val onPrimary = if (dynamicPrimaryColor.luminance() > 0.4f) Color.Black else Color.White
                if (isDark) {
                    darkColorScheme(
                        primary = dynamicPrimaryColor,
                        onPrimary = onPrimary,
                        primaryContainer = dynamicPrimaryColor.copy(alpha = 0.25f),
                        onPrimaryContainer = onPrimary,
                        secondary = dynamicPrimaryColor.copy(alpha = 0.8f),
                        onSecondary = onPrimary,
                        surface = Color(0xFF1C1B1F),
                        onSurface = Color.White,
                        background = Color(0xFF121212),
                        onBackground = Color.White,
                    )
                } else {
                    lightColorScheme(
                        primary = dynamicPrimaryColor,
                        onPrimary = if (dynamicPrimaryColor.luminance() > 0.4f) Color.Black else Color.White,
                    )
                }
            }
        }
        // AMOLED pure black override
        if (pureBlack && isDark) {
            base.copy(background = Color.Black, surface = Color.Black)
        } else base
    }

    // ── Header animation ──────────────────────────────────────────────────
    val nowPlayingData = remember(currentSongIndex, trackName, artistName, coverArtBitmap) {
        NowPlayingData(currentSongIndex, trackName, artistName, coverArtBitmap)
    }

    val headerProgress by animateFloatAsState(
        targetValue = if (currentSongIndex == -1) 0f else 1f,
        animationSpec = tween(800, easing = LinearOutSlowInEasing)
    )

    val currentImageSize = 48.dp + (72.dp * headerProgress)
    val currentSpacerWidth = (16.dp * headerProgress)
    val currentHeaderBias = -1f + headerProgress
    val metadataAlpha = headerProgress

    // ── Permissions ───────────────────────────────────────────────────────
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { _ -> }
    )
    LaunchedEffect(Unit) { permissionLauncher.launch(permissions) }

    LaunchedEffect(Unit) { permissionLauncher.launch(permissions) }

    // ── Normalisation helpers ─────────────────────────────────────────────
    fun robustNormalize(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)
        .lowercase().trim().replace(Regex("\\s+"), " ")

    fun fuzzyNormalize(s: String): String =
        robustNormalize(s)
            .replace(Regex("\\s*[\\[({].*?[\\])}]\\s*"), " ")
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
            .trim()

    // ── Keep screen on ────────────────────────────────────────────────────
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        val window = (view.context as? ComponentActivity)?.window
        if (keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // ── Audio focus behavior ──────────────────────────────────────────────
    DisposableEffect(audioFocusSetting) {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    if (audioFocusSetting != "IGNORE") {
                        audioPlayer.player.pause()
                        isPlaying = false
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    if (audioFocusSetting == "PAUSE") {
                        audioPlayer.player.pause()
                        isPlaying = false
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    when (audioFocusSetting) {
                        "DUCK"  -> audioPlayer.player.volume = 0.3f
                        "PAUSE" -> { audioPlayer.player.pause(); isPlaying = false }
                        // IGNORE -> do nothing
                    }
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    audioPlayer.player.volume = 1.0f
                    if (audioFocusSetting != "IGNORE" && !isPlaying) {
                        audioPlayer.player.play()
                        isPlaying = true
                    }
                }
            }
        }
        val req = AudioManager.OnAudioFocusChangeListener { focusChange -> focusListener.onAudioFocusChange(focusChange) }
        onDispose { /* listener cleanup managed by ExoPlayer internally */ }
    }

    // ── EQ preset side-effect ─────────────────────────────────────────────
    LaunchedEffect(eqPreset) { audioPlayer.applyEqPreset(eqPreset) }

    // ── Bass boost side-effect ─────────────────────────────────────────────
    LaunchedEffect(bassBoostEnabled, bassBoostStrength) { audioPlayer.setBassBoost(bassBoostEnabled, bassBoostStrength) }

    // ── Rescan helper ─────────────────────────────────────────────────────
    val performScan: suspend (String) -> List<Pair<File, File?>> = { scanPath ->
        val musicDir = File(scanPath)
        if (!musicDir.exists()) emptyList()
        else {
            val allFiles = musicDir.walkTopDown().filter { it.isFile }.toList()
            val audioExtensions = listOf("flac", "mp3", "m4a", "wav", "ogg", "aac")
            val audioFiles = allFiles.filter { it.extension.lowercase() in audioExtensions }
            val ttmlFiles = allFiles.filter { it.name.endsWith(".ttml", ignoreCase = true) }

            Log.d("SpicyPlayer", "Scanning $scanPath: Found ${audioFiles.size} audio, ${ttmlFiles.size} TTML")

            audioFiles.map { audioFile ->
                val baseName = robustNormalize(audioFile.nameWithoutExtension)
                val fuzzyBase = fuzzyNormalize(audioFile.nameWithoutExtension)

                var ttmlFile = allFiles.find {
                    it.name.endsWith(".ttml", ignoreCase = true) &&
                        robustNormalize(it.nameWithoutExtension) == baseName
                } ?: allFiles.find {
                    it.name.endsWith(".ttml", ignoreCase = true) &&
                        fuzzyNormalize(it.nameWithoutExtension) == fuzzyBase
                }

                if (ttmlFile == null && fuzzyBase.length >= 3) {
                    ttmlFile = ttmlFiles.find {
                        fuzzyNormalize(it.nameWithoutExtension).contains(fuzzyBase) ||
                            fuzzyBase.contains(fuzzyNormalize(it.nameWithoutExtension))
                    }
                }

                if (ttmlFile == null) {
                    try {
                        val retriever = MediaMetadataRetriever()
                        audioFile.inputStream().use { retriever.setDataSource(it.fd) }
                        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        retriever.release()
                        if (!title.isNullOrBlank()) {
                            val fuzzyTitle = fuzzyNormalize(title)
                            if (fuzzyTitle.length >= 3) {
                                ttmlFile = ttmlFiles.find {
                                    val ft = fuzzyNormalize(it.nameWithoutExtension)
                                    ft == fuzzyTitle || ft.contains(fuzzyTitle) || fuzzyTitle.contains(ft)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (ttmlFile == null) {
                    val latinOnly = fuzzyBase.replace(Regex("[^a-zA-Z0-9]"), "")
                    if (latinOnly.length >= 5) {
                        ttmlFile = ttmlFiles.find {
                            val tl = fuzzyNormalize(it.nameWithoutExtension).replace(Regex("[^a-zA-Z0-9]"), "")
                            tl == latinOnly && tl.isNotEmpty()
                        }
                    }
                }

                if (ttmlFile != null) {
                    Log.d("SpicyPlayer", "Paired: ${audioFile.name} -> ${ttmlFile.name}")
                    audioFile to ttmlFile
                } else {
                    Log.d("SpicyPlayer", "No lyrics for: ${audioFile.name}")
                    audioFile to null
                }
            }.sortedBy { it.first.name.lowercase() }
        }
    }

    val triggerRescan = {
        if (!isScanning) {
            isScanning = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val scanPath = settingsVm.scanDirectory.value.ifBlank { "/sdcard/Music/" }
                    val scannedPairs = performScan(scanPath)
                    launch(Dispatchers.Main) {
                        songPairs = scannedPairs
                        isScanning = false
                        Toast.makeText(context, "Library rescanned: ${scannedPairs.size} songs", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("SpicyPlayer", "Rescan error", e)
                    launch(Dispatchers.Main) { isScanning = false }
                }
            }
        }
    }

    // ── Directory Picker Launcher ──────────────────────────────────────────
    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    
                    val documentId = DocumentsContract.getTreeDocumentId(it)
                    val split = documentId.split(":")
                    val type = split[0]
                    val relativePath = if (split.size > 1) split[1] else ""
                    
                    val path = if ("primary".equals(type, ignoreCase = true)) {
                        "/sdcard/$relativePath"
                    } else {
                        "/storage/$type/$relativePath"
                    }

                    settingsVm.setScanDirectory(path)
                    // Auto rescan when directory changes
                    isScanning = false // reset just in case
                    coroutineScope.launch {
                        delay(500) // wait for datastore
                        triggerRescan()
                    }
                } catch (e: Exception) {
                    Log.e("SpicyPlayer", "Error handling directory picker", e)
                    Toast.makeText(context, "Failed to set directory: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    // ── Initial scan ──────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        if (songPairs.isEmpty() && !isScanning) {
            isScanning = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val scanPath = settingsVm.scanDirectory.value.ifBlank { "/sdcard/Music/" }
                    val scannedPairs = performScan(scanPath)
                    launch(Dispatchers.Main) {
                        songPairs = scannedPairs
                        isScanning = false
                    }
                } catch (e: Exception) {
                    Log.e("SpicyPlayer", "Initial scan error", e)
                    launch(Dispatchers.Main) { isScanning = false }
                }
            }
        }
    }

    // ── Song loading helpers ──────────────────────────────────────────────
    val loadSongFromPair = { pair: Pair<File, File?>, indexInSongPairs: Int, playWait: Boolean ->
        val (selectedAudio, selectedTtml) = pair
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.d("SpicyPlayer", "Loading: ${selectedAudio.name}, playWait=$playWait")

                val parsedLyrics = try {
                    if (selectedTtml != null) {
                        selectedTtml.inputStream().use { TtmlLyricsParser.parse(it) }
                    } else null
                } catch (e: Exception) {
                    Log.e("SpicyPlayer", "TTML parse failed", e); null
                }

                val result = try {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(selectedAudio.absolutePath)
                        val t = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            ?: selectedAudio.nameWithoutExtension
                        val a = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            ?: "Unknown Artist"
                        val br = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                            ?.let { (it.toInt() / 1000).toString() + " kbps" } ?: ""
                        val ext = selectedAudio.extension.uppercase()
                        
                        val artBytes = retriever.embeddedPicture
                        val bmp = artBytes?.let {
                            val raw = BitmapFactory.decodeByteArray(it, 0, it.size)
                            if (raw.width > 1024 || raw.height > 1024)
                                Bitmap.createScaledBitmap(raw, 1024, 1024, true) else raw
                        }
                        
                        NowPlayingMetadata(t, a, bmp, br, ext)
                    } finally {
                        retriever.release()
                    }
                } catch (e: Exception) {
                    Log.e("SpicyPlayer", "Metadata error", e)
                    NowPlayingMetadata(selectedAudio.nameWithoutExtension, "Unknown Artist", null, "", selectedAudio.extension.uppercase())
                }

                launch(Dispatchers.Main) {
                    lines = parsedLyrics?.lines ?: emptyList()
                    trackName = result.t
                    artistName = result.a
                    coverArtBitmap = result.art
                    currentBitrate = result.bitrate
                    currentFormat = result.ext
                    currentSongIndex = indexInSongPairs
                    audioPlayer.preparePlaylist(listOf(selectedAudio.absolutePath), playWhenReady = playWait)
                    isPlaying = playWait
                }
            } catch (e: Exception) {
                Log.e("SpicyPlayer", "Load error", e)
            }
        }
    }

    val loadSong = { index: Int, playWait: Boolean ->
        if (index in songPairs.indices) {
            loadSongFromPair(songPairs[index], index, playWait)
        }
    }

    // ── Queue helpers ─────────────────────────────────────────────────────
    val loadNext = {
        Log.d("SpicyPlayer", "loadNext: current=$currentSongIndex, loopMode=$loopMode")
        when {
            loopMode == 2 -> {
                // Loop one: replay current
                audioPlayer.player.seekTo(0)
                audioPlayer.player.play()
                isPlaying = true
            }
            playQueue.isNotEmpty() -> {
                val next = if (loopMode == 1)
                    (playQueueIndex + 1) % playQueue.size
                else
                    (playQueueIndex + 1)
                
                if (next < playQueue.size) {
                    playQueueIndex = next
                    loadSongFromPair(playQueue[next], songPairs.indexOf(playQueue[next]), true)
                } else {
                    // End of queue
                    isPlaying = false
                }
            }
            songPairs.isNotEmpty() -> {
                val next = (currentSongIndex + 1)
                if (next < songPairs.size) {
                    loadSong(next, true)
                } else if (loopMode == 1) {
                    loadSong(0, true)
                } else {
                    // End of playlist
                    isPlaying = false
                }
            }
        }
    }

    val loadPrevious = {
        Log.d("SpicyPlayer", "loadPrevious: current=$currentSongIndex")
        when {
            playQueue.isNotEmpty() -> {
                val prev = (playQueueIndex - 1 + playQueue.size) % playQueue.size
                playQueueIndex = prev
                loadSongFromPair(playQueue[prev], songPairs.indexOf(playQueue[prev]), true)
            }
            songPairs.isNotEmpty() -> {
                val prev = (currentSongIndex - 1 + songPairs.size) % songPairs.size
                loadSong(prev, true)
            }
        }
    }

    /** Shuffle: if no queue → shuffle all songs into queue and play first.
     *  If queue exists → reshuffle the queue and play from new first. */
    val handleShuffle = {
        val shuffled = if (playQueue.isEmpty()) songPairs.shuffled() else playQueue.shuffled()
        if (shuffled.isNotEmpty()) {
            playQueue = shuffled
            playQueueIndex = 0
            loadSongFromPair(shuffled[0], songPairs.indexOf(shuffled[0]), true)
        }
    }

    // ── Auto-advance on song end ──────────────────────────────────────────
    // REMOVED startup auto-load as requested
    
    val currentLoadNext by rememberUpdatedState(loadNext)

    DisposableEffect(audioPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val played = System.currentTimeMillis() - lastSongStartTime
                    if (played < 2000) {
                        Log.e("SpicyPlayer", "Rapid skip (${played}ms). Stopping.")
                        isPlaying = false
                    } else {
                        currentLoadNext()
                    }
                } else if (playbackState == Player.STATE_READY) {
                    lastSongStartTime = System.currentTimeMillis()
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("SpicyPlayer", "Player error: ${error.message}", error)
                isPlaying = false
            }
        }
        audioPlayer.player.addListener(listener)
        onDispose { audioPlayer.player.removeListener(listener) }
    }

    // ── Playback time loop ────────────────────────────────────────────────
    LaunchedEffect(isPlaying, isDraggingSlider) {
        if (isPlaying && !isDraggingSlider) {
            currentTimeMs = audioPlayer.player.currentPosition
            var lastTime = System.currentTimeMillis()
            while (isPlaying && !isDraggingSlider) {
                val now = System.currentTimeMillis()
                val dt = now - lastTime
                lastTime = now

                // Update total duration
                val dur = audioPlayer.player.duration
                if (dur > 0) currentDurationMs = dur

                val actualPos = audioPlayer.player.currentPosition
                val diff = actualPos - currentTimeMs
                if (kotlin.math.abs(diff) > 500) {
                    currentTimeMs = actualPos
                } else {
                    val correction = diff * 0.1f
                    currentTimeMs += dt + correction.toLong()
                }
                delay(16)
            }
        }
    }

    // ── Formatting helper ────────────────────────────────────────────────
    fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    // ── colours ───────────────────────────────────────────────────────────
    val onSurface = Color.White
    val primaryTint = colorScheme.primary
    val disabledTint = onSurface.copy(alpha = 0.38f)

    // ── UI ────────────────────────────────────────────────────────────────
    MaterialTheme(colorScheme = colorScheme) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                val duration = 400
                val enterSlide = when {
                    initialState == AppScreen.NOW_PLAYING && targetState == AppScreen.SETTINGS -> slideInHorizontally { it }
                    initialState == AppScreen.SETTINGS && targetState == AppScreen.EQUALIZER -> slideInHorizontally { it }
                    initialState == AppScreen.EQUALIZER && targetState == AppScreen.SETTINGS -> slideInHorizontally { -it / 3 }
                    initialState == AppScreen.SETTINGS && targetState == AppScreen.NOW_PLAYING -> slideInHorizontally { -it / 3 }
                    initialState == AppScreen.NOW_PLAYING && targetState == AppScreen.EQUALIZER -> slideInHorizontally { it }
                    initialState == AppScreen.EQUALIZER && targetState == AppScreen.NOW_PLAYING -> slideInHorizontally { -it / 3 }
                    else -> fadeIn()
                }
                val exitSlide = when {
                    initialState == AppScreen.NOW_PLAYING && targetState == AppScreen.SETTINGS -> slideOutHorizontally { -it / 3 }
                    initialState == AppScreen.SETTINGS && targetState == AppScreen.EQUALIZER -> slideOutHorizontally { -it / 3 }
                    initialState == AppScreen.EQUALIZER && targetState == AppScreen.SETTINGS -> slideOutHorizontally { it }
                    initialState == AppScreen.SETTINGS && targetState == AppScreen.NOW_PLAYING -> slideOutHorizontally { it }
                    initialState == AppScreen.NOW_PLAYING && targetState == AppScreen.EQUALIZER -> slideOutHorizontally { -it / 3 }
                    initialState == AppScreen.EQUALIZER && targetState == AppScreen.NOW_PLAYING -> slideOutHorizontally { it }
                    else -> fadeOut()
                }

                (enterSlide + fadeIn(tween(duration))).togetherWith(
                    exitSlide + fadeOut(tween(duration))
                ).apply {
                    targetContentZIndex = if (targetState.ordinal > initialState.ordinal) 1f else -1f
                }
            },
            modifier = Modifier.background(colorScheme.background),
            contentAlignment = Alignment.Center,
            label = "app_nav"
        ) { screen ->
            when (screen) {
                AppScreen.SETTINGS -> {
                    // ── Settings screen ──────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorScheme.background)
                    ) {
                        DynamicBackgroundView(
                            coverArtBitmap = coverArtBitmap ?: appLogo,
                            blurIntensity = blurIntensity
                        )
                        SettingsScreen(
                            onBack = { currentScreen = AppScreen.NOW_PLAYING },
                            onRescan = { triggerRescan() },
                            onNavigateToEqualizer = {
                                eqNavigationSource = AppScreen.SETTINGS
                                currentScreen = AppScreen.EQUALIZER
                            },
                            onDirectoryClick = {
                                // Hint to start at the root of primary storage to ensure internal storage is visible
                                val rootUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A")
                                directoryPickerLauncher.launch(rootUri)
                            },
                            isScanning = isScanning
                        )
                    }
                }
                AppScreen.EQUALIZER -> {
                    // ── Equalizer screen ──────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorScheme.background)
                    ) {
                        DynamicBackgroundView(
                            coverArtBitmap = coverArtBitmap ?: appLogo,
                            blurIntensity = blurIntensity
                        )
                        EqualizerScreen(
                            currentPreset = eqPreset,
                            onPresetChange = { settingsVm.setEqPreset(it) },
                            bassBoostEnabled = bassBoostEnabled,
                            onBassBoostEnabledChange = { settingsVm.setBassBoost(it) },
                            bassBoostStrength = bassBoostStrength,
                            onBassBoostStrengthChange = { settingsVm.setBassBoostStrength(it) },
                            onBack = { currentScreen = eqNavigationSource }
                        )
                    }
                }
                AppScreen.NOW_PLAYING -> {
                    // ── Now Playing ──────────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorScheme.background)
                    ) {
                    DynamicBackgroundView(
                        coverArtBitmap = coverArtBitmap ?: appLogo,
                        blurIntensity = blurIntensity
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                    ) {
                        // ── Header ──────────────────────────────────────────────
                        NowPlayingHeader(
                            nowPlayingData = nowPlayingData,
                            headerProgress = headerProgress,
                            currentImageSize = currentImageSize,
                            currentSpacerWidth = currentSpacerWidth,
                            currentHeaderBias = currentHeaderBias,
                            metadataAlpha = metadataAlpha
                        )

                        // ── Lyrics ──────────────────────────────────────────────
                        Box(modifier = Modifier.weight(1f)) {
                            if (currentSongIndex != -1) {
                                SpicyLyricsView(
                                    lines = lines,
                                    currentTimeMs = currentTimeMs + lyricsOffsetMs,
                                    onSeekWord = { timeMs ->
                                        audioPlayer.player.seekTo(timeMs)
                                        currentTimeMs = timeMs
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    fontSizeScale = lyricsFontScale
                                )
                            }
                        }

                        // ── Floating Bottom Controls ──────────────────────
                        NowPlayingControls(
                            controlsCollapsed = controlsCollapsed,
                            onToggleCollapse = { controlsCollapsed = !controlsCollapsed },
                            currentTimeMs = currentTimeMs,
                            currentDurationMs = currentDurationMs,
                            onSeek = { currentTimeMs = it },
                            onSeekFinished = { audioPlayer.player.seekTo(currentTimeMs) },
                            isDraggingSlider = isDraggingSlider,
                            onDraggingChanged = { isDraggingSlider = it },
                            currentBitrate = currentBitrate,
                            currentFormat = currentFormat,
                            primaryTint = primaryTint,
                            onShowMenu = { showMenu = true },
                            onShuffle = { handleShuffle() },
                            isShuffleActive = playQueue.isNotEmpty(),
                            onPrevious = { loadPrevious() },
                            onNext = { loadNext() },
                            isPlaying = isPlaying,
                            onTogglePlay = {
                                if (audioPlayer.player.isPlaying) {
                                    audioPlayer.player.pause()
                                    isPlaying = false
                                } else {
                                    audioPlayer.player.play()
                                    isPlaying = true
                                }
                            },
                            loopMode = loopMode,
                            onToggleLoop = { loopMode = (loopMode + 1) % 3 },
                            colorScheme = colorScheme,
                            formatTime = { formatTime(it) },
                            isExpressive = controlsStyle == "EXPRESSIVE"
                        )

                    }
                } // end AppScreen.NOW_PLAYING Box
            } // end when
        } // end AnimatedContent

        // ── Custom 3-Dots Menu (Scored for Immersive mode) ──
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrim (Fade Animation)
            AnimatedVisibility(
                visible = showMenu,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { showMenu = false }
                )
            }

            // Sheet (Slide Animation)
            AnimatedVisibility(
                visible = showMenu,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false) {}, // prevent click-through
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = colorScheme.surfaceVariant,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                            .navigationBarsPadding()
                    ) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                .align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "More Options",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp, start = 8.dp),
                            color = colorScheme.onSurfaceVariant
                        )

                        ListItem(
                            headlineContent = { Text("Add to Playlist") },
                            leadingContent = { Icon(Icons.Rounded.FavoriteBorder, contentDescription = null) },
                            modifier = Modifier.clickable { showMenu = false },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        ListItem(
                            headlineContent = { Text("Sleep Timer") },
                            leadingContent = { Icon(Icons.Rounded.Timer, contentDescription = null) },
                            modifier = Modifier.clickable { showMenu = false },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        ListItem(
                            headlineContent = { Text("Equalizer") },
                            leadingContent = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                            modifier = Modifier.clickable {
                                showMenu = false
                                eqNavigationSource = AppScreen.NOW_PLAYING
                                currentScreen = AppScreen.EQUALIZER
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        ListItem(
                            headlineContent = { Text("Settings") },
                            leadingContent = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            modifier = Modifier.clickable {
                                showMenu = false
                                currentScreen = AppScreen.SETTINGS
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
}
