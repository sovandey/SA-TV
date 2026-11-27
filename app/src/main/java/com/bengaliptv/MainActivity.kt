package com.bengaliptv

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.work.*
import coil.compose.SubcomposeAsyncImage
import com.bengaliptv.data.Channel
import com.bengaliptv.data.M3uParser
import com.bengaliptv.data.PlaylistSource
import com.bengaliptv.data.RemoteSources
import com.bengaliptv.work.PlaylistMonitorWorker
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

private val Bg = Color(0xFF101827)
private val Panel = Color(0xFF192436)
private val PanelAlt = Color(0xFF202D42)
private val Accent = Color(0xFF13C5D4)
private val Muted = Color(0xFF9AA8BC)
private val LiveRed = Color(0xFFE84F70)

private val categories = listOf(
    "Indian Bengali" to "🇮🇳",
    "Bangladesh" to "🇧🇩",
    "Hindi" to "🎬",
    "Sports" to "🏆",
    "FM Radio" to "📻",
    "Kids" to "🧸"
)
private val remoteGroups = setOf("Hindi", "Sports", "FM Radio", "Kids")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        val request = PeriodicWorkRequestBuilder<PlaylistMonitorWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "satv_playlist_monitor", ExistingPeriodicWorkPolicy.KEEP, request
        )
        val base = loadBundled()
        setContent { SATVApp(base) }
    }

    private fun loadBundled(): List<Channel> {
        val all = mutableListOf<Channel>()
        for (file in listOf("bengali-51-channels.m3u", "master.m3u")) {
            try {
                val text = assets.open(file).bufferedReader().use { it.readText() }
                all += M3uParser.parse(text, PlaylistSource(file, "local://$file", 0))
            } catch (e: Exception) {
                // skip missing asset
            }
        }
        return all.distinctBy { it.url }
            .distinctBy { it.name.lowercase().trim() }
            .filter { channelCategory(it) != null }
    }
}

private fun channelCategory(channel: Channel): String? {
    val group = channel.group.orEmpty()
    if (group in remoteGroups) return group
    val id = channel.id.orEmpty().lowercase()
    val country = channel.country.orEmpty().uppercase()
    val name = channel.name.lowercase()
    val banglaTerms = listOf("bangla", "bengali", "ananda", "kolkata", "jalsha", "rupasi",
        "ruposhi", "aakaash", "sangeet", "sananda", "tara tv", "mon bangla", "rongeen",
        "raatdin", "zillarbarta", "amar bangla")
    return when {
        group.equals("Bengali Kids", true) || name.contains("cartoon") -> "Kids"
        group.equals("Bangladesh", true) || country == "BD" || id.endsWith(".bd") || id.contains(".bd@") -> "Bangladesh"
        id.startsWith("ddnational") || name == "dd national hd" -> "Hindi"
        country == "CA" -> null
        channel.language.equals("ben", ignoreCase = true) -> "Indian Bengali"
        banglaTerms.any { name.contains(it) } -> "Indian Bengali"
        group.startsWith("Bengali", true) -> "Indian Bengali"
        id.contains(".in") && name != "news live" -> "Indian Bengali"
        else -> null
    }
}

private fun categoryEmoji(cat: String?): String = when (cat) {
    "Sports" -> "🏆"
    "FM Radio" -> "📻"
    "Kids" -> "🧸"
    else -> "📺"
}

// ───────────────────────── App shell + splash ─────────────────────────

@Composable
private fun SATVApp(base: List<Channel>) {
    val context = LocalContext.current
    var splash by remember { mutableStateOf(true) }
    var remote by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true
        val acc = mutableListOf<Channel>()
        RemoteSources.loadAll(context.applicationContext) { r ->
            acc += r
            remote = acc.toList()
        }
        loading = false
    }
    LaunchedEffect(Unit) {
        delay(2300)
        splash = false
    }
    val channels = remember(base, remote) { (base + remote).distinctBy { it.url } }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, background = Bg, surface = Panel,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Crossfade(targetState = splash, animationSpec = tween(600), label = "splash") { isSplash ->
            if (isSplash) SplashScreen()
            else SATVHome(channels, loading) { reloadKey++ }
        }
    }
}

@Composable
private fun SplashScreen() {
    val scale = remember { Animatable(0.9f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(1500)) }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0F18)), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(id = R.drawable.satv_logo),
            contentDescription = "SA-TV",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
        )
    }
}

// ───────────────────────── Home ─────────────────────────

