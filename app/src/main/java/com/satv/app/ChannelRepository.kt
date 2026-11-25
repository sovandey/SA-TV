package com.satv.app
import android.content.Context
object ChannelRepository {
    private var cached: List<Channel>? = null
    fun getChannels(context: Context): List<Channel> { if(cached==null) cached=M3UParser.parse(context); return cached ?: emptyList() }
    fun refresh(context: Context): List<Channel> { cached=M3UParser.parse(context); return cached ?: emptyList() }
}
