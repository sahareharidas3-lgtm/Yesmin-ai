package com.yesmin.ai.assistant

sealed class Command {
    data class OpenApp(val name: String) : Command()
    data class OpenSettingsPage(val page: String) : Command()
    object Camera : Command()
    object Gallery : Command()
    data class Torch(val on: Boolean) : Command()
    data class Volume(val mode: String, val percent: Int? = null) : Command()
    data class Call(val target: String) : Command()
    data class PlayMusic(val query: String, val favourite: Boolean = false) : Command()
    data class SaveNote(val text: String) : Command()
    object ShowNotes : Command()
    data class Remind(val minutes: Int, val text: String) : Command()
    data class Calc(val expr: String) : Command()
    data class Remember(val text: String) : Command()
}

/**
 * Fast, offline rule-based parser for phone actions (English / Hindi / Hinglish).
 * Anything it does not recognise returns null and is sent to Gemini as normal conversation.
 */
class CommandRouter {
    private val apps = mapOf(
        "यूट्यूब" to "youtube", "यूटयूब" to "youtube", "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp",
        "वाट्सएप" to "whatsapp", "क्रोम" to "chrome", "इंस्टाग्राम" to "instagram", "जीमेल" to "gmail",
        "टेलीग्राम" to "telegram", "मैप्स" to "maps", "yt" to "youtube", "whats app" to "whatsapp"
    )
    private val fillers = setOf(
        "open", "kholo", "khol", "kholiye", "launch", "app", "karo", "kar", "do", "please", "pls", "ko", "the", "mera", "meri",
        "ka", "ki", "kijiye", "na", "zara", "yesmin", "खोलो", "खोल", "ओपन", "करो", "को", "ऐप", "एप"
    )

    fun parse(raw: String): Command? {
        val t = raw.lowercase().replace("yesmin", " ").replace("यस्मिन", " ").trim()
        if (t.isEmpty()) return null
        val tokens = t.split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }
        val set = tokens.toSet()
        fun has(vararg w: String) = w.any { it in set || (it.contains(' ') && t.contains(it)) }

        // --- memory / notes / reminders ---
        Regex("^(?:please\\s+)?(?:remember(?: that)?|yaad rakh(?:o|na)?(?: ki)?)\\s+(.+)$").find(t)?.let { return Command.Remember(it.groupValues[1]) }
        if (has("notes") && has("show", "dikhao", "mere", "my", "read", "padho", "list")) return Command.ShowNotes
        Regex("^(?:save |add )?(?:a )?note(?: this| down)?[:,\\s]+(.+)$").find(t)?.let { return Command.SaveNote(it.groupValues[1]) }
        Regex("^(.+?)\\s+note\\s+(?:kar|karo|kar lo|kar do)$").find(t)?.let { return Command.SaveNote(it.groupValues[1]) }
        Regex("^(?:likh lo|likh do)[:,\\s]+(.+)$").find(t)?.let { return Command.SaveNote(it.groupValues[1]) }
        if (has("remind", "reminder", "yaad") && has("dilao", "dilana", "dila", "me", "remind", "reminder")) {
            Regex("(\\d+)\\s*(min|mins|minute|minutes|hour|hours|hr|hrs|ghante|ghanta)").find(t)?.let { m ->
                val n = m.groupValues[1].toInt()
                val mins = if (m.groupValues[2].startsWith("min")) n else n * 60
                val what = t.replace(m.value, " ")
                    .replace(Regex("\\b(remind me|reminder|yaad dila(?:na|o|do)?|baad|mein|me|in|to|ki|ke|about|set|a|karo|kar do|please)\\b"), " ")
                    .replace(Regex("\\s+"), " ").trim()
                return Command.Remind(mins, what.ifBlank { "Reminder" })
            }
        }

        // --- calculator ---
        val calcWord = has("calculate", "calculator", "hisab", "hisaab", "kitna", "equals", "solve")
        Calculator.extract(t)?.let { expr ->
            val pure = t.replace(Regex("[^a-z\\p{L}]+"), " ").trim().split(" ")
                .filter { it.isNotBlank() }.all { it in setOf("plus", "minus", "times", "into", "by", "divided", "multiplied", "x", "power", "percent", "of") }
            if (calcWord || pure) return Command.Calc(expr)
        }

