package com.satv.app
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
class PlayerActivity:AppCompatActivity(){
    private lateinit var b:ActivityPlayerBinding
    private var player:ExoPlayer?=null
    override fun onCreate(s:Bundle?){ super.onCreate(s); WindowCompat.setDecorFitsSystemWindows(window,false); b=ActivityPlayerBinding.inflate(layoutInflater); setContentView(b.root); hide()
        val name=intent.getStringExtra("channel_name")?:"SA TV"; val url=intent.getStringExtra("channel_url")?:return
        b.textChannelTitle.text=name
        player=ExoPlayer.Builder(this).build(); b.playerView.player=player; b.playerView.useController=true
        val src=HlsMediaSource.Factory(DefaultDataSource.Factory(this)).createMediaSource(MediaItem.fromUri(Uri.parse(url)))
        player?.setMediaSource(src); player?.prepare(); player?.playWhenReady=true
        player?.addListener(object:Player.Listener{ override fun onPlayerError(e:PlaybackException){ b.textError.visibility=View.VISIBLE; b.textError.text=getString(R.string.play_error) } }) }
    private fun hide(){ val c=WindowInsetsControllerCompat(window,window.decorView); c.hide(WindowInsetsCompat.Type.systemBars()); c.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
    override fun onStop(){ super.onStop(); player?.pause() }
    override fun onDestroy(){ super.onDestroy(); player?.release(); player=null }
}
