package com.bengaliptv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

/** Loads Sports / Kids / Hindi TV and FM radio lists from the internet (cached for offline use). */
object RemoteSources {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun fetch(url: String): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", "SA-TV/1.0 (Android)").build()
        client.newCall(req).execute().use { r ->
            if (r.isSuccessful) r.body?.string()?.takeIf { it.isNotBlank() } else null
        }
    } catch (e: Exception) {
        null
    }

    private fun cached(context: Context, key: String, urls: List<String>): String? {
        val file = File(context.cacheDir, "src_$key.txt")
        for (u in urls) {
            val text = fetch(u)
            if (text != null) {
                runCatching { file.writeText(text) }
                return text
            }
        }
        return if (file.exists()) runCatching { file.readText() }.getOrNull() else null
    }

    private fun playlist(context: Context, key: String, url: String, category: String, limit: Int): List<Channel> {
        val text = cached(context, key, listOf(url)) ?: return emptyList()
        fun rank(c: Channel): Int {
            val id = c.id.orEmpty().lowercase()
            return when {
                id.contains(".in") -> 0
                id.contains(".bd") -> 1
                else -> 2
            }
        }
        return M3uParser.parse(text, PlaylistSource(category, url, 0))
            .filter { !it.name.contains("Geo-blocked", true) && !it.name.contains("Not 24/7", true) }
            .distinctBy { it.url }
            .distinctBy { it.name.lowercase().trim() }
            .sortedBy { rank(it) }
            .take(limit)
            .map { it.copy(group = category) }
    }

    private fun radio(context: Context): List<Channel> {
        val hosts = listOf(
            "https://de1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info",
            "https://at1.api.radio-browser.info"
        )
        val queries = listOf(
            "bn" to "language=bengali&limit=80",
            "bd" to "countrycode=BD&limit=40",
            "in" to "countrycode=IN&tag=bengali&limit=20"
        )
        val out = LinkedHashMap<String, Channel>()
        for ((key, q) in queries) {
            val urls = hosts.map { "$it/json/stations/search?$q&hidebroken=true&order=clickcount&reverse=true" }
            val text = cached(context, "radio_$key", urls) ?: continue
            try {
                val arr = JSONArray(text)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val url = o.optString("url_resolved").ifBlank { o.optString("url") }
                    val name = o.optString("name").trim()
                    if (url.isBlank() || name.isBlank()) continue
                    out.putIfAbsent(
                        name.lowercase(),
                        Channel(
                            id = o.optString("stationuuid"),
                            name = name,
                            url = url,
                            logo = o.optString("favicon").ifBlank { null },
                            group = "FM Radio",
                            country = o.optString("countrycode"),
                            language = o.optString("language")
                        )
                    )
                }
            } catch (e: Exception) {
                // ignore a bad response
            }
        }
        return out.values.take(120)
    }

    suspend fun loadAll(context: Context, onResult: (List<Channel>) -> Unit) = coroutineScope {
        val jobs = listOf(
            async(Dispatchers.IO) {
                playlist(context, "sports", "https://iptv-org.github.io/iptv/categories/sports.m3u", "Sports", 60)
            },
            async(Dispatchers.IO) {
                playlist(context, "kids", "https://iptv-org.github.io/iptv/categories/kids.m3u", "Kids", 50)
            },
            async(Dispatchers.IO) {
                playlist(context, "hindi", "https://iptv-org.github.io/iptv/languages/hin.m3u", "Hindi", 100)
            },
            async(Dispatchers.IO) { radio(context) }
        )
        for (job in jobs) {
            val r = job.await()
            withContext(Dispatchers.Main) { onResult(r) }
        }
    }
}
