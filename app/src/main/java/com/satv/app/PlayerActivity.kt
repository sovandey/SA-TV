package com.satv.app

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.DefaultDataSource
import com.satv.app.databinding.ActivityPlayerBinding

class PlayerActivity : AppCompatActivity() {
    private lateinit var b: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var names: ArrayList<String> = arrayListOf()
    private var urls: ArrayList<String> = arrayListOf()
    private var index = 0
    private var fullscreen = true

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        names = intent.getStringArrayListExtra("channel_names") ?: arrayListOf(intent.getStringExtra("channel_name") ?: "SA TV")
        urls = intent.getStringArrayListExtra("channel_urls") ?: arrayListOf(intent.getStringExtra("channel_url") ?: "")
        index = intent.getIntExtra("channel_index", 0).coerceIn(0, (urls.size - 1).coerceAtLeast(0))
        player = ExoPlayer.Builder(this).build()
        b.playerView.player = player
        b.playerView.useController = true
        b.buttonPrevious.setOnClickListener { playAt(index - 1) }
        b.buttonNext.setOnClickListener { playAt(index + 1) }
        b.buttonPlayerFullscreen.setOnClickListener { toggleFullscreen() }
        b.buttonBack.setOnClickListener { finish() }
        b.buttonPrevious.isEnabled = index > 0
        b.buttonNext.isEnabled = index < urls.lastIndex
        playAt(index)
        hideSystemBars()
        player?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                b.textError.visibility = View.VISIBLE
                b.textError.text = "এই চ্যানেলটি এখন চালানো যাচ্ছে না। অন্য চ্যানেল চেষ্টা করুন।"
            }
        })
    }

    private fun playAt(newIndex: Int) {
        if (newIndex !in urls.indices) return
        index = newIndex
        b.textChannelTitle.text = String.format("%03d  •  %s", index + 1, names.getOrElse(index) { "SA TV" })
        b.textError.visibility = View.GONE
        b.buttonPrevious.isEnabled = index > 0
        b.buttonNext.isEnabled = index < urls.lastIndex
        val url = urls[index]
        val source = HlsMediaSource.Factory(DefaultDataSource.Factory(this)).createMediaSource(MediaItem.fromUri(Uri.parse(url)))
        player?.setMediaSource(source)
        player?.prepare()
        player?.playWhenReady = true
    }

    private fun toggleFullscreen() {
        fullscreen = !fullscreen
        if (fullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            hideSystemBars()
            b.buttonPlayerFullscreen.text = "Exit full screen"
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            b.buttonPlayerFullscreen.text = "Full screen"
        }
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onStop() { super.onStop(); player?.pause() }
    override fun onDestroy() { super.onDestroy(); player?.release(); player = null }
}
