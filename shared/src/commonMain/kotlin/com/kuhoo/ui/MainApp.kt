package com.kuhoo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kuhoo.innertube.HomeSection
import com.kuhoo.innertube.InnerTubeService
import com.kuhoo.media.AudioPlayer
import com.kuhoo.media.PlaybackState
import com.kuhoo.media.TrackInfo
import com.kuhoo.ui.canvas.ComposableViviMusicCanvas
import com.kuhoo.ui.lyrics.KaraokeLyricsView
import com.kuhoo.ui.lyrics.LyricsParser
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// Spotify-inspired color palette
private val SpotifyBlack = Color(0xFF121212)
private val SpotifyDarkGray = Color(0xFF181818)
private val SpotifyMedGray = Color(0xFF282828)
private val SpotifyLightGray = Color(0xFFB3B3B3)
private val SpotifyGreen = Color(0xFF1DB954)
private val SpotifyWhite = Color(0xFFFFFFFF)
private val KuhooPurple = Color(0xFF6366F1)
private val KuhooPink = Color(0xFFEC4899)

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

    fun playTrack(track: TrackInfo) {
        scope.launch {
            val streamUrl = innerTubeService.getStreamUrl(track.id)
            if (streamUrl.isNotEmpty()) {
                audioPlayer.playTrack(track.copy(streamUrl = streamUrl))
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = KuhooPurple,
            secondary = KuhooPink,
            background = SpotifyBlack,
            surface = SpotifyDarkGray,
            onPrimary = SpotifyWhite,
            onSurface = SpotifyWhite
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SpotifyBlack
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Spotify-style Sidebar
                Column(
                    modifier = Modifier
                        .width(72.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF000000))
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Logo
                    Text("K", fontSize = 28.sp, fontWeight = FontWeight.Black, color = KuhooPurple)
                    Spacer(Modifier.height(32.dp))

                    NavItem("Home", Icons.Default.Home, selectedScreen == "Home") { selectedScreen = "Home" }
                    NavItem("Search", Icons.Default.Search, selectedScreen == "Search") { selectedScreen = "Search" }
                    NavItem("Explore", Icons.Default.Explore, selectedScreen == "Explore") { selectedScreen = "Explore" }
                    NavItem("Library", Icons.Default.LibraryMusic, selectedScreen == "Library") { selectedScreen = "Library" }
                }

                // Main Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Top Bar with search
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SpotifyBlack.copy(alpha = 0.95f))
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("What do you want to listen to?", color = SpotifyLightGray) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = SpotifyLightGray) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        isSearching = true
                                        selectedScreen = "Search"
                                        scope.launch {
                                            searchResults = innerTubeService.searchTracks(searchQuery)
                                            isSearching = false
                                        }
                                    }) {
                                        Icon(Icons.Default.ArrowForward, "Search", tint = SpotifyWhite)
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = KuhooPurple,
                                unfocusedBorderColor = SpotifyMedGray,
                                focusedContainerColor = SpotifyMedGray,
                                unfocusedContainerColor = SpotifyMedGray,
                                cursorColor = SpotifyWhite,
                                focusedTextColor = SpotifyWhite,
                                unfocusedTextColor = SpotifyWhite
                            )
                        )
                    }

                    // Screen Content
                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedScreen) {
                            "Home" -> HomeScreen(onTrackSelect = ::playTrack)
                            "Search" -> SearchScreen(
                                query = searchQuery,
                                results = searchResults,
                                isLoading = isSearching,
                                onTrackSelect = ::playTrack
                            )
                            "Explore" -> ExploreScreen(onTrackSelect = ::playTrack)
                            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.LibraryMusic, null, Modifier.size(64.dp), tint = SpotifyLightGray)
                                    Spacer(Modifier.height(16.dp))
                                    Text("Library coming soon", fontSize = 18.sp, color = SpotifyLightGray)
                                }
                            }
                        }
                    }

                    // Mini Player (Spotify-style bottom bar)
                    currentTrack?.let { track ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPlayerSheet = true },
                            color = SpotifyMedGray,
                            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        ) {
                            Column {
                                // Progress bar on top of mini player
                                LinearProgressIndicator(
                                    progress = { if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f },
                                    modifier = Modifier.fillMaxWidth().height(2.dp),
                                    color = KuhooPurple,
                                    trackColor = SpotifyMedGray
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Album art
                                    if (track.thumbnailUrl?.isNotEmpty() == true) {
                                        AsyncImage(
                                            model = track.thumbnailUrl,
                                            contentDescription = track.title,
                                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(KuhooPurple),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.MusicNote, null, tint = SpotifyWhite)
                                        }
                                    }

                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(track.title, fontWeight = FontWeight.SemiBold, color = SpotifyWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.artist, color = SpotifyLightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }

                                    IconButton(onClick = { showLyrics = !showLyrics }) {
                                        Icon(Icons.Default.Subtitles, "Lyrics", tint = if (showLyrics) KuhooPink else SpotifyWhite)
                                    }
                                    IconButton(onClick = {
                                        if (playbackState == PlaybackState.PLAYING) audioPlayer.pause() else audioPlayer.play()
                                    }) {
                                        Icon(
                                            if (playbackState == PlaybackState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            "Play/Pause", tint = SpotifyWhite, modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Full-Screen Player Overlay
            AnimatedVisibility(
                visible = showPlayerSheet,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                currentTrack?.let { track ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        ComposableViviMusicCanvas()

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, SpotifyBlack.copy(alpha = 0.95f))))
                                .padding(32.dp)
                        ) {
                            // Top row
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { showPlayerSheet = false }) {
                                    Icon(Icons.Default.KeyboardArrowDown, "Close", tint = SpotifyWhite, modifier = Modifier.size(32.dp))
                                }
                                Text("Now Playing", fontWeight = FontWeight.Bold, color = SpotifyWhite, fontSize = 14.sp)
                                IconButton(onClick = { showLyrics = !showLyrics }) {
                                    Icon(Icons.Default.Subtitles, "Lyrics", tint = if (showLyrics) KuhooPink else SpotifyWhite)
                                }
                            }

                            Spacer(Modifier.height(24.dp))

                            if (showLyrics) {
                                KaraokeLyricsView(lyrics = lyrics, currentPositionMs = positionMs, onSeekTo = { audioPlayer.seekTo(it) }, modifier = Modifier.weight(1f))
                            } else {
                                // Album art
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    if (track.thumbnailUrl?.isNotEmpty() == true) {
                                        AsyncImage(
                                            model = track.thumbnailUrl,
                                            contentDescription = track.title,
                                            modifier = Modifier.size(300.dp).clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            Modifier.size(300.dp).clip(RoundedCornerShape(12.dp)).background(KuhooPurple.copy(alpha = 0.6f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.MusicNote, null, Modifier.size(120.dp), tint = SpotifyWhite)
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(24.dp))

                            // Track info
                            Text(track.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite, maxLines = 2)
                            Text(track.artist, fontSize = 16.sp, color = SpotifyLightGray, maxLines = 1)

                            Spacer(Modifier.height(24.dp))

                            // Seek bar
                            Slider(
                                value = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
                                onValueChange = { audioPlayer.seekTo((it * durationMs).toLong()) },
                                colors = SliderDefaults.colors(thumbColor = SpotifyWhite, activeTrackColor = KuhooPurple, inactiveTrackColor = SpotifyMedGray)
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatTime(positionMs), fontSize = 12.sp, color = SpotifyLightGray)
                                Text(formatTime(durationMs), fontSize = 12.sp, color = SpotifyLightGray)
                            }

                            Spacer(Modifier.height(16.dp))

                            // Controls
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { audioPlayer.seekTo(0) }) {
                                    Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(40.dp), tint = SpotifyWhite)
                                }
                                FloatingActionButton(
                                    onClick = { if (playbackState == PlaybackState.PLAYING) audioPlayer.pause() else audioPlayer.play() },
                                    containerColor = SpotifyWhite, shape = CircleShape, modifier = Modifier.size(64.dp)
                                ) {
                                    Icon(
                                        if (playbackState == PlaybackState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        "Play/Pause", tint = SpotifyBlack, modifier = Modifier.size(32.dp)
                                    )
                                }
                                IconButton(onClick = {}) {
                                    Icon(Icons.Default.SkipNext, "Next", Modifier.size(40.dp), tint = SpotifyWhite)
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
fun NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = if (selected) KuhooPurple else SpotifyLightGray, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = if (selected) SpotifyWhite else SpotifyLightGray)
    }
}

@Composable
fun HomeScreen(onTrackSelect: (TrackInfo) -> Unit) {
    val innerTubeService: InnerTubeService = koinInject()
    var homeSections by remember { mutableStateOf<List<HomeSection>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        homeSections = innerTubeService.getHomeSections()
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = KuhooPurple)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("Good evening", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite)
                Spacer(Modifier.height(20.dp))
            }

            if (homeSections.isEmpty()) {
                item {
                    Text("No recommendations available right now. Try searching!", fontSize = 16.sp, color = SpotifyLightGray)
                }
            }

            homeSections.forEach { section ->
                item {
                    Spacer(Modifier.height(24.dp))
                    Text(section.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite)
                    Spacer(Modifier.height(12.dp))
                }

                // Horizontal scrolling row of cards (Spotify-style)
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(section.items.take(10)) { track ->
                            TrackCard(track, onTrackSelect)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun TrackCard(track: TrackInfo, onTrackSelect: (TrackInfo) -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable { onTrackSelect(track) }
    ) {
        // Thumbnail
        if (track.thumbnailUrl?.isNotEmpty() == true) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpotifyMedGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, null, Modifier.size(48.dp), tint = SpotifyLightGray)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(track.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SpotifyWhite, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(track.artist, fontSize = 12.sp, color = SpotifyLightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SearchScreen(query: String, results: List<TrackInfo>, isLoading: Boolean, onTrackSelect: (TrackInfo) -> Unit) {
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = KuhooPurple)
        }
    } else if (results.isEmpty() && query.isNotEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.SearchOff, null, Modifier.size(64.dp), tint = SpotifyLightGray)
                Spacer(Modifier.height(16.dp))
                Text("No results for \"$query\"", fontSize = 18.sp, color = SpotifyWhite)
                Text("Try different keywords", fontSize = 14.sp, color = SpotifyLightGray)
            }
        }
    } else if (query.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Search, null, Modifier.size(64.dp), tint = SpotifyLightGray)
                Spacer(Modifier.height(16.dp))
                Text("Search for songs, artists, albums", fontSize = 18.sp, color = SpotifyWhite)
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("Results for \"$query\"", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite)
                Spacer(Modifier.height(16.dp))
            }
            items(results) { track ->
                TrackListItem(track, onTrackSelect)
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun TrackListItem(track: TrackInfo, onTrackSelect: (TrackInfo) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onTrackSelect(track) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        if (track.thumbnailUrl?.isNotEmpty() == true) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = track.title,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(SpotifyMedGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, null, tint = SpotifyLightGray)
            }
        }

        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontWeight = FontWeight.SemiBold, color = SpotifyWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = SpotifyLightGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        if (track.durationMs > 0) {
            Text(formatTime(track.durationMs), fontSize = 12.sp, color = SpotifyLightGray)
        }
    }
}

@Composable
fun ExploreScreen(onTrackSelect: (TrackInfo) -> Unit) {
    val innerTubeService: InnerTubeService = koinInject()
    var exploreSections by remember { mutableStateOf<List<HomeSection>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        exploreSections = innerTubeService.getExploreSections()
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = KuhooPurple)
        }
    } else if (exploreSections.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Explore, null, Modifier.size(64.dp), tint = SpotifyLightGray)
                Spacer(Modifier.height(16.dp))
                Text("Explore is loading...", fontSize = 18.sp, color = SpotifyWhite)
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("Explore", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite)
                Spacer(Modifier.height(20.dp))
            }

            exploreSections.forEach { section ->
                item {
                    Text(section.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite)
                    Spacer(Modifier.height(12.dp))
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(section.items.take(10)) { track ->
                            TrackCard(track, onTrackSelect)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
