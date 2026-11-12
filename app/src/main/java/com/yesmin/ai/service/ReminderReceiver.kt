package com.yesmin.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yesmin.ai.device.Notifications

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifications.postReminder(context, intent.getIntExtra("nid", 0), intent.getStringExtra("text") ?: "Reminder")
    }
}