@Composable
private fun SATVHome(channels: List<Channel>, loading: Boolean, onReload: () -> Unit) {
    var selectedSport by remember { mutableStateOf("Indian Bengali") }
    var searchMode by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var playingChannel by remember { mutableStateOf<Channel?>(null) }

    val counts = remember(channels) {
        categories.associate { (n, _) -> n to channels.count { channelCategory(it) == n } }
    }
    val categoryList = remember(channels, selectedSport) {
        channels.filter { channelCategory(it) == selectedSport }
    }

    val current = playingChannel
    if (current != null) {
        LivePlayerScreen(
            channels = categoryList,
            initialChannel = current,
            onBack = { playingChannel = null },
            onChannelChanged = { playingChannel = it }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Column(
            Modifier.fillMaxWidth().background(Color(0xFF192337)).statusBarsPadding()
                .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (searchMode) {
                    OutlinedTextField(
                        value = searchText, onValueChange = { searchText = it },
                        placeholder = { Text("Search channels", color = Muted) },
                        singleLine = true, modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = Muted,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White
                        )
                    )
                } else {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.satv_logo),
                            contentDescription = "SA-TV official logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(verticalArrangement = Arrangement.Center) {
                            Text("SA-TV", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("IDEAS. STORIES. IMPACT.", color = Color(0xFFE5B85C),
                                fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                HeaderAction("⟳") { onReload() }
                Spacer(Modifier.width(10.dp))
                HeaderAction(if (searchMode) "×" else "⌕") {
                    searchMode = !searchMode
                    if (!searchMode) searchText = ""
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEach { (name, icon) ->
                CategoryChip(icon, name, counts[name] ?: 0, selectedSport == name) { selectedSport = name }
            }
        }

        val shown = remember(categoryList, searchText) {
            categoryList.filter { searchText.isBlank() || it.name.contains(searchText, true) }
        }
        if (shown.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (loading && selectedSport in remoteGroups && searchText.isBlank()) {
                    CircularProgressIndicator(color = Accent)
                    Spacer(Modifier.height(12.dp))
                    Text("Loading channels…", color = Muted, fontSize = 14.sp)
                } else {
                    Text(
                        if (searchText.isNotBlank()) "No channel matches your search."
                        else "No channels loaded. Check your internet and tap ⟳.",
                        color = Muted, fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                item {
                    Text(
                        "${shown.size} ${if (selectedSport == "FM Radio") "STATIONS" else "CHANNELS"}",
                        color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 4.dp)
                    )
                }
                items(shown, key = { it.url }) { channel ->
                    ChannelCard(channel, false) { playingChannel = channel }
                }
                item { Spacer(Modifier.navigationBarsPadding().height(12.dp)) }
            }
        }
    }
}

@Composable
private fun HeaderAction(symbol: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(38.dp)) {
        Text(symbol, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun CategoryChip(icon: String, label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) Color(0xFF172D3D) else Panel,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Accent else PanelAlt),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                label, color = Color.White, fontSize = 13.sp, maxLines = 1,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
            Spacer(Modifier.width(6.dp))
            Text("$count", color = if (selected) Accent else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ChannelCard(channel: Channel, highlight: Boolean, onClick: () -> Unit) {
    val emoji = categoryEmoji(channelCategory(channel))
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (highlight) Color(0xFF173043) else Panel),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (highlight) Accent else Color(0xFF293A52))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFEEF2F7)),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logo.isNullOrBlank()) {
                    Text(emoji, fontSize = 22.sp)
                } else {
                    SubcomposeAsyncImage(
                        model = channel.logo,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        loading = { Text(emoji, fontSize = 22.sp) },
                        error = { Text(emoji, fontSize = 22.sp) }
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(channel.name, color = Color.White, fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (highlight) "Now playing"
                    else channel.group?.takeIf { it.isNotBlank() } ?: (channelCategory(channel) ?: ""),
                    color = if (highlight) Accent else Muted, fontSize = 11.sp, maxLines = 1
                )
            }
            Text(if (highlight) "●" else "›", color = Accent, fontSize = if (highlight) 14.sp else 24.sp)
        }
    }
}

// ───────────────────────── Player ─────────────────────────

