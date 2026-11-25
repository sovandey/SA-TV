package com.banglatv.app

/** One TV channel. A channel can have several stream URLs; the player fails over between them. */
data class Channel(
    val key: String,
    val name: String,
    val group: String,
    val logo: String?,
    val urls: List<String>,
)

object M3u {
    private val logoRe = Regex("""tvg-logo="([^"]*)"""")
    private val groupRe = Regex("""group-title="([^"]*)"""")
    private val resRe = Regex("""\s*\((\d+p|backup \d+)\)""")
    private val bracketRe = Regex("""\s*\[.*?]""")

    fun normalize(name: String): String {
        var s = name.lowercase()
        s = s.replace(Regex("""\(.*?\)|\[.*?]"""), " ")
        s = s.replace(Regex("""\b(hd|hq|sd)\b"""), " ")
        return s.filter { it.isLetterOrDigit() }
    }

    fun parse(text: String, forceGroup: String? = null): List<Channel> {
        val result = ArrayList<Channel>()
        var pending: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF")) {
                pending = line
            } else if (line.startsWith("#")) {
                continue
            } else if (line.startsWith("http") && pending != null) {
                val info = pending
                pending = null
                val display = info.substringAfterLast(',')
                    .replace(resRe, "")
                    .replace(bracketRe, "")
                    .trim()
                if (display.isEmpty()) continue
                val group = forceGroup
                    ?: groupRe.find(info)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                    ?: "Other"
                val logo = logoRe.find(info)?.groupValues?.get(1)?.takeIf { it.startsWith("http") }
                val url = line.substringBefore('|').trim()
                result.add(Channel(normalize(display), display, group, logo, listOf(url)))
            }
        }
        return result
    }

    /** Merge lists; channels with the same normalized name become alternate sources of one channel. */
    fun merge(vararg lists: List<Channel>): List<Channel> {
        val map = LinkedHashMap<String, Channel>()
        for (list in lists) for (c in list) {
            val old = map[c.key]
            map[c.key] = if (old == null) c else old.copy(
                logo = old.logo ?: c.logo,
                urls = (old.urls + c.urls).distinct(),
            )
        }
        return map.values.toList()
    }
}
