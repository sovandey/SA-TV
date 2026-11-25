package com.banglatv.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

const val DEFAULT_PLAYLIST_URL = "https://iptv-org.github.io/iptv/languages/ben.m3u"
const val ONLINE_GROUP = "Online"

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("banglatv", 0)
    private val cacheFile = File(app.cacheDir, "online.m3u")

    private var builtIn: List<Channel> = emptyList()
    private var online: List<Channel> = emptyList()

    var channels by mutableStateOf<List<Channel>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var favorites by mutableStateOf(prefs.getStringSet("favs", emptySet()) ?: emptySet()); private set

    var query by mutableStateOf("")
    var selectedGroup by mutableStateOf("All")

    var playlistUrl by mutableStateOf(prefs.getString("url", DEFAULT_PLAYLIST_URL) ?: DEFAULT_PLAYLIST_URL)
        private set
    var includeOnline by mutableStateOf(prefs.getBoolean("online", true))
        private set

    var nowPlaying by mutableStateOf<Channel?>(null)
    var queue: List<Channel> = emptyList()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            builtIn = runCatching {
                getApplication<Application>().assets.open("channels.m3u").bufferedReader().readText()
            }.map { M3u.parse(it) }.getOrDefault(emptyList())
            if (cacheFile.exists()) {
                online = runCatching { M3u.parse(cacheFile.readText(), ONLINE_GROUP) }.getOrDefault(emptyList())
            }
            withContext(Dispatchers.Main) { rebuild() }
            if (includeOnline) refresh()
        }
    }

    private fun rebuild() {
        channels = if (includeOnline) M3u.merge(builtIn, online) else builtIn
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = download(playlistUrl)
                    cacheFile.writeText(text)
                    M3u.parse(text, ONLINE_GROUP)
                }
            }
            result.onSuccess {
                online = it
                message = "Playlist updated (${it.size} online channels)"
            }.onFailure {
                message = "Could not update online list (${it.message ?: "network error"})"
            }
            rebuild()
            loading = false
        }
    }

    fun saveSettings(url: String, include: Boolean) {
        playlistUrl = url.trim().ifEmpty { DEFAULT_PLAYLIST_URL }
        includeOnline = include
        prefs.edit().putString("url", playlistUrl).putBoolean("online", include).apply()
        rebuild()
        if (include) refresh()
    }

    fun toggleFavorite(c: Channel) {
        val set = favorites.toMutableSet()
        if (!set.add(c.key)) set.remove(c.key)
        favorites = set
        prefs.edit().putStringSet("favs", set).apply()
    }

    private fun download(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 25000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) BanglaTV/1.0")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }
}