@Composable
private fun LivePlayerScreen(
    channels: List<Channel>,
    initialChannel: Channel,
    onBack: () -> Unit,
    onChannelChanged: (Channel) -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity
    val view = LocalView.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val exoPlayer = remember {
        val http = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context).setDataSourceFactory(http)
            )
            .build()
    }

    var current by remember { mutableStateOf(initialChannel) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsTick by remember { mutableStateOf(0) }
    var muted by remember { mutableStateOf(false) }
    var fit by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var videoRatio by remember { mutableStateOf(0f) }

    val isRadio = current.group == "FM Radio"
    val index = channels.indexOfFirst { it.url == current.url }.coerceAtLeast(0)

    fun switchTo(ch: Channel) {
        current = ch
        onChannelChanged(ch)
        controlsVisible = true
        controlsTick++
    }
    fun step(delta: Int) {
        if (channels.isEmpty()) return
        switchTo(channels[(index + delta + channels.size) % channels.size])
    }
    fun toggleOrientation() {
        activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    // stream loading
    LaunchedEffect(current.url) {
        errorText = null
        buffering = true
        videoRatio = 0f
        try {
            exoPlayer.setMediaItem(MediaItem.fromUri(current.url))
            exoPlayer.volume = if (muted) 0f else 1f
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } catch (e: Exception) {
            errorText = "Stream could not be played. Try another channel."
        }
    }

    // listeners + cleanup
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                errorText = "This stream is not available right now."
                buffering = false
                controlsVisible = true
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) errorText = null
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playing = playWhenReady
            }
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0)
                    videoRatio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // pause when app goes to background
    DisposableEffect(Unit) {
        var wasPlaying = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                wasPlaying = exoPlayer.playWhenReady
                exoPlayer.pause()
            } else if (event == Lifecycle.Event.ON_START && wasPlaying) {
                exoPlayer.play()
            }
        }
        (context as androidx.activity.ComponentActivity).lifecycle.addObserver(observer)
        onDispose { (context as androidx.activity.ComponentActivity).lifecycle.removeObserver(observer) }
    }

    // screen on + rotation allowed while player is open
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose {
            view.keepScreenOn = false
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.getInsetsController(activity.window, view).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // immersive fullscreen in landscape
    LaunchedEffect(landscape) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        if (landscape) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // auto-hide controls
    LaunchedEffect(controlsVisible, controlsTick, playing) {
        if (controlsVisible && playing) {
            delay(4000)
            controlsVisible = false
        }
    }

    BackHandler {
        if (landscape) toggleOrientation() else onBack()
    }

    val stage: @Composable (Modifier) -> Unit = { mod ->
        Box(
            mod.background(Color.Black).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) {
                controlsVisible = !controlsVisible
                controlsTick++
            }
        ) {
            if (isRadio) RadioArt() else VideoSurface(exoPlayer, fit, videoRatio, Modifier.fillMaxSize())

            if (buffering && errorText == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(46.dp), color = Accent)
            }
            if (errorText != null) {
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(errorText!!, color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        errorText = null
                        buffering = true
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    }) { Text("Retry") }
                }
            }

            if (controlsVisible) {
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(listOf(Color(0xB3000000), Color(0x00000000), Color(0xB3000000)))
                    )
                ) {
                    Box(
                        Modifier.fillMaxSize()
                            .then(if (landscape) Modifier.displayCutoutPadding() else Modifier)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        // top bar
                        Row(
                            Modifier.align(Alignment.TopStart).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircleBtn(40.dp, Color(0x66000000), onClick = {
                                if (landscape) toggleOrientation() else onBack()
                            }) { GlyphIcon("back", Modifier.size(22.dp)) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(current.name, color = Color.White, fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (isRadio) "FM Radio" else (channelCategory(current) ?: ""),
                                    color = Color(0xFFCBD5E1), fontSize = 11.sp, maxLines = 1)
                            }
                            CircleBtn(40.dp, Color(0x66000000), onClick = {
                                muted = !muted
                                exoPlayer.volume = if (muted) 0f else 1f
                            }) { Text(if (muted) "🔇" else "🔊", fontSize = 18.sp) }
                        }
                        // centre controls
                        Row(
                            Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(26.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircleBtn(48.dp, Color(0x66000000), onClick = { step(-1) }) {
                                GlyphIcon("prev", Modifier.size(26.dp))
                            }
                            CircleBtn(68.dp, Accent, onClick = {
                                if (playing) exoPlayer.pause() else {
                                    if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
                                    exoPlayer.play()
                                }
                                controlsTick++
                            }) { GlyphIcon(if (playing) "pause" else "play", Modifier.size(38.dp)) }
                            CircleBtn(48.dp, Color(0x66000000), onClick = { step(1) }) {
                                GlyphIcon("next", Modifier.size(26.dp))
                            }
                        }
                        // bottom bar
                        Row(
                            Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(LiveRed))
                            Spacer(Modifier.width(6.dp))
                            Text("LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(12.dp))
                            Text("${index + 1} / ${channels.size}", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            if (!isRadio) {
                                Surface(
                                    onClick = { fit = !fit; controlsTick++ },
                                    shape = RoundedCornerShape(50),
                                    color = Color(0x66000000)
                                ) {
                                    Text(if (fit) "FIT" else "FILL", color = Color.White, fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                            }
                            CircleBtn(40.dp, Color(0x66000000), onClick = { toggleOrientation() }) {
                                GlyphIcon(if (landscape) "exit" else "full", Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (landscape) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            stage(Modifier.fillMaxSize())
        }
    } else {
        Column(Modifier.fillMaxSize().background(Bg)) {
            Box(Modifier.fillMaxWidth().background(Color.Black).statusBarsPadding()) {
                stage(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(current.name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(LiveRed))
                    Spacer(Modifier.width(6.dp))
                    Text("LIVE  •  ${channelCategory(current) ?: ""}", color = Muted, fontSize = 12.sp)
                }
            }
            Text(
                if (isRadio) "MORE STATIONS" else "MORE CHANNELS", color = Muted, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 18.dp, bottom = 4.dp)
            )
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                items(channels, key = { it.url }) { ch ->
                    ChannelCard(ch, ch.url == current.url) { switchTo(ch) }
                }
                item { Spacer(Modifier.navigationBarsPadding().height(12.dp)) }
            }
        }
    }
}

@Composable
private fun VideoSurface(player: ExoPlayer, fit: Boolean, ratio: Float, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val frame = AspectRatioFrameLayout(ctx)
            val surface = android.view.SurfaceView(ctx)
            frame.addView(
                surface,
                android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            player.setVideoSurfaceView(surface)
            val holder = android.widget.FrameLayout(ctx)
            holder.addView(
                frame,
                android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.Gravity.CENTER
                )
            )
            holder
        },
        update = { holder ->
            val frame = holder.getChildAt(0) as AspectRatioFrameLayout
            frame.resizeMode = if (fit) AspectRatioFrameLayout.RESIZE_MODE_FIT
            else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            if (ratio > 0f) frame.setAspectRatio(ratio)
        }
    )
}

@Composable
private fun RadioArt() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF16324A), Color(0xFF0B1220)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📻", fontSize = 56.sp)
            Spacer(Modifier.height(6.dp))
            Text("LIVE RADIO", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CircleBtn(
    size: androidx.compose.ui.unit.Dp,
    bg: Color,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg).clickable { onClick() },
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun GlyphIcon(kind: String, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.09f
        when (kind) {
            "play" -> {
                val p = Path().apply {
                    moveTo(w * .32f, h * .2f); lineTo(w * .32f, h * .8f); lineTo(w * .82f, h * .5f); close()
                }
                drawPath(p, color)
            }
            "pause" -> {
                drawRect(color, Offset(w * .27f, h * .2f), Size(w * .16f, h * .6f))
                drawRect(color, Offset(w * .57f, h * .2f), Size(w * .16f, h * .6f))
            }
            "next" -> {
                val p = Path().apply {
                    moveTo(w * .2f, h * .22f); lineTo(w * .2f, h * .78f); lineTo(w * .68f, h * .5f); close()
                }
                drawPath(p, color)
                drawRect(color, Offset(w * .72f, h * .22f), Size(w * .09f, h * .56f))
            }
            "prev" -> {
                val p = Path().apply {
                    moveTo(w * .8f, h * .22f); lineTo(w * .8f, h * .78f); lineTo(w * .32f, h * .5f); close()
                }
                drawPath(p, color)
                drawRect(color, Offset(w * .19f, h * .22f), Size(w * .09f, h * .56f))
            }
            "back" -> {
                drawLine(color, Offset(w * .62f, h * .2f), Offset(w * .32f, h * .5f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * .32f, h * .5f), Offset(w * .62f, h * .8f), sw, StrokeCap.Round)
            }
            "full" -> {
                drawLine(color, Offset(w * .2f, h * .42f), Offset(w * .2f, h * .2f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * .2f, h * .2f), Offset(w * .42f, h * .2f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * .58f, h * .2f), Offset(w * .8f, h * .2f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * .8f, h * .2f), Offset(w * .8f, h * .42f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * .2f, h * .58f), Offset(w * .2f, h * .8f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * .2f, h * .8f), Offset(w * .42f, h * .8f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * .58f, h * .8f), Offset(w * .8f, h * .8f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * .8f, h * .8f), Offset(w * .8f, h * .58f), sw, StrokeCap.Round)
            }
            "exit" -> {
                drawLine(color, Offset(w * .4f, h * .18f), Offset(w * .4f, h * .4f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * .4f, h * .4f), Offset(w * .18f, h * .4f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * .6f, h * .18f), Offset(w * .6f, h * .4f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * .6f, h * .4f), Offset(w * .82f, h * .4f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * .4f, h * .82f), Offset(w * .4f, h * .6f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * .4f, h * .6f), Offset(w * .18f, h * .6f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * .6f, h * .82f), Offset(w * .6f, h * .6f), sw, StrokeCap.Round)
                drawLine(color, Offset(w * .6f, h * .6f), Offset(w * .82f, h * .6f), sw, StrokeCap.Round)
            }
        }
    }
}
