package com.banglatv.app

import android.content.pm.ActivityInfo
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

private val aspectLabels = listOf("Fit", "Zoom", "Stretch")
private val aspectModes = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    AspectRatioFrameLayout.RESIZE_MODE_FILL,
)

@Composable
fun PlayerScreen(vm: MainViewModel, channel: Channel) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val tv = remember { ctx.isTv() }

    var sourceIndex by remember(channel.key) { mutableIntStateOf(0) }
    var attempt by remember(channel.key) { mutableIntStateOf(0) }
    var ready by remember(channel.key) { mutableStateOf(false) }
    var failed by remember(channel.key) { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(true) }
    var aspect by remember { mutableIntStateOf(0) }
    var controls by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }

    val rootFocus = remember { FocusRequester() }
    val firstButtonFocus = remember { FocusRequester() }

    // ---- ExoPlayer -------------------------------------------------------------------------
    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(10_000)
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(8_000, 30_000, 1_500, 3_000)
                    .build(),
            )
            .build()
    }

    val fail: () -> Unit = {
        if (sourceIndex + 1 < channel.urls.size) {
            sourceIndex += 1
            Toast.makeText(ctx, "Trying source ${sourceIndex + 1}/${channel.urls.size}…", Toast.LENGTH_SHORT).show()
        } else {
            failed = true
        }
    }
    val currentFail by rememberUpdatedState(fail)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    ready = true
                    failed = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                currentFail()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(channel.key, sourceIndex, attempt) {
        ready = false
        failed = false
        buffering = true
        paused = false
        player.setMediaItem(MediaItem.fromUri(channel.urls[sourceIndex]))
        player.prepare()
        player.playWhenReady = true
        delay(20_000) // watchdog: if nothing plays within 20s, try the next source
        if (!ready) currentFail()
    }

    LaunchedEffect(channel.key) {
        banner = true
        delay(3_500)
        banner = false
    }

    // Pause when the app goes to background, resume when it comes back
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) player.pause()
            if (e == Lifecycle.Event.ON_START && !paused && !failed) player.play()
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    // ---- Full screen: hide system bars, landscape on phones, keep screen on -----------------
    DisposableEffect(fullscreen) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (fullscreen) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!tv) activity.requestedOrientation = if (fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // ---- Controls behaviour ------------------------------------------------------------------
    LaunchedEffect(controls, poke) {
        if (controls) {
            delay(5_000)
            controls = false
        }
    }
    LaunchedEffect(controls) {
        if (controls && tv) {
            delay(80)
            runCatching { firstButtonFocus.requestFocus() }
        } else if (!controls) {
            runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

    BackHandler(enabled = controls) { controls = false }

    fun step(delta: Int) {
        val q = vm.queue
        if (q.isEmpty()) return
        val i = q.indexOfFirst { it.key == channel.key }
        vm.nowPlaying = q[(i + delta + q.size).mod(q.size)]
    }

    val isFav = channel.key in vm.favorites

    // ---- UI ----------------------------------------------------------------------------------
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    controls = !controls
                    poke++
                })
            }
            .pointerInput(channel.key) {
                var dy = 0f
                detectVerticalDragGestures(
                    onDragStart = { dy = 0f },
                    onVerticalDrag = { _, d -> dy += d },
                    onDragEnd = {
                        if (dy < -160f) step(+1) else if (dy > 160f) step(-1)
                    },
                )
            }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                poke++
                when (e.key) {
                    Key.ChannelUp -> { step(+1); true }
                    Key.ChannelDown -> { step(-1); true }
                    Key.MediaPlayPause -> {
                        paused = !paused
                        player.playWhenReady = !paused
                        true
                    }
                    Key.DirectionUp -> if (!controls) { step(+1); true } else false
                    Key.DirectionDown -> if (!controls) { step(-1); true } else false
                    Key.DirectionLeft, Key.DirectionRight,
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Menu ->
                        if (!controls) { controls = true; true } else false
                    else -> false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                val exo = player
                PlayerView(c).apply {
                    useController = false
                    this.player = exo
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setKeepContentOnPlayerReset(false)
                }
            },
            update = { it.resizeMode = aspectModes[aspect] },
        )

        // Loading spinner
        if (buffering && !failed) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(color = Cyan)
                Text("Connecting…", color = Color.White, fontSize = 13.sp)
            }
        }

        // Error state
        if (failed) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC000000))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Channel not available right now", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("All ${channel.urls.size} source(s) failed", color = TextDim, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusButton(onClick = { sourceIndex = 0; attempt++ }) { Text("Retry", color = Color.White) }
                    FocusButton(onClick = { step(+1) }) { Text("Next channel", color = Color.White) }
                    FocusButton(onClick = { vm.nowPlaying = null }) { Text("Back", color = Color.White) }
                }
            }
        }

        // Channel banner (shown briefly when a channel starts)
        AnimatedVisibility(
            visible = banner && !controls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(20.dp),
        ) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xB3000000))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(channel.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(
                    "${channel.group}  •  ${if (tv) "OK = controls, ▲▼ = change channel" else "Tap = controls, swipe ↑↓ = change channel"}",
                    color = TextDim,
                    fontSize = 12.sp,
                )
            }
        }

        // Controls overlay
        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .systemBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FocusButton(onClick = { vm.nowPlaying = null }) {
                            Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                channel.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(channel.group, color = TextDim, fontSize = 12.sp)
                        }
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .systemBarsPadding()
                            .padding(16.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FocusButton(
                            onClick = { step(-1); poke++ },
                            modifier = Modifier.focusRequester(firstButtonFocus),
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, null, tint = Color.White)
                            Text("Prev", color = Color.White)
                        }
                        FocusButton(onClick = { step(+1); poke++ }) {
                            Icon(Icons.Filled.KeyboardArrowUp, null, tint = Color.White)
                            Text("Next", color = Color.White)
                        }
                        FocusButton(onClick = {
                            paused = !paused
                            player.playWhenReady = !paused
                            poke++
                        }) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
                            Text(if (paused) "Play" else "Pause", color = Color.White)
                        }
                        if (channel.urls.size > 1) {
                            FocusButton(onClick = {
                                sourceIndex = (sourceIndex + 1) % channel.urls.size
                                poke++
                            }) {
                                Text("Source ${sourceIndex + 1}/${channel.urls.size}", color = Color.White)
                            }
                        }
                        FocusButton(onClick = {
                            aspect = (aspect + 1) % aspectModes.size
                            poke++
                        }) {
                            Text("Screen: ${aspectLabels[aspect]}", color = Color.White)
                        }
                        FocusButton(onClick = { fullscreen = !fullscreen; poke++ }) {
                            Text(if (fullscreen) "Exit Fullscreen" else "Fullscreen", color = Color.White)
                        }
                        FocusButton(onClick = { vm.toggleFavorite(channel); poke++ }) {
                            Icon(
                                if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                "Favorite",
                                tint = if (isFav) Pink else Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}
