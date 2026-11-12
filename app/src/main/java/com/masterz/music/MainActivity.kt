package com.masterz.music

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
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
    var isPlaying by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("Home") }
    var query by remember { mutableStateOf("") }
    var onlineTracks by remember { mutableStateOf<List<OnlineTrack>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(player) { onDispose { player.release() } }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            pickedUri = uri
            pickedName = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Audio lokal"
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.play()
            isPlaying = true
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
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
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
                    "Home" -> HomeContent(text, muted, surface, pickedName, isPlaying, onPick = { audioPicker.launch(arrayOf("audio/*")) }, onPlay = {
                        if (player.currentMediaItem != null) {
                            if (player.isPlaying) player.pause() else player.play()
                            isPlaying = player.isPlaying
                        } else audioPicker.launch(arrayOf("audio/*"))
                    })
                    "Search" -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                        Text("Musik online", color = text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Text("Cari audio berlisensi Creative Commons / domain publik", color = muted, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Cari judul atau artis...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            shape = RoundedCornerShape(18.dp), singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = query.isNotBlank() && !searching,
                                onClick = {
                                    scope.launch {
                                        searching = true
                                        onlineError = null
                                        try { onlineTracks = OnlineCatalog.search(query) }
                                        catch (e: Exception) { onlineError = e.message ?: "Gagal memuat katalog" }
                                        finally { searching = false }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Coral)
                            ) {
                                Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text("Cari online")
                            }
                            OutlinedButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("File HP")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (searching) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Coral)
                        onlineError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                        if (!searching && onlineTracks.isEmpty() && onlineError == null) {
                            Text("Hasil musik legal akan muncul di sini. Koneksi internet diperlukan.", color = muted, fontSize = 12.sp)
                        }
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                            items(onlineTracks.size) { index ->
                                val track = onlineTracks[index]
                                OnlineTrackRow(track, text, muted, surface, onClick = {
                                    scope.launch {
                                        onlineError = null
                                        try {
                                            val stream = OnlineCatalog.streamUrl(track)
                                            player.setMediaItem(MediaItem.fromUri(stream))
                                            player.prepare()
                                            player.play()
                                            pickedName = track.title
                                            pickedUri = null
                                            isPlaying = true
                                        } catch (e: Exception) {
                                            onlineError = e.message ?: "Audio tidak dapat diputar"
                                        }
                                    }
                                })
                            }
                        }
                    }
                    "Library" -> Column(Modifier.fillMaxSize().padding(22.dp)) {
                        Text("Koleksi kamu", color = text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(18.dp))
                        LibraryRow(pickedName, muted, surface, onClick = { audioPicker.launch(arrayOf("audio/*")) })
                    }
                    else -> Column(Modifier.fillMaxSize().padding(22.dp)) {
                        Text("Pengaturan", color = text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(surface).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Tampilan", color = text, fontWeight = FontWeight.SemiBold)
                                Text(if (darkMode) "Mode gelap aktif" else "Mode terang aktif", color = muted, fontSize = 12.sp)
                            }
                            Switch(checked = darkMode, onCheckedChange = onThemeChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Coral))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("MasterZ 0.1.0 • Bebas iklan", color = muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeContent(
    text: Color, muted: Color, surface: Color, pickedName: String, isPlaying: Boolean,
    onPick: () -> Unit, onPlay: () -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Putar dari perangkat", color = text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("LOKAL", color = Coral, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(surface).clickable { onPick() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(Lavender.copy(alpha = .2f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = Lavender, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(pickedName, color = text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Audio di perangkat", color = muted, fontSize = 11.sp)
                }
                IconButton(onClick = onPlay) { Icon(if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle, null, tint = Coral, modifier = Modifier.size(34.dp)) }
            }
        }
        item {
            Text("Jelajahi MasterZ", color = text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GenreTile("Chill", "01", Lavender, Modifier.weight(1f))
                GenreTile("Focus", "02", Coral, Modifier.weight(1f))
                GenreTile("Energy", "03", Color(0xFF5D9E91), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Text("Cari audio legal di tab Search, atau tambahkan koleksi MP3 dari perangkat.", color = muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun OnlineTrackRow(
    track: OnlineTrack, text: Color, muted: Color, surface: Color, onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(surface)
            .clickable(onClick = onClick).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(13.dp))
                .background(Lavender.copy(alpha = .18f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Cloud, null, tint = Lavender) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Creative Commons / domain publik", color = muted, fontSize = 9.sp)
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

private object BrushColors {
    fun hero() = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF5A557F), Color(0xFFB56C68), Color(0xFFE89A70)))
}
