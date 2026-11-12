package com.yesmin.ai.voice

object Speech {
    private val code = Regex("```[\\s\\S]*?```")
    private val url = Regex("https?://\\S+")
    private val md = Regex("[*_#`>~|]")
    private val emoji = Regex("[\\p{So}\\p{Cs}\\uFE0F\\u200D]")

    /** Makes chat text speakable: removes emoji, markdown, URLs and code blocks. */
    fun clean(t: String): String = t
        .replace(code, " Code screen par likha hai. ")
        .replace(url, "")
        .replace(md, "")
        .replace(emoji, "")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun chunk(t: String, max: Int): List<String> {
        if (t.length <= max) return listOf(t)
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        t.split(Regex("(?<=[.!?।])\\s+")).forEach { s ->
            if (sb.length + s.length + 1 > max && sb.isNotEmpty()) { out += sb.toString(); sb.clear() }
            if (s.length > max) s.chunked(max).forEach { out += it } else { if (sb.isNotEmpty()) sb.append(' '); sb.append(s) }
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }
}
