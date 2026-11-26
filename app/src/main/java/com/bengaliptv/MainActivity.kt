package com.bengaliptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.work.*
import com.bengaliptv.work.PlaylistMonitorWorker
import com.bengaliptv.data.Channel
import com.bengaliptv.data.M3uParser
import com.bengaliptv.data.PlaylistSource
import java.util.concurrent.TimeUnit

private val Bg = Color(0xFF101827)
private val Panel = Color(0xFF192436)
private val PanelAlt = Color(0xFF202D42)
private val Accent = Color(0xFF13C5D4)
private val Muted = Color(0xFF9AA8BC)
private val LiveRed = Color(0xFFE84F70)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(25, 35, 53)
        window.navigationBarColor = android.graphics.Color.rgb(25, 35, 53)

        val request = PeriodicWorkRequestBuilder<PlaylistMonitorWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "satv_playlist_monitor", ExistingPeriodicWorkPolicy.KEEP, request
        )
        val playlistText = assets.open("bengali-51-channels.m3u").bufferedReader().use { it.readText() }
        val channels = M3uParser.parse(
            playlistText, PlaylistSource("Bengali 51 Channels", "local://bengali-51-channels.m3u", 0)
        ).distinctBy { it.url }.filter { channelCategory(it) != null }
        setContent { SATVHome(channels) }
    }
}

private data class Fixture(
    val sport: String,
    val competition: String,
    val left: String,
    val right: String,
    val leftMark: String,
    val rightMark: String,
    val timer: String,
    val hot: Boolean = false
)

private val sampleFixtures = listOf(
    Fixture("🏏", "CRICKET | MEN'S ASIA CUP RISING STARS 2026", "IND-A", "SL-A", "🇮🇳", "🇱🇰", "02:34:32", true),
    Fixture("🏏", "CRICKET | WOMEN'S BIG BASH LEAGUE", "WBBL", "WBBL", "🏏", "🏏", "04:24:32"),
    Fixture("🏏", "CRICKET | ITALIA T10 2026", "Italia T10", "Italia T10", "🇮🇹", "🇮🇹", "01:34:32"),
    Fixture("🏆", "FOOTBALL | AFC CHAMPIONS LEAGUE TWO", "Svay Rieng", "Machida", "⚽", "🔵", "00:19:33"),
    Fixture("🏆", "FOOTBALL | AFC CHAMPIONS LEAGUE TWO", "Tai Po", "Adelaide United", "🟢", "🔵", "LIVE")
)

private fun channelCategory(channel: Channel): String? {
    val id = channel.id.orEmpty().lowercase()
    val country = channel.country.orEmpty().uppercase()
    val name = channel.name.lowercase()
    val banglaTerms = listOf("bangla", "bengali", "ananda", "kolkata", "jalsha", "rupasi",
        "ruposhi", "aakaash", "sangeet", "sananda", "tara tv", "mon bangla", "rongeen",
        "raatdin", "zillarbarta", "amar bangla")
    return when {
        country == "BD" || id.endsWith(".bd") || id.contains(".bd@") -> "Bangladesh"
        id.startsWith("ddnational") || name == "dd national hd" -> "Hindi"
        country == "CA" -> null
        channel.language.equals("ben", ignoreCase = true) -> "Indian Bengali"
        banglaTerms.any { name.contains(it) } -> "Indian Bengali"
        id.contains(".in") && name != "news live" -> "Indian Bengali"
        else -> null
    }
}

