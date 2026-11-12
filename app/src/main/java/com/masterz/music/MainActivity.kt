package com.masterz.music

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Ink = Color(0xFF24263A)
private val Paper = Color(0xFFF5F1EA)
private val Coral = Color(0xFFE8795B)
private val Lavender = Color(0xFF9B8CDB)
private val Night = Color(0xFF171925)
private val Panel = Color(0xFF24283A)
private val SoftWhite = Color(0xFFF2F0F7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("masterz", MODE_PRIVATE)
        setContent {
            var darkMode by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
            MasterZApp(
                darkMode = darkMode,
                onThemeChange = {
                    darkMode = it
                    prefs.edit().putBoolean("dark", it).apply()
                }
            )
        }
    }
}

@Composable
private fun MasterZApp(darkMode: Boolean, onThemeChange: (Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var pickedName by remember { mutableStateOf("Belum ada lagu dipilih") }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var currentAudioUrl by remember { mutableStateOf<String?>(null) }
    var currentVideoUrl by remember { mutableStateOf<String?>(null) }
    var currentArtist by remember { mutableStateOf("MasterZ") }
    var currentOnlineTrack by remember { mutableStateOf<OnlineTrack?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("Home") }
    var playerMode by remember { mutableStateOf("Visual") }
    var query by remember { mutableStateOf("") }
    var onlineTracks by remember { mutableStateOf<List<OnlineTrack>>(emptyList()) }
    var trendingTracks by remember { mutableStateOf<List<OnlineTrack>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var loadingTrending by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf<String?>(null) }
    var trendingError by remember { mutableStateOf<String?>(null) }
    var lyrics by remember { mutableStateOf<String?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }
    var lyricsError by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                durationMs = player.duration.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(Unit) {
        loadingTrending = true
        try {
            trendingTracks = OnlineCatalog.trending()
            trendingError = if (trendingTracks.isEmpty()) "Belum ada rekomendasi dari katalog saat ini." else null
        } catch (e: Exception) {
            trendingError = e.message ?: "Rekomendasi gagal dimuat"
        } finally {
            loadingTrending = false
        }
    }

    LaunchedEffect(isPlaying, activeTab) {
        while (isPlaying) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    LaunchedEffect(playerMode, currentOnlineTrack, pickedName) {
        if (playerMode == "Lirik") {
            val track = currentOnlineTrack
            if (track == null) {
                lyrics = null
                lyricsError = "Lirik online hanya dicari untuk lagu dari katalog online."
            } else {
                lyricsLoading = true
                lyricsError = null
                try {
                    lyrics = LyricsClient.fetch(track.artist, track.title)
                } catch (e: Exception) {
                    lyrics = null
                    lyricsError = e.message ?: "Lirik tidak ditemukan"
                } finally {
                    lyricsLoading = false
                }
            }
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            pickedUri = uri
            currentOnlineTrack = null
            currentVideoUrl = null
            currentArtist = "Audio lokal"
            pickedName = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Audio lokal"
            currentAudioUrl = uri.toString()
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.play()
            activeTab = "Player"
            playerMode = "Visual"
        }
    }

    fun playOnline(track: OnlineTrack) {
        scope.launch {
            onlineError = null
            try {
                val audio = OnlineCatalog.streamUrl(track)
                currentAudioUrl = audio
                currentOnlineTrack = track
                currentArtist = track.artist
                currentVideoUrl = null
                pickedName = track.title
                pickedUri = null
                player.setMediaItem(MediaItem.fromUri(audio))
                player.prepare()
                player.play()
                playerMode = "Visual"
                activeTab = "Player"
                // Video is optional; a missing video never blocks audio playback.
                currentVideoUrl = try { OnlineCatalog.videoUrl(track) } catch (_: Exception) { null }
            } catch (e: Exception) {
                onlineError = e.message ?: "Audio tidak dapat diputar"
                activeTab = "Search"
            }
        }
    }

    val bg = if (darkMode) Night else Paper
    val surface = if (darkMode) Panel else Color.White
    val text = if (darkMode) SoftWhite else Ink
    val muted = if (darkMode) Color(0xFFA8A9B8) else Color(0xFF777789)

    MaterialTheme(colorScheme = if (darkMode) darkColorScheme(
        background = bg, surface = surface, primary = Coral, secondary = Lavender, onBackground = text, onSurface = text
    ) else lightColorScheme(
        background = bg, surface = surface, primary = Coral, secondary = Lavender, onBackground = text, onSurface = text
    )) {
        Scaffold(
            containerColor = bg,
            bottomBar = {
                NavigationBar(containerColor = surface, contentColor = text) {
                    listOf("Home", "Search", "Library", "Settings").forEach { tab ->
                        val icon = when (tab) {
                            "Home" -> Icons.Default.Home
                            "Search" -> Icons.Default.Search
                            "Library" -> Icons.Default.LibraryMusic
                            else -> Icons.Default.Settings
                        }
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            icon = { Icon(icon, contentDescription = tab) },
                            label = { Text(tab, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Coral, indicatorColor = Coral.copy(alpha = .14f), selectedTextColor = Coral)
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("MASTERZ", color = text, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text("YOUR SOUND, YOUR SPACE", color = muted, fontSize = 9.sp, letterSpacing = 1.8.sp)
                    }
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Coral.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                        Text("MZ", color = Coral, fontWeight = FontWeight.ExtraBold)
                    }
                }

                when (activeTab) {
                    "Home" -> HomeContent(
                        text, muted, surface, pickedName, currentArtist, isPlaying, trendingTracks, loadingTrending, trendingError,
                        onPick = { audioPicker.launch(arrayOf("audio/*")) },
                        onPlay = {
                            if (player.currentMediaItem != null) {
                                if (player.isPlaying) player.pause() else player.play()
                            } else audioPicker.launch(arrayOf("audio/*"))
                        },
                        onOpenPlayer = { if (player.currentMediaItem != null) activeTab = "Player" },
                        onPlayTrack = { playOnline(it) },
                        onRefreshTrending = {
                            scope.launch {
                                loadingTrending = true
                                trendingError = null
                                try { trendingTracks = OnlineCatalog.trending() }
                                catch (e: Exception) { trendingError = e.message ?: "Gagal memuat rekomendasi" }
                                finally { loadingTrending = false }
                            }
                        }
                    )
                    "Search" -> SearchContent(
                        text, muted, surface, query, { query = it }, onlineTracks, searching, onlineError,
                        onSearch = {
                            scope.launch {
                                searching = true
                                onlineError = null
                                try { onlineTracks = OnlineCatalog.search(query) }
                                catch (e: Exception) { onlineError = e.message ?: "Gagal memuat katalog" }
                                finally { searching = false }
                            }
                        },
                        onPick = { audioPicker.launch(arrayOf("audio/*")) },
                        onPlayTrack = { playOnline(it) }
                    )
                    "Player" -> PlayerContent(
                        player = player, title = pickedName, artist = currentArtist, isPlaying = isPlaying,
                        mode = playerMode, onModeChange = { mode ->
                            playerMode = mode
                            if (mode == "Video" && currentVideoUrl != null) {
                                player.setMediaItem(MediaItem.fromUri(currentVideoUrl!!))
                                player.prepare()
                                player.play()
                            } else if (mode != "Video" && currentAudioUrl != null &&
                                player.currentMediaItem?.localConfiguration?.uri?.toString() != currentAudioUrl) {
                                player.setMediaItem(MediaItem.fromUri(currentAudioUrl!!))
                                player.prepare()
                                player.play()
                            }
                        },
                        videoUrl = currentVideoUrl, lyrics = lyrics, lyricsLoading = lyricsLoading, lyricsError = lyricsError,
                        positionMs = positionMs, durationMs = durationMs,
                        onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                        onSeek = { value -> player.seekTo(value.toLong()); positionMs = value.toLong() },
                        onNext = { if (player.hasNextMediaItem()) player.seekToNextMediaItem() },
                        onBack = { activeTab = "Home" }
                    )
                    "Library" -> Column(Modifier.fillMaxSize().padding(22.dp)) {
                        Text("Koleksi kamu", color = text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(18.dp))
                        LibraryRow(pickedName, muted, surface, onClick = { audioPicker.launch(arrayOf("audio/*")) })
                    }
                    else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item {
                            Text("Pengaturan", color = text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(surface).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Tampilan", color = text, fontWeight = FontWeight.SemiBold)
                                    Text(if (darkMode) "Mode gelap aktif" else "Mode terang aktif", color = muted, fontSize = 12.sp)
                                }
                                Switch(checked = darkMode, onCheckedChange = onThemeChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Coral))
                            }
                        }
                        item { AudioEffectsPanel(player) }
                        item { Text("MasterZ 0.3.0 • Bebas iklan", color = muted, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeContent(
    text: Color, muted: Color, surface: Color, pickedName: String, artist: String, isPlaying: Boolean,
    trending: List<OnlineTrack>, loading: Boolean, error: String?,
    onPick: () -> Unit, onPlay: () -> Unit, onOpenPlayer: () -> Unit,
    onPlayTrack: (OnlineTrack) -> Unit, onRefreshTrending: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column {
                Text("Selamat datang kembali.", color = muted, fontSize = 13.sp)
                Text("Putar suasana hatimu.", color = text, fontSize = 27.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(BrushColors.hero()).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("MUSIC, WITHOUT LIMITS", color = Color.White.copy(alpha = .8f), fontSize = 9.sp, letterSpacing = 1.3.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Dengarkan\ncaramu.", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 28.sp)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onPick, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Tambah musik")
                    }
                }
                Box(Modifier.size(100.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.GraphicEq, null, tint = Color.White, modifier = Modifier.size(62.dp))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(surface).clickable { onOpenPlayer() }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Lavender.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Album, null, tint = Lavender, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(pickedName, color = text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artist, color = muted, fontSize = 11.sp)
                }
                IconButton(onClick = onPlay) { Icon(if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, null, tint = Coral, modifier = Modifier.size(34.dp)) }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sedang populer", color = text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("Musik Indonesia yang sedang ramai di Audius • arsip terbuka sebagai cadangan", color = muted, fontSize = 10.sp)
                }
                IconButton(onClick = onRefreshTrending, enabled = !loading) { Icon(Icons.Default.Refresh, null, tint = Coral) }
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Coral) }
        if (error != null) item { Text(error, color = muted, fontSize = 12.sp) }
        items(trending, key = { it.identifier }) { track ->
            OnlineTrackRow(track, text, muted, surface, onClick = { onPlayTrack(track) }, showDownloads = true)
        }
        item {
            Text("Jelajahi kategori", color = text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GenreTile("Chill", "01", Lavender, Modifier.weight(1f))
                GenreTile("Focus", "02", Coral, Modifier.weight(1f))
                GenreTile("Energy", "03", Color(0xFF5D9E91), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SearchContent(
    text: Color, muted: Color, surface: Color, query: String, onQuery: (String) -> Unit,
    tracks: List<OnlineTrack>, searching: Boolean, error: String?,
    onSearch: () -> Unit, onPick: () -> Unit, onPlayTrack: (OnlineTrack) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Musik online", color = text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Cari musik Indonesia dari Audius dan arsip audio berlisensi terbuka", color = muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cari judul atau artis...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(18.dp), singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = query.isNotBlank() && !searching, onClick = onSearch, colors = ButtonDefaults.buttonColors(containerColor = Coral)) {
                Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text("Cari online")
            }
            OutlinedButton(onClick = onPick) {
                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("File HP")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (searching) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Coral)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        if (!searching && tracks.isEmpty() && error == null) {
            Text("Hasil musik legal akan muncul di sini. Koneksi internet diperlukan.", color = muted, fontSize = 12.sp)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(tracks, key = { it.identifier }) { track ->
                OnlineTrackRow(track, text, muted, surface, onClick = { onPlayTrack(track) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerContent(
    player: ExoPlayer, title: String, artist: String, isPlaying: Boolean, mode: String,
    onModeChange: (String) -> Unit, videoUrl: String?, lyrics: String?, lyricsLoading: Boolean, lyricsError: String?,
    positionMs: Long, durationMs: Long, onPlayPause: () -> Unit, onSeek: (Float) -> Unit,
    onNext: () -> Unit, onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onBackground) }
            Text("NOW PLAYING", modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { onModeChange("Visual") }) { Icon(Icons.Default.GraphicEq, null, tint = Coral) }
        }
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Visual", "Lirik", "Video").forEachIndexed { index, item ->
                SegmentedButton(selected = mode == item, onClick = { onModeChange(item) }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(item) }
            }
        }
        Spacer(Modifier.height(14.dp))
        when (mode) {
            "Video" -> {
                if (videoUrl != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(22.dp)),
                        factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = false } },
                        update = { it.player = player }
                    )
                } else {
                    Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.VideocamOff, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Video tidak tersedia", fontWeight = FontWeight.Bold)
                            Text("Sumber ini hanya menyediakan audio.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
            "Lirik" -> {
                Card(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(22.dp)) {
                    Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                        when {
                            lyricsLoading -> CircularProgressIndicator(color = Coral)
                            lyrics != null -> LazyColumn { item { Text(lyrics, fontSize = 15.sp, lineHeight = 25.sp) } }
                            else -> Text(lyricsError ?: "Lirik belum tersedia", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            else -> VisualizerPanel(isPlaying)
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
        Slider(
            value = if (durationMs > 0) positionMs.coerceIn(0L, durationMs).toFloat() else 0f,
            onValueChange = onSeek,
            valueRange = 0f..(durationMs.coerceAtLeast(1L).toFloat()),
            enabled = durationMs > 0
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(positionMs), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            Text(formatTime(durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { player.seekTo(0) }) { Icon(Icons.Default.Replay10, null, modifier = Modifier.size(26.dp)) }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(68.dp)) {
                Icon(if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled, null, tint = Coral, modifier = Modifier.size(62.dp))
            }
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(30.dp)) }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun VisualizerPanel(isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "audio-visualizer")
    Box(Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(26.dp)).background(
        androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF393451), Color(0xFF775D83), Color(0xFFE8795B)))
    ), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(150.dp).clip(RoundedCornerShape(38.dp)).background(Color.White.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Album, null, tint = Color.White, modifier = Modifier.size(94.dp))
            }
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp).height(72.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(25) { index ->
                    val target = if (isPlaying) 0.25f + ((index * 37) % 70) / 100f else 0.12f
                    val height by transition.animateFloat(
                        initialValue = 0.12f, targetValue = target,
                        animationSpec = infiniteRepeatable(tween(350 + (index % 5) * 100), RepeatMode.Reverse),
                        label = "bar-$index"
                    )
                    Box(Modifier.weight(1f).fillMaxHeight(height).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = .85f)))
                }
            }
            Text(if (isPlaying) "NOW PLAYING" else "PAUSED", color = Color.White.copy(alpha = .8f), fontSize = 10.sp, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun OnlineTrackRow(
    track: OnlineTrack, text: Color, muted: Color, surface: Color, onClick: () -> Unit, showDownloads: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(surface)
            .clickable(onClick = onClick).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Lavender.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Cloud, null, tint = Lavender)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (track.source == "Audius") "♫ ${track.downloads} pemutaran • Audius" else "Internet Archive • ${track.license.substringAfterLast("/").ifBlank { "Lisensi terbuka" }}", color = muted, fontSize = 9.sp)
        }
        Icon(Icons.Default.PlayCircle, null, tint = Coral, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun GenreTile(title: String, number: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = .16f)).padding(12.dp)) {
        Text(number, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(22.dp))
        Text(title, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LibraryRow(name: String, muted: Color, surface: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(surface).clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.LibraryMusic, null, tint = Coral)
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Lagu lokal", fontWeight = FontWeight.SemiBold)
            Text(name, color = muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private object BrushColors {
    fun hero() = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF5A557F), Color(0xFFB56C68), Color(0xFFE89A70)))
}
