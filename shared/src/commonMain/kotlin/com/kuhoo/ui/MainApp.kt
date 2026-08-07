package com.kuhoo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.kuhoo.db.MusicRepository
import com.kuhoo.innertube.AlbumDetail
import com.kuhoo.innertube.ArtistDetail
import com.kuhoo.innertube.HomeSection
import com.kuhoo.innertube.InnerTubeService
import com.kuhoo.innertube.PlaylistDetail
import com.kuhoo.media.AudioPlayer
import com.kuhoo.media.ItemType
import com.kuhoo.media.PlaybackState
import com.kuhoo.media.TrackInfo
import com.kuhoo.ui.canvas.ComposableViviMusicCanvas
import com.kuhoo.ui.lyrics.KaraokeLyricsView
import com.kuhoo.ui.lyrics.LyricsParser
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.Calendar

// Kuhoo color palette (Spotify-inspired dark theme with Kuhoo brand colors)
private val SpotifyBlack = Color(0xFF121212)
private val SpotifyDarkGray = Color(0xFF181818)
private val SpotifyMedGray = Color(0xFF282828)
private val SpotifyLightGray = Color(0xFFB3B3B3)
private val SpotifyGreen = Color(0xFF1DB954)
private val SpotifyWhite = Color(0xFFFFFFFF)
private val KuhooPurple = Color(0xFF6366F1)
private val KuhooPink = Color(0xFFEC4899)
private val KuhooTeal = Color(0xFF2DD4BF)

// =========================================================================
// NAVIGATION STATE
// =========================================================================

sealed class DetailScreen {
    data class AlbumScreen(val browseId: String) : DetailScreen()
    data class PlaylistScreen(val playlistId: String) : DetailScreen()
    data class ArtistScreen(val browseId: String) : DetailScreen()
    data class LocalPlaylist(val type: String) : DetailScreen() // "liked", "recent", "mostplayed", "downloaded"
}