        // --- flashlight ---
        if (has("flashlight", "torch", "flash", "tourch", "टॉर्च", "फ्लैशलाइट")) {
            val off = has("off", "band", "bujha", "bujhao", "stop", "बंद", "बुझाओ")
            return Command.Torch(!off)
        }

        // --- volume ---
        if (has("volume", "awaaz", "awaz", "आवाज", "वॉल्यूम")) {
            Regex("(\\d{1,3})").find(t)?.let { m -> m.groupValues[1].toInt().takeIf { it in 0..100 }?.let { return Command.Volume("set", it) } }
            return when {
                has("mute", "zero", "silent") -> Command.Volume("mute")
                has("max", "full", "maximum", "poori") -> Command.Volume("max")
                has("up", "badha", "badhao", "badhana", "increase", "zyada", "jyada", "tez", "loud", "बढ़ाओ", "बढाओ", "ज्यादा") -> Command.Volume("up")
                has("down", "kam", "kum", "ghata", "ghatao", "decrease", "low", "slow", "धीमा", "कम", "घटाओ") -> Command.Volume("down")
                else -> null
            }
        }

        // --- call ---
        Regex("^(?:please\\s+)?(?:call|dial|phone)\\s+(?:to\\s+)?(.+?)(?:\\s+(?:karo|kar do|kijiye|please|lagao))?$").find(t)?.let { return Command.Call(it.groupValues[1].trim()) }
        Regex("^(.+?)\\s+ko\\s+(?:call|phone|fon)\\s*(?:karo|kar do|lagao|laga do|kijiye)?$").find(t)?.let { return Command.Call(it.groupValues[1].trim()) }

        val openW = has("open", "kholo", "khol", "kholiye", "launch", "खोलो", "खोल", "ओपन")

        // --- music (before "open" so "play X on youtube" is music) ---
        if (!t.contains("play store") && has("play", "bajao", "bajaao", "chalao", "sunao", "बजाओ", "सुनाओ", "चलाओ", "लगाओ")) {
            if (has("favourite", "favorite", "fav", "pasandida", "pasandeeda", "पसंदीदा")) return Command.PlayMusic("", true)
            val stop = setOf("play", "bajao", "bajaao", "chalao", "sunao", "please", "on", "youtube", "yt", "par", "pe", "mein", "me",
                "karo", "kar", "do", "for", "a", "some", "the", "ek", "mere", "liye", "बजाओ", "सुनाओ", "चलाओ", "लगाओ", "यूट्यूब", "पर")
            var q = tokens.filter { it !in stop }.joinToString(" ")
            if (q == "romantic songs" || q == "romantic song" || q == "romantic") q = "romantic hindi songs"
            return Command.PlayMusic(q)
        }

        // --- settings pages / camera / gallery / apps ---
        if (openW || has("settings")) {
            val pages = listOf(
                "wifi" to listOf("wifi", "wi-fi", "wi fi", "internet"), "bluetooth" to listOf("bluetooth"),
                "display" to listOf("display", "brightness"), "sound" to listOf("sound"), "battery" to listOf("battery"),
                "location" to listOf("location", "gps"), "airplane" to listOf("airplane", "flight mode"),
                "storage" to listOf("storage"), "accessibility" to listOf("accessibility"), "apps" to listOf("apps")
            )
            for ((p, keys) in pages) if (keys.any { set.contains(it) || ((it.contains(' ') || it.contains('-')) && t.contains(it)) }) return Command.OpenSettingsPage(p)
            if (has("settings", "setting", "सेटिंग", "सेटिंग्स")) return Command.OpenSettingsPage("general")
        }
        if (openW && has("camera", "कैमरा")) return Command.Camera
        if (openW && has("gallery", "photos", "गैलरी")) return Command.Gallery
        if (openW) {
            var name = tokens.filter { it !in fillers }.joinToString(" ")
            apps[name]?.let { name = it }
            if (name.isNotBlank()) return Command.OpenApp(name)
        }
        return null
    }
}
