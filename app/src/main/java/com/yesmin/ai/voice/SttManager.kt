package com.yesmin.ai.voice

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.yesmin.ai.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

sealed class SttResult {
    data class Text(val text: String) : SttResult()
    data class Error(val code: Int, val message: String, val benign: Boolean = false, val fatal: Boolean = false) : SttResult()
}

/**
 * Speech-to-text through Android's SpeechRecognizer. By default Google's recognizer may send audio to
 * Google servers; enable "Prefer offline" in Settings to request on-device recognition when a language
 * pack is installed. One listening session at a time.
 */
class SttManager(private val app: Application, private val settings: SettingsRepository) {
    val listening = MutableStateFlow(false)
    val partial = MutableStateFlow("")
    val rms = MutableStateFlow(0f)
    @Volatile private var cancelHook: (() -> Unit)? = null

    fun cancel() {
        val h = cancelHook ?: return
        Handler(Looper.getMainLooper()).post { h() }
    }

    suspend fun listenOnce(foreground: Boolean = true): SttResult = withContext(Dispatchers.Main.immediate) {
        if (!SpeechRecognizer.isRecognitionAvailable(app))
            return@withContext SttResult.Error(-1, "Speech recognition service is not available on this phone (install/enable Google app).", fatal = true)
        val s = settings.settings.value
        suspendCancellableCoroutine<SttResult> { cont ->
            val rec = SpeechRecognizer.createSpeechRecognizer(app)
            val finished = AtomicBoolean(false)
            fun finish(r: SttResult) {
                if (!finished.compareAndSet(false, true)) return
                cancelHook = null
                if (foreground) { listening.value = false; rms.value = 0f; partial.value = "" }
                runCatching { rec.destroy() }
                if (cont.isActive) cont.resume(r)
            }
            rec.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { if (foreground) rms.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) {
                    if (foreground) partial.value = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                }
                override fun onResults(results: Bundle?) {
                    val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    finish(if (t.isEmpty()) info(SpeechRecognizer.ERROR_NO_MATCH) else SttResult.Text(t))
                }
                override fun onError(error: Int) = finish(info(error))
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (s.language == "english") "en-IN" else "hi-IN")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, s.preferOffline)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
            cancelHook = { finish(SttResult.Error(-2, "cancelled", benign = true)) }
            cont.invokeOnCancellation {
                cancelHook = null
                Handler(Looper.getMainLooper()).post {
                    finished.set(true)
                    runCatching { rec.destroy() }
                    if (foreground) { listening.value = false; rms.value = 0f; partial.value = "" }
                }
            }
            if (foreground) listening.value = true
            rec.startListening(i)
        }
    }

    private fun info(code: Int): SttResult.Error = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> SttResult.Error(code, "Mujhe kuch samajh nahi aaya.", benign = true)
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SttResult.Error(code, "Kuch sunai nahi diya.", benign = true)
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SttResult.Error(code, "Recognizer busy.", benign = true)
        SpeechRecognizer.ERROR_CLIENT -> SttResult.Error(code, "Recognizer restarted.", benign = true)
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SttResult.Error(code, "Microphone permission missing.", fatal = true)
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
            SttResult.Error(code, "Speech service needs internet (or install an offline language pack).")
        SpeechRecognizer.ERROR_AUDIO -> SttResult.Error(code, "Microphone audio error (is another app using the mic?).")
        else -> SttResult.Error(code, "Speech recognition error ($code).")
    }
}
