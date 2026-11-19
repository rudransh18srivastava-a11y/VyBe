package com.vybe.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

enum class SortMode(val label: String) {
    NEWEST("Newest first"), NAME("Name (A–Z)"), DURATION("Longest first"), SIZE("Largest first")
}

enum class LibTab(val label: String, val icon: ImageVector) {
    VIDEOS("Videos", Icons.Rounded.VideoLibrary),
    FOLDERS("Folders", Icons.Rounded.Folder),
    STREAM("Stream", Icons.Rounded.Link)
}

@Composable
fun LibraryScreen(
    videos: List<VideoItem>,
    loading: Boolean,
    granted: Boolean,
    resumeTick: Int,
    prefs: Prefs,
    onRequestPermission: () -> Unit,
    onPlay: (List<VideoItem>, Int) -> Unit,
    onOpenFile: () -> Unit,
    onPlayUrl: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(LibTab.VIDEOS) }
    var openFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var sort by remember { mutableStateOf(SortMode.values().getOrElse(prefs.sort) { SortMode.NEWEST }) }
    var grid by remember { mutableStateOf(prefs.grid) }
    var menuOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    BackHandler(enabled = searching || openFolder != null) {
        if (searching) {
            searching = false
            query = ""
        } else {
            openFolder = null
        }
    }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }

    val inFolderView = tab == LibTab.FOLDERS && openFolder != null
    val showVideos = tab == LibTab.VIDEOS || inFolderView

    val shown = remember(videos, tab, openFolder, query, sort) {
        val base = if (tab == LibTab.VIDEOS) videos else videos.filter { it.folder == openFolder }
        val filtered = if (query.isBlank()) base else base.filter { it.title.contains(query, ignoreCase = true) }
        when (sort) {
            SortMode.NEWEST -> filtered.sortedByDescending { it.dateAdded }
            SortMode.NAME -> filtered.sortedBy { it.title.lowercase() }
            SortMode.DURATION -> filtered.sortedByDescending { it.durationMs }
            SortMode.SIZE -> filtered.sortedByDescending { it.sizeBytes }
        }
    }

    Scaffold(
        containerColor = VyBg,
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(60.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (searching) {
                    IconButton(onClick = { searching = false; query = "" }) {
                        Icon(Icons.Rounded.ArrowBack, "Close search")
                    }
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search videos") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focus),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                    }
                } else {
                    if (inFolderView) {
                        IconButton(onClick = { openFolder = null }) {
                            Icon(Icons.Rounded.ArrowBack, "Back")
                        }
                        Text(
                            openFolder.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.width(8.dp))
                        VyBeLogo(Modifier.weight(1f))
                    }
                    if (tab != LibTab.STREAM) {
                        IconButton(onClick = { searching = true }) {
                            Icon(Icons.Rounded.Search, "Search")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Rounded.Sort, "Sort and view")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                SortMode.values().forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label) },
                                        leadingIcon = {
                                            if (mode == sort) Icon(Icons.Rounded.Check, null, tint = VyPink)
                                            else Spacer(Modifier.size(24.dp))
                                        },
                                        onClick = {
                                            sort = mode
                                            prefs.sort = mode.ordinal
                                            menuOpen = false
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (grid) "List view" else "Grid view") },
                                    leadingIcon = {
                                        Icon(if (grid) Icons.Rounded.ViewList else Icons.Rounded.GridView, null)
                                    },
                                    onClick = {
                                        grid = !grid
                                        prefs.grid = grid
                                        menuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Refresh library") },
                                    leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                                    onClick = { menuOpen = false; onRefresh() }
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenFile) {
                        Icon(Icons.Rounded.FolderOpen, "Open a file")
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = VySurface) {
                LibTab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t; openFolder = null },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = VyPink,
                            indicatorColor = VyPurple,
                            unselectedIconColor = VyMuted,
                            unselectedTextColor = VyMuted
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            when {
                tab == LibTab.STREAM -> StreamTab(prefs, onPlayUrl)
                !granted -> PermissionPrompt(onRequestPermission, onOpenFile)
                loading && videos.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = VyPink)
                showVideos -> VideoList(
                    items = shown,
                    grid = grid,
                    resumeTick = resumeTick,
                    prefs = prefs,
                    onPlay = { index -> onPlay(shown, index) }
                )
                else -> FolderList(videos, query, onOpen = { openFolder = it; query = ""; searching = false })
            }
        }
    }
}

