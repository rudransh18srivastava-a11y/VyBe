package com.vybe.player

import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Hud(val icon: ImageVector, val text: String, val progress: Float? = null)

private enum class DialogKind { SPEED, AUDIO, SUBTITLE }

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)

private val RESIZE_MODES = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Crop to fill",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch"
)

@Composable
fun PlayerScreen(
    player: ExoPlayer,
    inPip: Boolean,
    getBrightness: () -> Float,
    setBrightness: (Float) -> Unit,
    onBack: () -> Unit,
    onPip: () -> Unit,
    onRotate: () -> Unit,
    onPickSubtitle: () -> Unit
) {
    val ctx = LocalContext.current
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var title by remember { mutableStateOf(player.mediaMetadata.title?.toString().orEmpty()) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var tracks by remember { mutableStateOf(player.currentTracks) }
    var speed by remember { mutableFloatStateOf(player.playbackParameters.speed) }
    var hasNext by remember { mutableStateOf(player.hasNextMediaItem()) }
    var hasPrev by remember { mutableStateOf(player.hasPreviousMediaItem()) }

    var resizeIndex by remember { mutableIntStateOf(0) }
    var controls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var touchTick by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableFloatStateOf(0f) }
    var dialog by remember { mutableStateOf<DialogKind?>(null) }
    var hud by remember { mutableStateOf<Hud?>(null) }
    var hudTick by remember { mutableIntStateOf(0) }

    fun poke() { touchTick++ }
    fun showHud(h: Hud) { hud = h; hudTick++ }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying_: Boolean) { isPlaying = isPlaying_ }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                hasNext = player.hasNextMediaItem()
                hasPrev = player.hasPreviousMediaItem()
                if (state == Player.STATE_ENDED && !player.hasNextMediaItem()) controls = true
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                title = mediaItem?.mediaMetadata?.title?.toString().orEmpty()
                hasNext = player.hasNextMediaItem()
                hasPrev = player.hasPreviousMediaItem()
            }

            override fun onTracksChanged(t: Tracks) { tracks = t }

            override fun onPlaybackParametersChanged(p: PlaybackParameters) { speed = p.speed }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Keep the seek bar fresh while controls are on screen.
    LaunchedEffect(controls, inPip) {
        while (controls && !inPip) {
            position = player.currentPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            delay(250)
        }
    }

    // Auto-hide controls after a few seconds of playback.
    LaunchedEffect(controls, isPlaying, scrubbing, dialog, touchTick) {
        if (controls && isPlaying && !scrubbing && dialog == null) {
            delay(3500)
            controls = false
        }
    }

    // Fade the gesture HUD.
    LaunchedEffect(hudTick) {
        if (hud != null) {
            delay(700)
            hud = null
        }
    }

    BackHandler(enabled = locked) { controls = true }

    fun togglePlay() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        if (player.isPlaying) player.pause() else player.play()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // ---- video surface ----
        val exoPlayer = player
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    this.player = exoPlayer
                }
            },
            update = { it.resizeMode = RESIZE_MODES[resizeIndex].first },
            modifier = Modifier.fillMaxSize()
        )

        // ---- gesture layer ----
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = { controls = !controls },
                        onDoubleTap = { offset ->
                            if (!locked) {
                                val third = size.width / 3f
                                when {
                                    offset.x < third -> {
                                        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                                        showHud(Hud(Icons.Rounded.FastRewind, "-10s"))
                                    }
                                    offset.x > third * 2 -> {
                                        val d = player.duration
                                        val target = player.currentPosition + 10_000
                                        player.seekTo(if (d > 0) target.coerceAtMost(d) else target)
                                        showHud(Hud(Icons.Rounded.FastForward, "+10s"))
                                    }
                                    else -> togglePlay()
                                }
                            }
                        },
                        onPress = {
                            if (!locked) {
                                // Press and hold = temporary 2x speed.
                                var boosted = false
                                val previous = player.playbackParameters.speed
                                coroutineScope {
                                    val job = launch {
                                        delay(500)
                                        if (player.isPlaying) {
                                            boosted = true
                                            player.setPlaybackSpeed(2f)
                                            showHud(Hud(Icons.Rounded.FastForward, "2× speed"))
                                        }
                                    }
                                    tryAwaitRelease()
                                    job.cancel()
                                }
                                if (boosted) player.setPlaybackSpeed(previous)
                            }
                        }
                    )
                }
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    var mode = 0 // 0 undecided, 1 seek, 2 brightness, 3 volume
                    var total = Offset.Zero
                    var startX = 0f
                    var seekStart = 0L
                    var seekTarget = 0L
                    var bright = 0f
                    var vol = 0f
                    val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    detectDragGestures(
                        onDragStart = { o ->
                            mode = 0
                            total = Offset.Zero
                            startX = o.x
                            seekStart = player.currentPosition
                            seekTarget = seekStart
                            bright = getBrightness()
                            vol = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            total += amount
                            if (mode == 0 && (abs(total.x) > 30f || abs(total.y) > 30f)) {
                                mode = when {
                                    abs(total.x) > abs(total.y) -> 1
                                    startX < size.width / 2f -> 2
                                    else -> 3
                                }
                            }
                            when (mode) {
                                1 -> {
                                    val d = player.duration
                                    if (d > 0) {
                                        seekTarget = (seekStart + total.x / size.width * 90_000L)
                                            .toLong().coerceIn(0L, d)
                                        val delta = seekTarget - seekStart
                                        val sign = if (delta >= 0) "+" else "-"
                                        showHud(
                                            Hud(
                                                if (delta >= 0) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                                                "$sign${formatTime(abs(delta))}  (${formatTime(seekTarget)})"
                                            )
                                        )
                                    }
                                }
                                2 -> {
                                    bright = (bright - amount.y / size.height * 1.3f).coerceIn(0.01f, 1f)
                                    setBrightness(bright)
                                    showHud(Hud(Icons.Rounded.BrightnessMedium, "${(bright * 100).roundToInt()}%", bright))
                                }
                                3 -> {
                                    vol = (vol - amount.y / size.height * 1.3f).coerceIn(0f, 1f)
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (vol * maxVol).roundToInt(), 0)
                                    showHud(Hud(Icons.Rounded.VolumeUp, "${(vol * 100).roundToInt()}%", vol))
                                }
                            }
                        },
                        onDragEnd = {
                            if (mode == 1) player.seekTo(seekTarget)
                            mode = 0
                            hudTick++
                        },
                        onDragCancel = { mode = 0 }
                    )
                }
        )

        if (!inPip) {
            // ---- buffering spinner ----
            if (buffering) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(48.dp), color = VyPink)
            }

            // ---- gesture HUD ----
            hud?.let { h ->
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(h.icon, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(h.text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    if (h.progress != null) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { h.progress },
                            color = VyPink,
                            trackColor = Color(0x44FFFFFF),
                            modifier = Modifier.width(120.dp).height(4.dp).clip(CircleShape)
                        )
                    }
                }
            }

            // ---- locked: only an unlock button ----
            AnimatedVisibility(
                visible = locked && controls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                IconButton(
                    onClick = { locked = false; poke() },
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(24.dp)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0x99000000))
                ) { Icon(Icons.Rounded.LockOpen, "Unlock controls", tint = Color.White) }
            }

            // ---- full controls ----
            AnimatedVisibility(
                visible = controls && !locked,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x73000000))
                ) {
                    // top bar
                    Row(
                        Modifier
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.displayCutout)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White) }
                        Text(
                            title,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        )
                        IconButton(onClick = onPip) {
                            Icon(Icons.Rounded.PictureInPictureAlt, "Picture in picture", tint = Color.White)
                        }
                    }

                    // centre transport
                    Row(
                        Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        IconButton(
                            onClick = { player.seekToPreviousMediaItem(); poke() },
                            enabled = hasPrev
                        ) {
                            Icon(
                                Icons.Rounded.SkipPrevious, "Previous",
                                tint = if (hasPrev) Color.White else Color(0x55FFFFFF),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        IconButton(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); poke() }) {
                            Icon(Icons.Rounded.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        IconButton(
                            onClick = { togglePlay(); poke() },
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(VyGradient)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        IconButton(onClick = {
                            val d = player.duration
                            val t = player.currentPosition + 10_000
                            player.seekTo(if (d > 0) t.coerceAtMost(d) else t)
                            poke()
                        }) {
                            Icon(Icons.Rounded.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        IconButton(
                            onClick = { player.seekToNextMediaItem(); poke() },
                            enabled = hasNext
                        ) {
                            Icon(
                                Icons.Rounded.SkipNext, "Next",
                                tint = if (hasNext) Color.White else Color(0x55FFFFFF),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // bottom: seek bar + actions
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.displayCutout)
                            .navigationBarsPadding()
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatTime(if (scrubbing) scrubPos.toLong() else position),
                                color = Color.White, fontSize = 12.sp
                            )
                            Slider(
                                value = (if (scrubbing) scrubPos else position.toFloat())
                                    .coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
                                onValueChange = {
                                    scrubbing = true
                                    scrubPos = it
                                    poke()
                                },
                                onValueChangeFinished = {
                                    player.seekTo(scrubPos.toLong())
                                    position = scrubPos.toLong()
                                    scrubbing = false
                                },
                                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = VyPink,
                                    activeTrackColor = VyPurple,
                                    inactiveTrackColor = Color(0x55FFFFFF)
                                ),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            Text(formatTime(duration), color = Color.White, fontSize = 12.sp)
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ControlAction(Icons.Rounded.Lock, "Lock") { locked = true; controls = true; poke() }
                            ControlAction(Icons.Rounded.Speed, speedLabel(speed)) { dialog = DialogKind.SPEED }
                            ControlAction(Icons.Rounded.AspectRatio, RESIZE_MODES[resizeIndex].second) {
                                resizeIndex = (resizeIndex + 1) % RESIZE_MODES.size
                                showHud(Hud(Icons.Rounded.AspectRatio, RESIZE_MODES[resizeIndex].second))
                                poke()
                            }
                            ControlAction(Icons.Rounded.Audiotrack, "Audio") { dialog = DialogKind.AUDIO }
                            ControlAction(Icons.Rounded.Subtitles, "Subtitles") { dialog = DialogKind.SUBTITLE }
                            ControlAction(Icons.Rounded.NoteAdd, "Add sub") { onPickSubtitle() }
                            ControlAction(Icons.Rounded.ScreenRotation, "Rotate") { onRotate(); poke() }
                        }
                    }
                }
            }
        }
    }

    // ---- dialogs ----
    when (dialog) {
        DialogKind.SPEED -> OptionDialog(
            title = "Playback speed",
            options = SPEEDS.map { speedLabel(it) },
            selected = SPEEDS.indexOfFirst { abs(it - speed) < 0.01f },
            onSelect = { player.setPlaybackSpeed(SPEEDS[it]); dialog = null },
            onDismiss = { dialog = null }
        )
        DialogKind.AUDIO -> {
            val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
            OptionDialog(
                title = "Audio track",
                options = groups.mapIndexed { i, g -> trackLabel(g.getTrackFormat(0), i, "Track", audio = true) },
                selected = groups.indexOfFirst { it.isSelected },
                emptyText = "No audio tracks",
                onSelect = { selectTrack(player, C.TRACK_TYPE_AUDIO, groups[it]); dialog = null },
                onDismiss = { dialog = null }
            )
        }
        DialogKind.SUBTITLE -> {
            val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
            val textDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
            val sel = if (textDisabled) 0 else groups.indexOfFirst { it.isSelected } + 1
            OptionDialog(
                title = "Subtitles",
                options = listOf("Off") + groups.mapIndexed { i, g -> trackLabel(g.getTrackFormat(0), i, "Subtitle", audio = false) },
                selected = sel,
                onSelect = {
                    selectTrack(player, C.TRACK_TYPE_TEXT, if (it == 0) null else groups[it - 1])
                    dialog = null
                },
                onDismiss = { dialog = null }
            )
        }
        null -> Unit
    }
}