@Composable
private fun SATVHome(channels: List<Channel>) {
    var selectedSport by remember { mutableStateOf("Indian Bengali") }
    var searchMode by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var playingChannel by remember { mutableStateOf<Channel?>(null) }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, background = Bg, surface = Panel,
        onBackground = Color.White, onSurface = Color.White
    )) {
        if (playingChannel != null) {
            val activeList = channels.filter { channelCategory(it) == selectedSport }
            LivePlayerScreen(
                channels = activeList,
                initialChannel = playingChannel!!,
                onBack = { playingChannel = null },
                onChannelChanged = { playingChannel = it }
            )
        } else Column(Modifier.fillMaxSize().background(Bg)) {
            Column(
                Modifier.fillMaxWidth().background(Color(0xFF192337))
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (searchMode) {
                        OutlinedTextField(
                            value = searchText, onValueChange = { searchText = it },
                            placeholder = { Text("Search events", color = Muted) },
                            singleLine = true, modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent, unfocusedBorderColor = Muted,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White
                            )
                        )
                    } else {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.satv_logo),
                                contentDescription = "SA-TV official logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    "SA-TV",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "IDEAS. STORIES. IMPACT.",
                                    color = Color(0xFFE5B85C),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Text("•", color = Color.Transparent, fontSize = 22.sp)
                    HeaderAction("↗") { searchMode = false }
                    Spacer(Modifier.width(18.dp))
                    HeaderAction("⟳") { searchText = ""; selectedSport = "Indian Bengali" }
                    Spacer(Modifier.width(18.dp))
                    HeaderAction(if (searchMode) "×" else "⌕") {
                        searchMode = !searchMode
                        if (!searchMode) searchText = ""
                    }
                }
            }

            Text(
                "  Update to the latest version for the best experience.  |  SA-TV",
                color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Clip,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Panel).padding(vertical = 12.dp)
            )

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = 14.dp, top = 7.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val cats = listOf("Indian Bengali" to "🇮🇳", "Bangladesh" to "🇧🇩", "Hindi" to "🇮🇳", "Sports" to "🏆")
                cats.forEach { (name, icon) ->
                    val n = channels.count { channelCategory(it) == name }
                    if (n > 0) CategoryChip(icon, "$name ($n)", selectedSport == name) { selectedSport = name }
                }
            }

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp)
            ) {
                val categoryChannels = channels.filter { channelCategory(it) == selectedSport }
                    .filter { searchText.isBlank() || it.name.contains(searchText, true) }
                if (selectedSport == "Sports") {
                    Text(
                        "No sports channels were found in bengali-51-channels.m3u.",
                        color = Muted, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                } else if (categoryChannels.isEmpty()) {
                    Text("No channels found in this category.", color = Muted, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(24.dp))
                } else {
                    Text("${categoryChannels.size} CHANNELS", color = Muted, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp))
                    categoryChannels.forEach { channel ->
                        ChannelCard(channel, selectedSport) { playingChannel = channel }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

        }
    }
}

@Composable
private fun HeaderAction(symbol: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(32.dp)) {
        Text(symbol, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun CategoryChip(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Color(0xFF172D3D) else Panel,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Accent else PanelAlt),
        modifier = Modifier.padding(end = 10.dp).height(76.dp).width(132.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(icon, fontSize = 25.sp)
            Text(
                label, color = Color.White, fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChannelCard(channel: Channel, category: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF293A52))
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF26364D)),
                contentAlignment = Alignment.Center) {
                Text(if (category == "Bangladesh") "🇧🇩" else if (category == "Hindi") "🇮🇳" else "📺",
                    fontSize = 23.sp)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(channel.name, color = Color.White, fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(channel.group?.takeIf { it.isNotBlank() } ?: category,
                    color = Muted, fontSize = 11.sp, maxLines = 1)
            }
            Text("›", color = Accent, fontSize = 25.sp)
        }
    }
}


