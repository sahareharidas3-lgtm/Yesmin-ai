package com.yesmin.ai

import android.app.Application
import com.yesmin.ai.device.Notifications

class YesminApp : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
        Notifications.createChannels(this)
    }
}