@Composable
private fun ControlAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun OptionDialog(
    title: String,
    options: List<String>,
    selected: Int,
    emptyText: String = "Nothing available",
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VySurface,
        title = { Text(title) },
        text = {
            if (options.isEmpty()) {
                Text(emptyText, color = VyMuted)
            } else {
                LazyColumn {
                    itemsIndexed(options) { i, label ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(i) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = i == selected, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun speedLabel(s: Float): String =
    if (s == s.toInt().toFloat()) "${s.toInt()}.0×" else "${"%.2f".format(s).trimEnd('0')}×"

private fun trackLabel(f: Format, index: Int, prefix: String, audio: Boolean): String {
    val lang = f.language?.takeIf { it != "und" }
        ?.let { Locale.forLanguageTag(it).displayLanguage.takeIf { n -> n.isNotBlank() } }
    val parts = mutableListOf<String>()
    f.label?.takeIf { it.isNotBlank() }?.let { parts += it }
    if (lang != null && parts.none { it.equals(lang, true) }) parts += lang
    if (parts.isEmpty()) parts += "$prefix ${index + 1}"
    if (audio && f.channelCount > 0) {
        parts += when (f.channelCount) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> "${f.channelCount}ch"
        }
    }
    return parts.joinToString("  •  ")
}

private fun selectTrack(player: ExoPlayer, type: Int, group: Tracks.Group?) {
    val builder = player.trackSelectionParameters.buildUpon()
    if (group == null) {
        builder.setTrackTypeDisabled(type, true)
    } else {
        builder.setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
    }
    player.trackSelectionParameters = builder.build()
}
