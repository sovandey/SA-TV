package com.satv.app

import android.content.Context

object M3UParser {
    fun parse(context: Context): List<Channel> {
        val text = context.resources.openRawResource(R.raw.channels).bufferedReader().use { it.readText() }
        val channels = mutableListOf<Channel>()
        var name = ""
        var category = "All"
        var logo: String? = null
        var indian = false
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    name = line.substringAfterLast(",").trim()
                    category = Regex("group-title=\"(.*?)\"").find(line)?.groupValues?.get(1) ?: "All"
                    logo = Regex("tvg-logo=\"(.*?)\"").find(line)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                    val tvgId = Regex("tvg-id=\"(.*?)\"").find(line)?.groupValues?.get(1).orEmpty()
                    val marker = "${category} ${name} ${tvgId}".lowercase()
                    indian = tvgId.endsWith(".in", true) || marker.contains("india") || marker.contains("indian") ||
                        category.contains("hindi", true) || category.contains("india", true)
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    if (name.isNotBlank()) channels.add(Channel(name, line, category, logo, indian))
                    name = ""; category = "All"; logo = null; indian = false
                }
            }
        }
        return channels.distinctBy { it.name to it.url }.sortedWith(compareByDescending<Channel> { it.isIndian }.thenBy { it.name.lowercase() })
    }
}
