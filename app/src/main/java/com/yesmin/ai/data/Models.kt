package com.yesmin.ai.data

import org.json.JSONObject

data class ChatMessage(val id: Long, val role: String, val text: String, val time: Long) {
    val isUser get() = role == "user"
    fun toJson(): JSONObject = JSONObject().put("id", id).put("role", role).put("text", text).put("time", time)
    companion object {
        fun fromJson(o: JSONObject) = ChatMessage(o.getLong("id"), o.getString("role"), o.getString("text"), o.getLong("time"))
    }
}

/** Used for both notes and user-approved memory items. */
data class TextItem(val id: Long, val text: String, val time: Long) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("text", text).put("time", time)
    companion object {
        fun fromJson(o: JSONObject) = TextItem(o.getLong("id"), o.getString("text"), o.getLong("time"))
    }
}

data class AppSettings(
    val model: String = "gemini-2.5-flash",
    val language: String = "hinglish",        // hinglish | hindi | english
    val voiceName: String = "",               // "" = automatic
    val pitch: Float = 1.25f,
    val speechRate: Float = 1.0f,
    val wakeWordEnabled: Boolean = false,
    val wakeWord: String = "yesmin",
    val wakeOnlyScreenOn: Boolean = true,
    val hairColor: Int = 0,
    val eyeColor: Int = 0,
    val personality: String = "romantic",     // romantic | playful | caring | smart
    val theme: String = "system",             // system | light | dark
    val memoryEnabled: Boolean = false,
    val saveHistory: Boolean = true,
    val webSearch: Boolean = true,
    val continuous: Boolean = false,
    val preferOffline: Boolean = false,
    val muted: Boolean = false,
    val voiceConsent: Boolean = false,
    val favouriteSong: String = "",
    val responseLength: String = "short"      // short | normal | detailed
)
