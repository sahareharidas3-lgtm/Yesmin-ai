package com.yesmin.ai.assistant

object WakeWord {
    private val yesminVariants = listOf(
        "yesmin", "yes min", "yasmin", "yasmine", "yesmeen", "yasmeen", "jasmin", "jasmine",
        "यस्मिन", "येसमिन", "येस्मिन", "यासमीन", "यास्मीन", "यास्मिन", "जैस्मिन", "यस मिन", "येस मीन"
    )

    /** Returns the text spoken AFTER the wake word ("" if only the wake word), or null if not present. */
    fun match(text: String, custom: String): String? {
        val t = text.lowercase()
        val c = custom.lowercase().trim()
        val variants = if (c == "yesmin" || c.isEmpty()) yesminVariants else listOf(c)
        var best: Pair<Int, Int>? = null
        for (v in variants) {
            val i = t.indexOf(v)
            if (i >= 0 && (best == null || i < best.first)) best = i to v.length
        }
        return best?.let { t.substring(it.first + it.second).trim(' ', ',', '.', '!', '?', '।') }
    }
}
