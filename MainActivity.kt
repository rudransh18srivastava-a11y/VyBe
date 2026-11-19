package com.vybe.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VyBeTheme { VyBeRoot() } }
    }
}

@Composable
private fun VyBeRoot() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(ctx) }

    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
        )
    }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var resumeTick by remember { mutableIntStateOf(0) }

    fun reload() {
        if (loading) return
        scope.launch {
            loading = true
            videos = withContext(Dispatchers.IO) { VideoRepository.load(ctx) }
            loading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = queryDisplayName(ctx, uri) ?: "Video"
            PlayerActivity.launch(ctx, listOf(uri.toString()), listOf(name), 0)
        }
    }

    LaunchedEffect(granted) { if (granted) reload() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        resumeTick++ // refresh the "resume" progress bars after coming back from the player
        val stillGranted =
            ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
        if (stillGranted != granted) granted = stillGranted
        else if (granted && videos.isNotEmpty()) reload()
    }

    LibraryScreen(
        videos = videos,
        loading = loading,
        granted = granted,
        resumeTick = resumeTick,
        prefs = prefs,
        onRequestPermission = { permissionLauncher.launch(permission) },
        onPlay = { list, index ->
            PlayerActivity.launch(
                ctx,
                list.map { it.uri.toString() },
                list.map { it.title },
                index
            )
        },
        onOpenFile = { filePicker.launch(arrayOf("video/*", "application/x-mpegURL")) },
        onPlayUrl = { url ->
            val title = Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() } ?: url
            PlayerActivity.launch(ctx, listOf(url), listOf(title), 0)
        },
        onRefresh = { reload() }
    )
}

fun queryDisplayName(ctx: android.content.Context, uri: Uri): String? = try {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
} catch (_: Exception) {
    null
}
