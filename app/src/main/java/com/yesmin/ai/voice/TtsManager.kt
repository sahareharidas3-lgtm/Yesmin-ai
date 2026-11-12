package com.yesmin.ai.voice

import android.app.Application
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.yesmin.ai.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class VoiceInfo(val name: String, val label: String)

/**
 * Text-to-speech using the phone's TTS engine (usually Google). Android does not expose voice gender,
 * so the user picks a voice in Settings and previews it; pitch is raised for a cuter tone.
 * [mouthKick] increments on every spoken word and drives the avatar's lip-sync.
 */
class TtsManager(private val app: Application, private val settings: SettingsRepository) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    val speaking = MutableStateFlow(false)
    val mouthKick = MutableStateFlow(0)
    val voices = MutableStateFlow<List<VoiceInfo>>(emptyList())
    private val waiters = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(id: String?) { speaking.value = true }
        override fun onDone(id: String?) = finish(id)
        @Deprecated("Deprecated in Java")
        override fun onError(id: String?) = finish(id)
        override fun onError(id: String?, errorCode: Int) = finish(id)
        override fun onStop(id: String?, interrupted: Boolean) = finish(id)
        override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) { mouthKick.value = mouthKick.value + 1 }
    }

    init {
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.setOnUtteranceProgressListener(listener)
                refreshVoices()
            }
        }
    }

    private fun finish(id: String?) {
        if (id != null) waiters.remove(id)?.complete(Unit)
        if (waiters.isEmpty()) speaking.value = false
    }

    private fun refreshVoices() {
        val list = tts?.voices.orEmpty()
            .filter { it.locale.language == "en" || it.locale.language == "hi" }
            .sortedWith(compareBy({ it.locale.toString() }, { it.name }))
            .map { VoiceInfo(it.name, "${it.locale.toLanguageTag()} · ${it.name}" + if (it.isNetworkConnectionRequired) " (online)" else "") }
        voices.value = list
    }

    private fun apply(t: TextToSpeech) {
        val s = settings.settings.value
        t.setPitch(s.pitch)
        t.setSpeechRate(s.speechRate)
        val chosen = if (s.voiceName.isNotBlank()) t.voices?.firstOrNull { it.name == s.voiceName } else null
        if (chosen != null) t.setVoice(chosen)
        else t.setLanguage(if (s.language == "hindi") Locale("hi", "IN") else Locale("en", "IN"))
    }

    /** Speaks and suspends until finished (or cancelled). Silent when muted. */
    suspend fun speak(text: String) {
        if (settings.settings.value.muted || !ready) return
        val t = tts ?: return
        val clean = Speech.clean(text)
        if (clean.isBlank()) return
        apply(t)
        val chunks = Speech.chunk(clean, 1500)
        val done = CompletableDeferred<Unit>()
        val lastId = "last_${System.nanoTime()}"
        waiters[lastId] = done
        chunks.forEachIndexed { i, c ->
            val id = if (i == chunks.lastIndex) lastId else "part_${System.nanoTime()}_$i"
            if (t.speak(c, TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) {
                waiters.remove(lastId); done.complete(Unit); return@forEachIndexed
            }
        }
        try { done.await() } catch (e: CancellationException) { stop(); throw e }
    }

    fun stop() {
        tts?.stop()
        waiters.values.forEach { it.complete(Unit) }
        waiters.clear()
        speaking.value = false
    }
}
