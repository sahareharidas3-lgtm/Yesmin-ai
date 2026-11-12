package com.yesmin.ai

import android.app.Application
import com.yesmin.ai.ai.GeminiClient
import com.yesmin.ai.assistant.AssistantEngine
import com.yesmin.ai.assistant.CommandRouter
import com.yesmin.ai.data.ChatMessage
import com.yesmin.ai.data.PersistentList
import com.yesmin.ai.data.SecureStore
import com.yesmin.ai.data.SettingsRepository
import com.yesmin.ai.data.TextItem
import com.yesmin.ai.device.DeviceController
import com.yesmin.ai.voice.SttManager
import com.yesmin.ai.voice.TtsManager
import java.io.File

/** Simple manual dependency container shared by the UI, the engine and the background service. */
class Graph(val app: Application) {
    val secure = SecureStore(app)
    val settings = SettingsRepository(app)
    val gemini = GeminiClient(secure)
    val chat = PersistentList<ChatMessage>(File(app.filesDir, "chat.json"), { it.toJson() }, { ChatMessage.fromJson(it) }, 400)
    val notes = PersistentList<TextItem>(File(app.filesDir, "notes.json"), { it.toJson() }, { TextItem.fromJson(it) })
    val memory = PersistentList<TextItem>(File(app.filesDir, "memory.json"), { it.toJson() }, { TextItem.fromJson(it) }, 100)
    val tts = TtsManager(app, settings)
    val stt = SttManager(app, settings)
    val device = DeviceController(app)
    val router = CommandRouter()
    val engine = AssistantEngine(this)
}
