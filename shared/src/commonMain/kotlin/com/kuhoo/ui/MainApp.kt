package com.kuhoo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuhoo.innertube.InnerTubeService
import com.kuhoo.media.AudioPlayer
import com.kuhoo.media.PlaybackState
import com.kuhoo.media.TrackInfo
import com.kuhoo.ui.canvas.ComposableViviMusicCanvas
import com.kuhoo.ui.lyrics.KaraokeLyricsView
import com.kuhoo.ui.lyrics.LyricsParser
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KuhooApp(
    audioPlayer: AudioPlayer = koinInject(),
    innerTubeService: InnerTubeService = koinInject()
) {
    val scope = rememberCoroutineScope()
    var selectedScreen by remember { mutableStateOf("Home") }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showPlayerSheet by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    val currentTrack by audioPlayer.currentTrack.collectAsState()
    val playbackState by audioPlayer.playbackState.collectAsState()
    val positionMs by audioPlayer.positionMs.collectAsState()
    val durationMs by audioPlayer.durationMs.collectAsState()
    val volume by audioPlayer.volume.collectAsState()

    val sampleLrc = """
        [00:00.00]Welcome to Kuhoo Music Player
        [00:05.00]Ad-Free Streaming & Local Offline Caching
        [00:10.00]Compose Multiplatform for Desktop and WebAssembly
        [00:15.00]Enjoy synchronized karaoke lyrics!
        [00:20.00]Kuhoo - Music Redefined
    """.trimIndent()
    val lyrics = remember { LyricsParser.parseLrc(sampleLrc) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6366F1),
            secondary = Color(0xFFEC4899),
            background = Color(0xFF0F172A),
            surface = Color(0xFF1E293B),
            onPrimary = Color.White,
            onSurface = Color.White
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Navigation Rail / Side Bar
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = Color(0xFF020617)
                ) {
                    Spacer(Modifier.height(16.dp))
                    Text("Kuhoo", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
                    Spacer(Modifier.height(32.dp))

                    NavigationRailItem(
                        selected = selectedScreen == "Home",
                        onClick = { selectedScreen = "Home" },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationRailItem(
                        selected = selectedScreen == "Explore",
                        onClick = { selectedScreen = "Explore" },
                        icon = { Icon(Icons.Default.Explore, contentDescription = "Explore") },
                        label = { Text("Explore") }
                    )
                    NavigationRailItem(
                        selected = selectedScreen == "Search",
                        onClick = { selectedScreen = "Search" },
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search") }
                    )
                    NavigationRailItem(
                        selected = selectedScreen == "Library",
                        onClick = { selectedScreen = "Library" },
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                        label = { Text("Library") }
                    )
                }

                // Main Content Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Top Search Bar
                    TopAppBar(
                        title = {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search songs, artists, albums...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = {
                                            isSearching = true
                                            scope.launch {
                                                searchResults = innerTubeService.searchTracks(searchQuery)
                                                isSearching = false
                                                selectedScreen = "Search"
                                            }
                                        }) {
                                            Icon(Icons.Default.ArrowForward, contentDescription = "Search")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(26.dp),
                                singleLine = true
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedScreen) {
                            "Home" -> HomeScreenContent(onTrackSelect = { track ->
                                scope.launch {
                                    val streamUrl = innerTubeService.getStreamUrl(track.id)
                                    audioPlayer.playTrack(track.copy(streamUrl = streamUrl))
                                }
                            })
                            "Search" -> SearchScreenContent(
                                query = searchQuery,
                                results = searchResults,
                                isLoading = isSearching,
                                onTrackSelect = { track ->
                                    scope.launch {
                                        val streamUrl = innerTubeService.getStreamUrl(track.id)
                                        audioPlayer.playTrack(track.copy(streamUrl = streamUrl))
                                    }
                                }
                            )
                            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("$selectedScreen Screen coming soon", fontSize = 20.sp, color = Color.Gray)
                            }
                        }
                    }

                    // Mini Player Bottom Bar
                    currentTrack?.let { track ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clickable { showPlayerSheet = true },
                            color = Color(0xFF1E293B)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF6366F1)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.title, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                    Text(track.artist, color = Color.Gray, fontSize = 13.sp, maxLines = 1)
                                }

                                IconButton(onClick = { showLyrics = !showLyrics }) {
                                    Icon(
                                        Icons.Default.Subtitles,
                                        contentDescription = "Lyrics",
                                        tint = if (showLyrics) Color(0xFFEC4899) else Color.White
                                    )
                                }

                                IconButton(onClick = {
                                    if (playbackState == PlaybackState.PLAYING) audioPlayer.pause()
                                    else audioPlayer.play()
                                }) {
                                    Icon(
                                        if (playbackState == PlaybackState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Full Screen Player Sheet / Modal
            AnimatedVisibility(
                visible = showPlayerSheet,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                currentTrack?.let { track ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Dynamic Animated Canvas Visualizer
                        ComposableViviMusicCanvas()

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE0F172A))))
                                .padding(24.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { showPlayerSheet = false }) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close", tint = Color.White)
                                }
                                Text("Now Playing", fontWeight = FontWeight.Bold, color = Color.White)
                                IconButton(onClick = { showLyrics = !showLyrics }) {
                                    Icon(Icons.Default.Subtitles, contentDescription = "Lyrics", tint = if (showLyrics) Color(0xFFEC4899) else Color.White)
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            if (showLyrics) {
                                KaraokeLyricsView(
                                    lyrics = lyrics,
                                    currentPositionMs = positionMs,
                                    onSeekTo = { audioPlayer.seekTo(it) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(280.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF6366F1).copy(alpha = 0.8f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(100.dp), tint = Color.White)
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Text(track.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(track.artist, fontSize = 16.sp, color = Color.LightGray)

                            Spacer(Modifier.height(16.dp))

                            Slider(
                                value = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
                                onValueChange = { percent -> audioPlayer.seekTo((percent * durationMs).toLong()) },
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF6366F1), activeTrackColor = Color(0xFF6366F1))
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { audioPlayer.seekTo(0) }) {
                                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp), tint = Color.White)
                                }
                                FloatingActionButton(
                                    onClick = {
                                        if (playbackState == PlaybackState.PLAYING) audioPlayer.pause()
                                        else audioPlayer.play()
                                    },
                                    containerColor = Color(0xFF6366F1),
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        if (playbackState == PlaybackState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White
                                    )
                                }
                                IconButton(onClick = {}) {
                                    Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp), tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreenContent(onTrackSelect: (TrackInfo) -> Unit) {
    val innerTubeService: InnerTubeService = koinInject()
    var homeSections by remember { mutableStateOf<List<com.kuhoo.innertube.HomeSection>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoading = true
        homeSections = innerTubeService.getHomeSections()
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF6366F1))
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            "Welcome to the Kuhoo Music Platform",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Ad-Free Cross-Platform Streaming & Offline Caching",
                            fontSize = 15.sp,
                            color = Color(0xFF6366F1)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Lead Developer: Sai Dheeraj Nalkari (nsd999) • Contact: nalkarisaidheeraj@gmail.com",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }

            if (homeSections.isEmpty()) {
                item {
                    Text("No recommendations available. Try searching for a song!", fontSize = 16.sp, color = Color.Gray)
                }
            }

            homeSections.forEach { section ->
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(section.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                }

                items(section.items.take(10)) { track ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onTrackSelect(track) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFEC4899)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(track.title, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                Text(track.artist, color = Color.Gray, fontSize = 14.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreenContent(
    query: String,
    results: List<TrackInfo>,
    isLoading: Boolean,
    onTrackSelect: (TrackInfo) -> Unit
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF6366F1))
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            item {
                Text("Search Results for \"$query\"", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(16.dp))
            }
            items(results) { track ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onTrackSelect(track) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF6366F1)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(track.title, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(track.artist, color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
