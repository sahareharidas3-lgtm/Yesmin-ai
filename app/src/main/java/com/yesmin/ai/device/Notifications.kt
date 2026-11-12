package com.yesmin.ai.device

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yesmin.ai.R
import com.yesmin.ai.service.WakeWordService
import com.yesmin.ai.ui.MainActivity

object Notifications {
    const val CH_SERVICE = "yesmin_service"
    const val CH_REMINDER = "yesmin_reminder"
    const val CH_ACTION = "yesmin_action"
    const val ID_SERVICE = 1

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SERVICE, "Wake-word listening", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_REMINDER, "Reminders", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_ACTION, "Tap to complete action", NotificationManager.IMPORTANCE_HIGH))
    }

    fun serviceNotification(ctx: Context) = NotificationCompat.Builder(ctx, CH_SERVICE)
        .setSmallIcon(R.drawable.ic_stat_heart)
        .setContentTitle("YESMIN is listening for \"Yesmin\"")
        .setContentText("Microphone in use · tap to open")
        .setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        .addAction(0, "Stop listening", PendingIntent.getService(ctx, 1,
            Intent(ctx, WakeWordService::class.java).setAction(WakeWordService.ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
        .build()

    /** Returns true if posted. Used when Android blocks starting an app from the background. */
    fun postAction(ctx: Context, text: String, intent: Intent): Boolean = notify(ctx, (System.nanoTime() and 0xffffff).toInt(),
        NotificationCompat.Builder(ctx, CH_ACTION).setSmallIcon(R.drawable.ic_stat_heart)
            .setContentTitle("YESMIN: $text").setContentText("Tap to do it now")
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(ctx, (System.nanoTime() and 0xffffff).toInt(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build())

    fun postReminder(ctx: Context, id: Int, text: String) = notify(ctx, id,
        NotificationCompat.Builder(ctx, CH_REMINDER).setSmallIcon(R.drawable.ic_stat_heart)
            .setContentTitle("Reminder from YESMIN ❤️").setContentText(text).setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)).build())

    fun enabled(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    private fun notify(ctx: Context, id: Int, n: android.app.Notification): Boolean {
        if (!enabled(ctx)) return false
        return try { NotificationManagerCompat.from(ctx).notify(id, n); true } catch (e: SecurityException) { false }
    }
}
