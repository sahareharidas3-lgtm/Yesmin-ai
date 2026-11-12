package com.yesmin.ai.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yesmin.ai.YesminApp
import com.yesmin.ai.assistant.WakeResult
import com.yesmin.ai.device.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service (visible notification, microphone type) that repeatedly listens for the wake word
 * using Android's SpeechRecognizer. This is NOT a dedicated low-power wake-word chip/engine: it uses
 * more battery, may stop on aggressive OEM battery managers, and speech services may beep.
 * See README for the Porcupine (offline) upgrade path.
 */
class WakeWordService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val graph = (application as YesminApp).graph
        if (intent?.action == ACTION_STOP) {
            graph.settings.update { it.copy(wakeWordEnabled = false) }
            stopSelf(); return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, Notifications.ID_SERVICE, Notifications.serviceNotification(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            graph.engine.wakeStatus.value = "Android refused to start the microphone service: ${e.message}"
            stopSelf(); return START_NOT_STICKY
        }
        if (job?.isActive != true) job = scope.launch { loop() }
        return START_STICKY
    }

    private suspend fun loop() {
        val g = (application as YesminApp).graph
        val pm = getSystemService(PowerManager::class.java)
        while (currentCoroutineContext().isActive) {
            val s = g.settings.settings.value
            if (!s.wakeWordEnabled || !s.voiceConsent) break
            if (s.wakeOnlyScreenOn && !pm.isInteractive) {
                g.engine.wakeStatus.value = "Paused (screen off, saves battery)"; delay(2000); continue
            }
            when (val r = g.engine.listenForWakeWord()) {
                is WakeResult.Detected -> g.engine.wakeTurn(r.rest)
                is WakeResult.None -> delay(r.pauseMs)
                is WakeResult.Fatal -> { g.engine.wakeStatus.value = r.message; break }
            }
        }
        if (g.engine.wakeStatus.value.startsWith("Listening") || g.engine.wakeStatus.value.startsWith("Wake")) g.engine.wakeStatus.value = "Off"
        stopSelf()
    }

    override fun onDestroy() {
        val g = (application as YesminApp).graph
        g.stt.cancel()
        scope.cancel()
        if (g.engine.wakeStatus.value.startsWith("Listening")) g.engine.wakeStatus.value = "Off"
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.yesmin.ai.STOP_WAKE"

        /** Must be called while the app is visible (Android 12+ restriction). */
        fun start(ctx: Context): Boolean {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
            return try { ContextCompat.startForegroundService(ctx, Intent(ctx, WakeWordService::class.java)); true }
            catch (e: Exception) { false }
        }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, WakeWordService::class.java)) }
    }
}
