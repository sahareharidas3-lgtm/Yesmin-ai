package com.yesmin.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yesmin.ai.YesminApp
import com.yesmin.ai.ai.AiResult
import com.yesmin.ai.data.AppSettings
import com.yesmin.ai.data.TextItem
import com.yesmin.ai.service.WakeWordService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val g = (app as YesminApp).graph

    val settings = g.settings.settings
    val messages = g.chat.items
    val notes = g.notes.items
    val memory = g.memory.items
    val avatar = g.engine.avatarState
    val happy = g.engine.happy
    val busy = g.engine.busy
    val pendingCall = g.engine.pendingCall
    val wakeStatus = g.engine.wakeStatus
    val mouthKick = g.tts.mouthKick
    val speaking = g.tts.speaking
    val voices = g.tts.voices
    val listening = g.stt.listening
    val partial = g.stt.partial
    val rms = g.stt.rms
    val apiStatus = g.gemini.status

    private val _hasKey = MutableStateFlow(g.gemini.hasKey())
    val hasKey = _hasKey.asStateFlow()
    private val _models = MutableStateFlow(listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.5-flash-lite", "gemini-2.0-flash"))
    val models = _models.asStateFlow()
    private val _testMsg = MutableStateFlow("")
    val testMsg = _testMsg.asStateFlow()
    private val _working = MutableStateFlow(false)
    val working = _working.asStateFlow()

    private var voiceJob: Job? = null
    private var textJob: Job? = null

    fun keyTail() = g.gemini.keyTail()
    fun update(f: (AppSettings) -> AppSettings) = g.settings.update(f)

    fun sendText(t: String) {
        if (t.isBlank()) return
        textJob?.cancel()
        g.engine.interrupt()
        textJob = viewModelScope.launch { g.engine.handleUserText(t) }
    }

    /** Mic button: starts a voice conversation, or stops it if one is already running. */
    fun toggleVoice() {
        if (voiceJob?.isActive == true) { voiceJob?.cancel(); g.engine.interrupt(); return }
        g.engine.interrupt()
        voiceJob = viewModelScope.launch { g.engine.voiceConversation() }
    }

    fun toggleMute() {
        val m = !settings.value.muted
        g.settings.update { it.copy(muted = m) }
        if (m) g.tts.stop()
    }

    fun confirmCall(ok: Boolean) { viewModelScope.launch { g.engine.confirmCall(ok) } }

    fun previewVoice() { viewModelScope.launch { g.tts.stop(); g.tts.speak("Ji Boss! Main yahin hoon. Bataiye kya karna hai?") } }

    // ---- Gemini settings
    fun saveKey(k: String) { if (k.isNotBlank()) { g.gemini.saveKey(k); _hasKey.value = true; _testMsg.value = "" } }
    fun clearKey() { g.gemini.clearKey(); _hasKey.value = false; _testMsg.value = "" }

    fun testApi() = viewModelScope.launch {
        _working.value = true; _testMsg.value = "Testing…"
        _testMsg.value = when (val r = g.gemini.test(settings.value.model)) {
            is AiResult.Ok -> "✅ Connected. Gemini replied: ${r.text.take(40)}"
            is AiResult.Err -> "❌ ${r.message}"
        }
        _working.value = false
    }

    fun fetchModels() = viewModelScope.launch {
        _working.value = true
        g.gemini.listModels().onSuccess { if (it.isNotEmpty()) _models.value = it; _testMsg.value = "Found ${it.size} models" }
            .onFailure { _testMsg.value = "❌ Could not list models: ${it.message}" }
        _working.value = false
    }

    // ---- wake word
    fun setWake(enabled: Boolean): Boolean {
        if (enabled) {
            val ok = WakeWordService.start(getApplication())
            if (!ok) return false
            g.settings.update { it.copy(wakeWordEnabled = true) }
        } else {
            g.settings.update { it.copy(wakeWordEnabled = false) }
            WakeWordService.stop(getApplication())
        }
        return true
    }

    // ---- notes / memory / privacy
    fun addNote(t: String) { if (t.isNotBlank()) g.notes.add(TextItem(System.nanoTime(), t.trim(), System.currentTimeMillis())) }
    fun deleteNote(id: Long) = g.notes.remove { it.id == id }
    fun addMemory(t: String) { if (t.isNotBlank()) g.memory.add(TextItem(System.nanoTime(), t.trim(), System.currentTimeMillis())) }
    fun deleteMemory(id: Long) = g.memory.remove { it.id == id }
    fun clearChat() = g.chat.clear()

    fun deleteEverything() {
        setWake(false)
        g.chat.clear(); g.notes.clear(); g.memory.clear()
        g.gemini.clearKey(); _hasKey.value = false
        g.settings.update { AppSettings() }
    }
}