@Composable
private fun VideoList(
    items: List<VideoItem>,
    grid: Boolean,
    resumeTick: Int,
    prefs: Prefs,
    onPlay: (Int) -> Unit
) {
    if (items.isEmpty()) {
        EmptyState("No videos found")
        return
    }
    if (grid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            gridItemsIndexed(items, key = { _, v -> v.id }) { index, v ->
                VideoCard(v, rememberProgress(v, prefs, resumeTick)) { onPlay(index) }
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(items, key = { _, v -> v.id }) { index, v ->
                VideoRow(v, rememberProgress(v, prefs, resumeTick)) { onPlay(index) }
            }
        }
    }
}

@Composable
private fun rememberProgress(v: VideoItem, prefs: Prefs, tick: Int): Float =
    remember(v.id, tick) {
        val p = prefs.getPosition(v.uri.toString())
        if (p > 0 && v.durationMs > 0) (p.toFloat() / v.durationMs).coerceIn(0f, 1f) else 0f
    }

@Composable
private fun Thumbnail(v: VideoItem, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(v.uri).crossfade(true).build(),
        contentDescription = v.title,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@Composable
private fun ThumbBox(v: VideoItem, progress: Float, modifier: Modifier) {
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .background(VySurfaceHi)
    ) {
        Thumbnail(v, Modifier.fillMaxSize())
        Text(
            formatTime(v.durationMs),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xB3000000))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        )
        if (progress > 0.01f) {
            LinearProgressIndicator(
                progress = { progress },
                color = VyPink,
                trackColor = Color(0x66000000),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }
    }
}

@Composable
private fun VideoRow(v: VideoItem, progress: Float, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThumbBox(v, progress, Modifier.width(128.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                v.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                color = VyText
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOf(v.resolution, formatSize(v.sizeBytes)).filter { it.isNotEmpty() }.joinToString("  •  "),
                color = VyMuted,
                fontSize = 12.sp
            )
            Text(v.folder, color = VyMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun VideoCard(v: VideoItem, progress: Float, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        ThumbBox(v, progress, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = VyText)
        Text(formatSize(v.sizeBytes), color = VyMuted, fontSize = 11.sp)
    }
}

@Composable
private fun FolderList(videos: List<VideoItem>, query: String, onOpen: (String) -> Unit) {
    val folders = remember(videos, query) {
        videos.groupBy { it.folder }
            .map { (name, list) -> FolderInfo(name, list.size, list.first().uri) }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
    }
    if (folders.isEmpty()) {
        EmptyState("No folders found")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(folders, key = { _, f -> f.name }) { _, f ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(f.name) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VySurfaceHi)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(f.cover).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(f.name, fontWeight = FontWeight.SemiBold, color = VyText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (f.count == 1) "1 video" else "${f.count} videos",
                        color = VyMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamTab(prefs: Prefs, onPlayUrl: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    val recents = remember { mutableStateListOf<String>().apply { addAll(prefs.recentUrls) } }

    fun play(raw: String) {
        var u = raw.trim()
        if (u.isEmpty()) return
        if (!u.contains("://")) u = "https://$u"
        prefs.addRecentUrl(u)
        recents.clear()
        recents.addAll(prefs.recentUrls)
        onPlayUrl(u)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Network stream", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Paste a direct video link: MP4, MKV, WebM, HLS (.m3u8) or DASH (.mpd).",
            color = VyMuted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("https://example.com/video.mp4") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { play(url) }),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        GradientButton("Play stream", Icons.Rounded.PlayArrow, enabled = url.isNotBlank()) { play(url) }

        if (recents.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { prefs.clearRecentUrls(); recents.clear() }) {
                    Icon(Icons.Rounded.DeleteSweep, "Clear recent", tint = VyMuted)
                }
            }
            LazyColumn {
                itemsIndexed(recents.toList()) { _, r ->
                    Text(
                        r,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = VyText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { play(r) }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit, onOpenFile: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.VideoLibrary, null, tint = VyPurple, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Let VyBe find your videos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "VyBe needs access to your device's videos to build your library. Nothing leaves your phone.",
            color = VyMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        GradientButton("Allow access", Icons.Rounded.Check, onClick = onRequest)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenFile) { Text("Or pick a single file") }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = VyMuted)
    }
}
