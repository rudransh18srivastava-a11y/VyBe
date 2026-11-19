package com.vybe.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer

class PlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var prefs: Prefs
    private val inPip = mutableStateOf(false)
    private var autoRotated = false

    private val subtitlePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) addSubtitle(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val (items, startIndex) = parseIntent() ?: run {
            finish()
            return
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.also {
                it.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(
                DefaultRenderersFactory(this)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                    .setEnableDecoderFallback(true)
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                // Landscape videos open in landscape automatically (once).
                if (!autoRotated && videoSize.width > videoSize.height) {
                    autoRotated = true
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                autoRotated = false
            }
        })

        val resume = prefs.getPosition(items[startIndex].mediaId)
        player.setMediaItems(items, startIndex, if (resume > 0) resume else C.TIME_UNSET)
        player.prepare()
        player.playWhenReady = true

        setContent {
            VyBeTheme {
                PlayerScreen(
                    player = player,
                    inPip = inPip.value,
                    getBrightness = ::getBrightness,
                    setBrightness = ::setBrightness,
                    onBack = { finish() },
                    onPip = ::enterPip,
                    onRotate = ::toggleOrientation,
                    onPickSubtitle = { subtitlePicker.launch(arrayOf("*/*")) }
                )
            }
        }
    }

    // ---- intent ---------------------------------------------------------------------------

    private fun parseIntent(): Pair<List<MediaItem>, Int>? {
        val data = intent?.data
        if (intent?.action == Intent.ACTION_VIEW && data != null) {
            val title = queryDisplayName(this, data)?.substringBeforeLast('.')
                ?: data.lastPathSegment
                ?: "Video"
            return listOf(buildItem(data.toString(), title)) to 0
        }
        val uris = intent?.getStringArrayListExtra(EX_URIS) ?: return null
        val titles = intent?.getStringArrayListExtra(EX_TITLES) ?: arrayListOf()
        if (uris.isEmpty()) return null
        val index = intent.getIntExtra(EX_INDEX, 0).coerceIn(0, uris.lastIndex)
        return uris.mapIndexed { i, u -> buildItem(u, titles.getOrNull(i) ?: "Video") } to index
    }

    private fun buildItem(uri: String, title: String): MediaItem =
        MediaItem.Builder()
            .setUri(Uri.parse(uri))
            .setMediaId(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()

    // ---- lifecycle ------------------------------------------------------------------------

    private fun savePosition() {
        if (!::player.isInitialized) return
        val item = player.currentMediaItem ?: return
        val duration = player.duration
        val pos = player.currentPosition
        if (duration != C.TIME_UNSET && pos > 5_000 && pos < duration - 5_000) {
            prefs.setPosition(item.mediaId, pos)
        } else {
            prefs.clearPosition(item.mediaId)
        }
    }

    override fun onPause() {
        super.onPause()
        savePosition()
        if (::player.isInitialized && !isInPictureInPictureMode) player.pause()
    }

    override fun onStop() {
        super.onStop()
        savePosition()
        // PiP window dismissed or screen turned off: stop playback.
        if (::player.isInitialized && inPip.value) player.pause()
    }

    override fun onDestroy() {
        if (::player.isInitialized) {
            savePosition()
            player.release()
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // ---- picture-in-picture ---------------------------------------------------------------

    private fun enterPip() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(this, "Picture-in-picture isn't supported on this device", Toast.LENGTH_SHORT).show()
            return
        }
        val size = player.videoSize
        val ratio = if (size.width > 0 && size.height > 0) {
            (size.width.toFloat() / size.height).coerceIn(0.42f, 2.38f)
        } else {
            16f / 9f
        }
        try {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
                    .build()
            )
        } catch (_: IllegalStateException) {
            Toast.makeText(this, "Enable picture-in-picture for VyBe in system settings", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (::player.isInitialized && player.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    // ---- helpers used by the UI -----------------------------------------------------------

    private fun toggleOrientation() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        autoRotated = true
    }

    private fun getBrightness(): Float {
        val b = window.attributes.screenBrightness
        if (b >= 0f) return b
        return try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (_: Exception) {
            0.5f
        }
    }

    private fun setBrightness(value: Float) {
        window.attributes = window.attributes.also { it.screenBrightness = value }
    }

    private fun addSubtitle(uri: Uri) {
        val item = player.currentMediaItem ?: return
        val name = queryDisplayName(this, uri) ?: uri.lastPathSegment ?: "Subtitle"
        val lower = name.lowercase()
        val mime = when {
            lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            lower.endsWith(".ttml") || lower.endsWith(".xml") || lower.endsWith(".dfxp") ->
                MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val subtitle = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mime)
            .setLanguage("und")
            .setLabel(name)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        val wasPlaying = player.playWhenReady
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        player.replaceMediaItem(index, item.buildUpon().setSubtitleConfigurations(listOf(subtitle)).build())
        player.seekTo(index, position)
        player.playWhenReady = wasPlaying
        Toast.makeText(this, "Subtitle loaded: $name", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val EX_URIS = "vybe.uris"
        private const val EX_TITLES = "vybe.titles"
        private const val EX_INDEX = "vybe.index"

        /** Starts the player with a playlist. Only a window of 400 items around [index] is sent. */
        fun launch(context: Context, uris: List<String>, titles: List<String>, index: Int) {
            val start = (index - 200).coerceAtLeast(0)
            val end = minOf(uris.size, start + 400)
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putStringArrayListExtra(EX_URIS, ArrayList(uris.subList(start, end)))
                    .putStringArrayListExtra(EX_TITLES, ArrayList(titles.subList(start, end)))
                    .putExtra(EX_INDEX, index - start)
            )
        }
    }
}
