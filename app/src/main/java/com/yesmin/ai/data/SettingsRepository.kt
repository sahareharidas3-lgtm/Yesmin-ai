package com.yesmin.ai.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(ctx: Context) {
    private val p = ctx.getSharedPreferences("yesmin_settings", Context.MODE_PRIVATE)
    private val _s = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _s.asStateFlow()

    @Synchronized fun update(f: (AppSettings) -> AppSettings) {
        val n = f(_s.value)
        _s.value = n
        save(n)
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            model = p.getString("model", d.model) ?: d.model,
            language = p.getString("language", d.language) ?: d.language,
            voiceName = p.getString("voiceName", d.voiceName) ?: "",
            pitch = p.getFloat("pitch", d.pitch),
            speechRate = p.getFloat("speechRate", d.speechRate),
            wakeWordEnabled = p.getBoolean("wakeWordEnabled", d.wakeWordEnabled),
            wakeWord = p.getString("wakeWord", d.wakeWord) ?: d.wakeWord,
            wakeOnlyScreenOn = p.getBoolean("wakeOnlyScreenOn", d.wakeOnlyScreenOn),
            hairColor = p.getInt("hairColor", d.hairColor),
            eyeColor = p.getInt("eyeColor", d.eyeColor),
            personality = p.getString("personality", d.personality) ?: d.personality,
            theme = p.getString("theme", d.theme) ?: d.theme,
            memoryEnabled = p.getBoolean("memoryEnabled", d.memoryEnabled),
            saveHistory = p.getBoolean("saveHistory", d.saveHistory),
            webSearch = p.getBoolean("webSearch", d.webSearch),
            continuous = p.getBoolean("continuous", d.continuous),
            preferOffline = p.getBoolean("preferOffline", d.preferOffline),
            muted = p.getBoolean("muted", d.muted),
            voiceConsent = p.getBoolean("voiceConsent", d.voiceConsent),
            favouriteSong = p.getString("favouriteSong", d.favouriteSong) ?: "",
            responseLength = p.getString("responseLength", d.responseLength) ?: d.responseLength
        )
    }

    private fun save(s: AppSettings) {
        p.edit()
            .putString("model", s.model).putString("language", s.language).putString("voiceName", s.voiceName)
            .putFloat("pitch", s.pitch).putFloat("speechRate", s.speechRate)
            .putBoolean("wakeWordEnabled", s.wakeWordEnabled).putString("wakeWord", s.wakeWord)
            .putBoolean("wakeOnlyScreenOn", s.wakeOnlyScreenOn)
            .putInt("hairColor", s.hairColor).putInt("eyeColor", s.eyeColor)
            .putString("personality", s.personality).putString("theme", s.theme)
            .putBoolean("memoryEnabled", s.memoryEnabled).putBoolean("saveHistory", s.saveHistory)
            .putBoolean("webSearch", s.webSearch).putBoolean("continuous", s.continuous)
            .putBoolean("preferOffline", s.preferOffline).putBoolean("muted", s.muted)
            .putBoolean("voiceConsent", s.voiceConsent).putString("favouriteSong", s.favouriteSong)
            .putString("responseLength", s.responseLength)
            .apply()
    }
}