@Composable
private fun LivePlayerScreen(
    channels: List<Channel>,
    initialChannel: Channel,
    onBack: () -> Unit,
    onChannelChanged: (Channel) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember {
        val http = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context).setDataSourceFactory(http))
            .build()
    }
    val exoPlayer = player
    var currentChannel by remember { mutableStateOf(initialChannel) }
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var fitMode by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var videoRatio by remember { mutableStateOf(0f) }

    LaunchedEffect(currentChannel.url) {
        errorText = null
        try {
            exoPlayer.setMediaItem(MediaItem.fromUri(currentChannel.url))
            exoPlayer.volume = if (muted) 0f else 1f
            exoPlayer.prepare()
        } catch (e: Exception) {
            errorText = "Stream could not be played. Try another channel."
        }
        exoPlayer.playWhenReady = true
        playing = true
    }
    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                errorText = "Stream could not be played. Try another channel."
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

    val currentIndex = channels.indexOfFirst { it.url == currentChannel.url }.coerceAtLeast(0)
    fun changeChannel(step: Int) {
        if (channels.isEmpty()) return
        val nextIndex = (currentIndex + step + channels.size) % channels.size
        currentChannel = channels[nextIndex]
        onChannelChanged(currentChannel)
        controlsVisible = true
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { viewContext ->
                val frame = AspectRatioFrameLayout(viewContext)
                val surface = android.view.SurfaceView(viewContext)
                frame.addView(surface, android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT))
                exoPlayer.setVideoSurfaceView(surface)
                val holder = android.widget.FrameLayout(viewContext)
                holder.addView(frame, android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.Gravity.CENTER))
                holder
            },
            update = { holder ->
                val frame = holder.getChildAt(0) as AspectRatioFrameLayout
                frame.resizeMode = if (fitMode) AspectRatioFrameLayout.RESIZE_MODE_FIT
                    else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                if (videoRatio > 0f) frame.setAspectRatio(videoRatio)
            },
            modifier = Modifier.fillMaxSize()
        )

        if (controlsVisible || locked) {
            Box(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Color(0x99071320)).padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", color = Color.White, fontSize = 42.sp,
                        modifier = Modifier.clickable { onBack() }.padding(end = 14.dp))
                    Text(currentChannel.name, color = Color.White, fontSize = 23.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(if (muted) "🔇" else "🔊", fontSize = 27.sp,
                        modifier = Modifier.clickable {
                            muted = !muted
                            exoPlayer.volume = if (muted) 0f else 1f
                        }.padding(horizontal = 10.dp))
                    Text(if (locked) "🔒" else "🔓", fontSize = 27.sp,
                        modifier = Modifier.clickable { locked = !locked }.padding(start = 8.dp))
                }
            }
            if (!locked) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Color(0x99071320)).padding(horizontal = 18.dp, vertical = 18.dp)
                ) {
                    if (errorText != null) {
                        Text(errorText!!, color = Color.White, fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        PlayerControl("⏮", "Previous channel") { changeChannel(-1) }
                        PlayerControl(if (playing) "Ⅱ" else "▶", "Play or pause") {
                            if (playing) exoPlayer.pause() else exoPlayer.play()
                            playing = !playing
                        }
                        PlayerControl("⏭", "Next channel") { changeChannel(1) }
                        PlayerControl(if (fitMode) "▣" else "⛶", "Resize video") {
                            fitMode = !fitMode
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("LIVE", color = Color(0xFFFF496D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("${currentIndex + 1} / ${channels.size}   •   Next channel ⏭",
                            color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        } else {
            Text("▶", color = Color.White, fontSize = 28.sp,
                modifier = Modifier.align(Alignment.Center).clickable {
                    controlsVisible = true
                })
        }

        if (locked) {
            Text("🔒", color = Color.White, fontSize = 22.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
                    .clickable { locked = false; controlsVisible = true })
        }
    }
}

@Composable
private fun PlayerControl(symbol: String, description: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(symbol, color = Color.White, fontSize = 35.sp, fontWeight = FontWeight.Bold)
        Text(description, color = Color(0xFFCBD5E1), fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun FixtureCard(fixture: Fixture) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF293A52))
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fixture.sport, fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                Text(fixture.competition, color = Color.White, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                if (fixture.hot) {
                    Surface(color = Accent, shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)) {
                        Text(" HOT ", color = Color.White, fontWeight = FontWeight.Bold,
                            fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TeamLabel(fixture.leftMark, fixture.left, Modifier.weight(1f), true)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(66.dp)) {
                    Text("Live", color = LiveRed, fontSize = 12.sp)
                }
                TeamLabel(fixture.rightMark, fixture.right, Modifier.weight(1f), false)
            }
            Spacer(Modifier.height(12.dp))
            Text(fixture.timer, color = Color.White, fontSize = 17.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun TeamLabel(mark: String, name: String, modifier: Modifier, left: Boolean) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (left) Arrangement.Start else Arrangement.End) {
        if (left) TeamMark(mark)
        Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp))
        if (!left) TeamMark(mark)
    }
}

@Composable
private fun TeamMark(mark: String) {
    Box(
        Modifier.size(43.dp).clip(CircleShape).background(Color(0xFFEEF2F7)),
        contentAlignment = Alignment.Center
    ) { Text(mark, fontSize = 24.sp) }
}