// =========================================================================
// MAIN APP
// =========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KuhooApp(
    audioPlayer: AudioPlayer = koinInject(),
    innerTubeService: InnerTubeService = koinInject(),
    musicRepository: MusicRepository = koinInject()
) {
    val scope = rememberCoroutineScope()
    var selectedScreen by remember { mutableStateOf("Home") }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var showPlayerSheet by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    // Detail screen navigation
    var detailScreen by remember { mutableStateOf<DetailScreen?>(null) }

    val currentTrack by audioPlayer.currentTrack.collectAsState()
    val playbackState by audioPlayer.playbackState.collectAsState()
    val positionMs by audioPlayer.positionMs.collectAsState()
    val durationMs by audioPlayer.durationMs.collectAsState()
    val volume by audioPlayer.volume.collectAsState()

    // Lyrics state
    var currentLyrics by remember { mutableStateOf<List<com.kuhoo.ui.lyrics.LyricLine>>(emptyList()) }

    // Load lyrics when track changes
    LaunchedEffect(currentTrack?.id) {
        currentTrack?.let { track ->
            val syncedLrc = innerTubeService.getSyncedLyrics(track.id)
            currentLyrics = if (syncedLrc != null) {
                LyricsParser.parseLrc(syncedLrc)
            } else {
                val sampleLrc = """
                    [00:00.00]${track.title}
                    [00:05.00]${track.artist}
                    [00:10.00]Playing on Kuhoo Music
                """.trimIndent()
                LyricsParser.parseLrc(sampleLrc)
            }
        }
    }

    fun playTrack(track: TrackInfo) {
        scope.launch {
            val streamUrl = innerTubeService.getStreamUrl(track.id)
            if (streamUrl.isNotEmpty()) {
                audioPlayer.playTrack(track.copy(streamUrl = streamUrl))
                // Record play for personalization
                musicRepository.recordPlay(track)
            }
        }
    }

    fun handleItemClick(track: TrackInfo) {
        when (track.itemType) {
            ItemType.SONG -> playTrack(track)
            ItemType.ALBUM -> { detailScreen = DetailScreen.AlbumScreen(track.id) }
            ItemType.PLAYLIST -> { detailScreen = DetailScreen.PlaylistScreen(track.id) }
            ItemType.ARTIST -> { detailScreen = DetailScreen.ArtistScreen(track.id) }
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
                // =================== SIDEBAR ===================
                Column(
                    modifier = Modifier
                        .width(72.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF000000))
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Kuhoo Logo (bird icon text placeholder — loads from resources if available)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(KuhooPurple, KuhooTeal))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("K", fontSize = 20.sp, fontWeight = FontWeight.Black, color = SpotifyWhite)
                    }
                    Spacer(Modifier.height(32.dp))

                    NavItem("Home", Icons.Default.Home, selectedScreen == "Home") {
                        selectedScreen = "Home"; detailScreen = null
                    }
                    NavItem("Search", Icons.Default.Search, selectedScreen == "Search") {
                        selectedScreen = "Search"; detailScreen = null
                    }
                    NavItem("Explore", Icons.Default.Explore, selectedScreen == "Explore") {
                        selectedScreen = "Explore"; detailScreen = null
                    }
                    NavItem("Library", Icons.Default.LibraryMusic, selectedScreen == "Library") {
                        selectedScreen = "Library"; detailScreen = null
                    }
                }

                // =================== MAIN CONTENT ===================
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
                        // Back button when viewing detail
                        if (detailScreen != null) {
                            IconButton(onClick = { detailScreen = null }) {
                                Icon(Icons.Default.ArrowBack, "Back", tint = SpotifyWhite)
                            }
                            Spacer(Modifier.width(8.dp))
                        }

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
                                        detailScreen = null
                                        scope.launch {
                                            searchResults = innerTubeService.searchAll(searchQuery)
                                            musicRepository.saveSearch(searchQuery)
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
                        // If a detail screen is active, show it
                        if (detailScreen != null) {
                            when (val detail = detailScreen!!) {
                                is DetailScreen.AlbumScreen -> AlbumDetailScreen(
                                    browseId = detail.browseId,
                                    onTrackSelect = ::playTrack,
                                    onBack = { detailScreen = null }
                                )
                                is DetailScreen.PlaylistScreen -> PlaylistDetailScreen(
                                    playlistId = detail.playlistId,
                                    onTrackSelect = ::playTrack,
                                    onBack = { detailScreen = null }
                                )
                                is DetailScreen.ArtistScreen -> ArtistDetailScreen(
                                    browseId = detail.browseId,
                                    onItemClick = ::handleItemClick,
                                    onBack = { detailScreen = null }
                                )
                                is DetailScreen.LocalPlaylist -> LocalPlaylistScreen(
                                    type = detail.type,
                                    onTrackSelect = ::playTrack,
                                    onBack = { detailScreen = null }
                                )
                            }
                        } else {
                            when (selectedScreen) {
                                "Home" -> HomeScreen(
                                    onItemClick = ::handleItemClick
                                )
                                "Search" -> SearchScreen(
                                    query = searchQuery,
                                    results = searchResults,
                                    isLoading = isSearching,
                                    onItemClick = ::handleItemClick
                                )
                                "Explore" -> ExploreScreen(onItemClick = ::handleItemClick)
                                "Library" -> LibraryScreen(
                                    onDetailOpen = { detailScreen = it }
                                )
                            }
                        }
                    }

                    // =================== MINI PLAYER ===================
                    currentTrack?.let { track ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPlayerSheet = true },
                            color = SpotifyMedGray,
                            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        ) {
                            Column {
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
                                        ) { Icon(Icons.Default.MusicNote, null, tint = SpotifyWhite) }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(track.title, fontWeight = FontWeight.SemiBold, color = SpotifyWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.artist, color = SpotifyLightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    // Like button
                                    var isLiked by remember(track.id) { mutableStateOf(false) }
                                    LaunchedEffect(track.id) { isLiked = musicRepository.isLiked(track.id) }
                                    IconButton(onClick = {
                                        scope.launch { isLiked = musicRepository.toggleLike(track.id) }
                                    }) {
                                        Icon(
                                            if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            "Like", tint = if (isLiked) KuhooPink else SpotifyWhite
                                        )
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

            // =================== FULL-SCREEN PLAYER ===================
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
                                KaraokeLyricsView(lyrics = currentLyrics, currentPositionMs = positionMs, onSeekTo = { audioPlayer.seekTo(it) }, modifier = Modifier.weight(1f))
                            } else {
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    if (track.thumbnailUrl?.isNotEmpty() == true) {
                                        AsyncImage(
                                            model = track.thumbnailUrl, contentDescription = track.title,
                                            modifier = Modifier.size(300.dp).clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            Modifier.size(300.dp).clip(RoundedCornerShape(12.dp)).background(KuhooPurple.copy(alpha = 0.6f)),
                                            contentAlignment = Alignment.Center
                                        ) { Icon(Icons.Default.MusicNote, null, Modifier.size(120.dp), tint = SpotifyWhite) }
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                            Text(track.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite, maxLines = 2)
                            Text(track.artist, fontSize = 16.sp, color = SpotifyLightGray, maxLines = 1)
                            Spacer(Modifier.height(24.dp))
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

// =========================================================================
// NAV ITEM
// =========================================================================

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

// =========================================================================
// HOME SCREEN (with personalization)
// =========================================================================

@Composable
fun HomeScreen(onItemClick: (TrackInfo) -> Unit) {
    val innerTubeService: InnerTubeService = koinInject()
    val musicRepository: MusicRepository = koinInject()
    var homeSections by remember { mutableStateOf<List<HomeSection>>(emptyList()) }
    var recentlyPlayed by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            in 18..21 -> "Good evening"
            else -> "Good night"
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        // Load local recently played for personalized section
        recentlyPlayed = musicRepository.getRecentlyPlayed()
        // Load YouTube Music home sections
        homeSections = innerTubeService.getHomeSections()
        isLoading = false
    }

    if (isLoading && recentlyPlayed.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = KuhooPurple)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(greeting, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite)
                Spacer(Modifier.height(20.dp))
            }

            // Recently Played section (local, always available)
            if (recentlyPlayed.isNotEmpty()) {
                item {
                    Text("Recently Played", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite)
                    Spacer(Modifier.height(12.dp))
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(recentlyPlayed.take(10)) { track ->
                            TrackCard(track, onItemClick)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            if (homeSections.isEmpty() && !isLoading) {
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
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 16.dp)
                    ) {
                        items(section.items.take(10)) { track ->
                            TrackCard(track, onItemClick)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

// =========================================================================
// TRACK CARD (responsive - handles different item types)
// =========================================================================

@Composable
fun TrackCard(track: TrackInfo, onItemClick: (TrackInfo) -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable { onItemClick(track) }
    ) {
        Box(modifier = Modifier.size(160.dp)) {
            if (track.thumbnailUrl?.isNotEmpty() == true) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = track.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(if (track.itemType == ItemType.ARTIST) CircleShape else RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(if (track.itemType == ItemType.ARTIST) CircleShape else RoundedCornerShape(8.dp))
                        .background(
                            when (track.itemType) {
                                ItemType.ALBUM -> Brush.linearGradient(listOf(KuhooPurple, KuhooPink))
                                ItemType.PLAYLIST -> Brush.linearGradient(listOf(KuhooTeal, KuhooPurple))
                                ItemType.ARTIST -> Brush.linearGradient(listOf(KuhooPink, KuhooPurple))
                                else -> Brush.linearGradient(listOf(SpotifyMedGray, SpotifyMedGray))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (track.itemType) {
                            ItemType.ALBUM -> Icons.Default.Album
                            ItemType.PLAYLIST -> Icons.Default.QueueMusic
                            ItemType.ARTIST -> Icons.Default.Person
                            else -> Icons.Default.MusicNote
                        },
                        null, Modifier.size(48.dp), tint = SpotifyWhite.copy(alpha = 0.8f)
                    )
                }
            }
            // Small play button overlay for songs
            if (track.itemType == ItemType.SONG) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .size(32.dp).clip(CircleShape).background(KuhooPurple),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp), tint = SpotifyWhite)
                }
            }
            // Type badge for non-songs
            if (track.itemType != ItemType.SONG) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    color = SpotifyBlack.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        when (track.itemType) {
                            ItemType.ALBUM -> "Album"
                            ItemType.PLAYLIST -> "Playlist"
                            ItemType.ARTIST -> "Artist"
                            else -> ""
                        },
                        fontSize = 10.sp, color = SpotifyWhite,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(track.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SpotifyWhite, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (track.artist.isNotEmpty()) {
            Text(track.artist, fontSize = 12.sp, color = SpotifyLightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// =========================================================================
// SEARCH SCREEN
// =========================================================================

@Composable
fun SearchScreen(query: String, results: List<TrackInfo>, isLoading: Boolean, onItemClick: (TrackInfo) -> Unit) {
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
                TrackListItem(track, onItemClick)
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

// =========================================================================
// TRACK LIST ITEM (horizontal row item)
// =========================================================================

@Composable
fun TrackListItem(track: TrackInfo, onItemClick: (TrackInfo) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onItemClick(track) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (track.thumbnailUrl?.isNotEmpty() == true) {
            AsyncImage(
                model = track.thumbnailUrl, contentDescription = track.title,
                modifier = Modifier.size(52.dp).clip(
                    if (track.itemType == ItemType.ARTIST) CircleShape else RoundedCornerShape(6.dp)
                ),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(SpotifyMedGray),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.MusicNote, null, tint = SpotifyLightGray) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontWeight = FontWeight.SemiBold, color = SpotifyWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.itemType != ItemType.SONG) {
                    Text(
                        when (track.itemType) {
                            ItemType.ALBUM -> "Album"
                            ItemType.PLAYLIST -> "Playlist"
                            ItemType.ARTIST -> "Artist"
                            else -> ""
                        },
                        fontSize = 11.sp, color = KuhooPurple,
                        modifier = Modifier
                            .background(KuhooPurple.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(track.artist, color = SpotifyLightGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (track.durationMs > 0) {
            Text(formatTime(track.durationMs), fontSize = 12.sp, color = SpotifyLightGray)
        }
        if (track.itemType != ItemType.SONG) {
            Icon(Icons.Default.ChevronRight, null, tint = SpotifyLightGray, modifier = Modifier.size(20.dp))
        }
    }
}

// =========================================================================
// EXPLORE SCREEN
// =========================================================================

@Composable
fun ExploreScreen(onItemClick: (TrackInfo) -> Unit) {
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
                            TrackCard(track, onItemClick)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

// =========================================================================
// LIBRARY SCREEN (with auto-generated playlists)
// =========================================================================

@Composable
fun LibraryScreen(onDetailOpen: (DetailScreen) -> Unit) {
    val musicRepository: MusicRepository = koinInject()
    var likedCount by remember { mutableStateOf(0) }
    var recentCount by remember { mutableStateOf(0) }
    var mostPlayedCount by remember { mutableStateOf(0) }
    var downloadedCount by remember { mutableStateOf(0) }
    var recentlyPlayed by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        val liked = musicRepository.getLikedSongs()
        likedCount = liked.size
        val recent = musicRepository.getRecentlyPlayed()
        recentCount = recent.size
        recentlyPlayed = recent.take(6)
        mostPlayedCount = musicRepository.getMostPlayed().size
        downloadedCount = musicRepository.getDownloadedSongs().size
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Your Library", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite)
            Spacer(Modifier.height(24.dp))
        }

        // Auto-generated playlist cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LibraryPlaylistCard(
                    title = "Liked Songs",
                    icon = Icons.Default.Favorite,
                    count = likedCount,
                    gradientColors = listOf(KuhooPink, KuhooPurple),
                    modifier = Modifier.weight(1f),
                    onClick = { onDetailOpen(DetailScreen.LocalPlaylist("liked")) }
                )
                LibraryPlaylistCard(
                    title = "Recently Played",
                    icon = Icons.Default.History,
                    count = recentCount,
                    gradientColors = listOf(KuhooPurple, KuhooTeal),
                    modifier = Modifier.weight(1f),
                    onClick = { onDetailOpen(DetailScreen.LocalPlaylist("recent")) }
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LibraryPlaylistCard(
                    title = "Most Played",
                    icon = Icons.Default.Whatshot,
                    count = mostPlayedCount,
                    gradientColors = listOf(Color(0xFFFF6B35), KuhooPink),
                    modifier = Modifier.weight(1f),
                    onClick = { onDetailOpen(DetailScreen.LocalPlaylist("mostplayed")) }
                )
                LibraryPlaylistCard(
                    title = "Downloaded",
                    icon = Icons.Default.Download,
                    count = downloadedCount,
                    gradientColors = listOf(SpotifyGreen, KuhooTeal),
                    modifier = Modifier.weight(1f),
                    onClick = { onDetailOpen(DetailScreen.LocalPlaylist("downloaded")) }
                )
            }
            Spacer(Modifier.height(32.dp))
        }

        // Recently played preview
        if (recentlyPlayed.isNotEmpty()) {
            item {
                Text("Jump Back In", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite)
                Spacer(Modifier.height(12.dp))
            }
            items(recentlyPlayed) { track ->
                TrackListItem(track) { onDetailOpen(DetailScreen.LocalPlaylist("recent")) }
            }
        }

        // Empty state
        if (likedCount == 0 && recentCount == 0) {
            item {
                Spacer(Modifier.height(48.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LibraryMusic, null, Modifier.size(64.dp), tint = SpotifyLightGray)
                        Spacer(Modifier.height(16.dp))
                        Text("Start listening to build your library", fontSize = 16.sp, color = SpotifyLightGray)
                        Text("Your liked songs, history, and playlists will appear here", fontSize = 13.sp, color = SpotifyLightGray.copy(alpha = 0.7f))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
fun LibraryPlaylistCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(gradientColors))
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Icon(icon, title, Modifier.size(24.dp), tint = SpotifyWhite)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite, modifier = Modifier.weight(1f))
                    Text("$count", fontSize = 12.sp, color = SpotifyWhite.copy(alpha = 0.8f))
                }
            }
        }
    }
}

// =========================================================================
// LOCAL PLAYLIST SCREEN (liked, recent, mostplayed, downloaded)
// =========================================================================

@Composable
fun LocalPlaylistScreen(type: String, onTrackSelect: (TrackInfo) -> Unit, onBack: () -> Unit) {
    val musicRepository: MusicRepository = koinInject()
    var songs by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val title = when (type) {
        "liked" -> "Liked Songs"
        "recent" -> "Recently Played"
        "mostplayed" -> "Most Played"
        "downloaded" -> "Downloaded"
        else -> "Songs"
    }
    val icon = when (type) {
        "liked" -> Icons.Default.Favorite
        "recent" -> Icons.Default.History
        "mostplayed" -> Icons.Default.Whatshot
        "downloaded" -> Icons.Default.Download
        else -> Icons.Default.MusicNote
    }

    LaunchedEffect(type) {
        isLoading = true
        songs = when (type) {
            "liked" -> musicRepository.getLikedSongs()
            "recent" -> musicRepository.getRecentlyPlayed()
            "mostplayed" -> musicRepository.getMostPlayed()
            "downloaded" -> musicRepository.getDownloadedSongs()
            else -> emptyList()
        }
        isLoading = false
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(32.dp), tint = KuhooPurple)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite)
                    Text("${songs.size} songs", fontSize = 14.sp, color = SpotifyLightGray)
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KuhooPurple)
                }
            }
        } else if (songs.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("No songs yet", fontSize = 16.sp, color = SpotifyLightGray)
                }
            }
        } else {
            itemsIndexed(songs) { index, track ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .clickable { onTrackSelect(track) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${index + 1}", fontSize = 14.sp, color = SpotifyLightGray, modifier = Modifier.width(28.dp))
                    if (track.thumbnailUrl?.isNotEmpty() == true) {
                        AsyncImage(model = track.thumbnailUrl, contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(SpotifyMedGray),
                            contentAlignment = Alignment.Center) {
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
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

// =========================================================================
// ALBUM DETAIL SCREEN
// =========================================================================

@Composable
fun AlbumDetailScreen(browseId: String, onTrackSelect: (TrackInfo) -> Unit, onBack: () -> Unit) {
    val innerTubeService: InnerTubeService = koinInject()
    var albumDetail by remember { mutableStateOf<AlbumDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(browseId) {
        isLoading = true
        albumDetail = innerTubeService.getAlbumSongs(browseId)
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = KuhooPurple)
        }
    } else {
        albumDetail?.let { album ->
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Album header
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(280.dp)
                            .background(Brush.verticalGradient(listOf(KuhooPurple.copy(alpha = 0.4f), SpotifyBlack)))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            if (album.thumbnailUrl.isNotEmpty()) {
                                AsyncImage(model = album.thumbnailUrl, contentDescription = album.title,
                                    modifier = Modifier.size(180.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop)
                            }
                            Spacer(Modifier.width(24.dp))
                            Column {
                                Text("ALBUM", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SpotifyLightGray, letterSpacing = 2.sp)
                                Text(album.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite, maxLines = 2)
                                Text(album.artist, fontSize = 16.sp, color = SpotifyLightGray)
                                album.year?.let { Text("$it", fontSize = 14.sp, color = SpotifyLightGray) }
                                Text("${album.songs.size} songs", fontSize = 14.sp, color = SpotifyLightGray)
                            }
                        }
                    }
                }

                // Song list
                itemsIndexed(album.songs) { index, track ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onTrackSelect(track) }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}", fontSize = 14.sp, color = SpotifyLightGray, modifier = Modifier.width(28.dp))
                        if (track.thumbnailUrl?.isNotEmpty() == true) {
                            AsyncImage(model = track.thumbnailUrl, contentDescription = null,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(track.title, fontWeight = FontWeight.SemiBold, color = SpotifyWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, color = SpotifyLightGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (track.durationMs > 0) {
                            Text(formatTime(track.durationMs), fontSize = 12.sp, color = SpotifyLightGray)
                        }
                    }
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }
}

// =========================================================================
// PLAYLIST DETAIL SCREEN
// =========================================================================

@Composable
fun PlaylistDetailScreen(playlistId: String, onTrackSelect: (TrackInfo) -> Unit, onBack: () -> Unit) {
    val innerTubeService: InnerTubeService = koinInject()
    var playlistDetail by remember { mutableStateOf<PlaylistDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(playlistId) {
        isLoading = true
        playlistDetail = innerTubeService.getPlaylistSongs(playlistId)
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = KuhooPurple)
        }
    } else {
        playlistDetail?.let { playlist ->
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(280.dp)
                            .background(Brush.verticalGradient(listOf(KuhooTeal.copy(alpha = 0.3f), SpotifyBlack)))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            if (playlist.thumbnailUrl.isNotEmpty()) {
                                AsyncImage(model = playlist.thumbnailUrl, contentDescription = playlist.title,
                                    modifier = Modifier.size(180.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop)
                            } else {
                                Box(
                                    Modifier.size(180.dp).clip(RoundedCornerShape(8.dp))
                                        .background(Brush.linearGradient(listOf(KuhooTeal, KuhooPurple))),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.QueueMusic, null, Modifier.size(80.dp), tint = SpotifyWhite) }
                            }
                            Spacer(Modifier.width(24.dp))
                            Column {
                                Text("PLAYLIST", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SpotifyLightGray, letterSpacing = 2.sp)
                                Text(playlist.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite, maxLines = 2)
                                Text("${playlist.songs.size} songs", fontSize = 14.sp, color = SpotifyLightGray)
                            }
                        }
                    }
                }
                itemsIndexed(playlist.songs) { index, track ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onTrackSelect(track) }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}", fontSize = 14.sp, color = SpotifyLightGray, modifier = Modifier.width(28.dp))
                        if (track.thumbnailUrl?.isNotEmpty() == true) {
                            AsyncImage(model = track.thumbnailUrl, contentDescription = null,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(track.title, fontWeight = FontWeight.SemiBold, color = SpotifyWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, color = SpotifyLightGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (track.durationMs > 0) {
                            Text(formatTime(track.durationMs), fontSize = 12.sp, color = SpotifyLightGray)
                        }
                    }
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }
}

// =========================================================================
// ARTIST DETAIL SCREEN
// =========================================================================

@Composable
fun ArtistDetailScreen(browseId: String, onItemClick: (TrackInfo) -> Unit, onBack: () -> Unit) {
    val innerTubeService: InnerTubeService = koinInject()
    var artistDetail by remember { mutableStateOf<ArtistDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(browseId) {
        isLoading = true
        artistDetail = innerTubeService.getArtistDetail(browseId)
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = KuhooPurple)
        }
    } else {
        artistDetail?.let { artist ->
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Artist header
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(300.dp)
                            .background(Brush.verticalGradient(listOf(KuhooPink.copy(alpha = 0.3f), SpotifyBlack)))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            if (artist.thumbnailUrl.isNotEmpty()) {
                                AsyncImage(model = artist.thumbnailUrl, contentDescription = artist.name,
                                    modifier = Modifier.size(140.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop)
                                Spacer(Modifier.height(16.dp))
                            }
                            Text(artist.name, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite)
                            artist.subscriberCount?.let {
                                Text(it, fontSize = 14.sp, color = SpotifyLightGray)
                            }
                        }
                    }
                }

                // Artist sections (Songs, Albums, Singles, etc.)
                artist.sections.forEach { section ->
                    item {
                        Spacer(Modifier.height(24.dp))
                        Text(section.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite,
                            modifier = Modifier.padding(horizontal = 24.dp))
                        Spacer(Modifier.height(12.dp))
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp)
                        ) {
                            items(section.items.take(10)) { track ->
                                TrackCard(track, onItemClick)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }
}

// =========================================================================
// UTILITY
// =========================================================================

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
