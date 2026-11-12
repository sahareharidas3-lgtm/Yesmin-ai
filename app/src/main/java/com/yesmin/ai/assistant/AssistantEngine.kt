package com.yesmin.ai.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.yesmin.ai.Graph
import com.yesmin.ai.ai.AiResult
import com.yesmin.ai.ai.Persona
import com.yesmin.ai.data.AppSettings
import com.yesmin.ai.data.ChatMessage
import com.yesmin.ai.data.TextItem
import com.yesmin.ai.device.DeviceController
import com.yesmin.ai.voice.SttResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private data class Reply(val text: String, val speech: String = text)

/**
 * The brain that connects voice, commands, Gemini, memory and the avatar.
 * 1) Phone-action commands are handled locally (fast, offline, never "faked").
 * 2) Everything else goes to Gemini with YESMIN's persona.
 */
class AssistantEngine(private val g: Graph) {
    private val app = g.app
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var happyJob: Job? = null

    val avatarState = MutableStateFlow(AvatarState.Idle)
    val happy = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val pendingCall = MutableStateFlow<PendingCall?>(null)
    val wakeStatus = MutableStateFlow("Off")
    /** Only one microphone session at a time. */
    val micLock = Mutex()

    private fun pick(vararg o: String) = o[Random.nextInt(o.size)]
    private fun hasMic() = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun addMsg(role: String, text: String) =
        g.chat.add(ChatMessage(System.nanoTime(), role, text, System.currentTimeMillis()), g.settings.settings.value.saveHistory)

    fun interrupt() { g.tts.stop(); g.stt.cancel() }

    private fun markHappy(text: String) {
        if (listOf("❤", "😊", "😘", "🥰", "😍", "💕", "✨", "😄").none { text.contains(it) }) return
        happy.value = true
        happyJob?.cancel()
        happyJob = scope.launch { delay(4500); happy.value = false }
    }

    private suspend fun speakReply(text: String) {
        if (g.settings.settings.value.muted) return
        avatarState.value = AvatarState.Talking
        try { g.tts.speak(text) } finally { avatarState.value = AvatarState.Idle }
    }

    // ------------------------------------------------------------------ text turn
    suspend fun handleUserText(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        val s = g.settings.settings.value
        addMsg("user", text)
        busy.value = true
        avatarState.value = AvatarState.Thinking
        try {
            val reply = produce(text, s)
            addMsg("model", reply.text)
            markHappy(reply.text)
            speakReply(reply.speech)
        } finally {
            busy.value = false
            if (avatarState.value != AvatarState.Listening) avatarState.value = AvatarState.Idle
        }
    }

    private suspend fun produce(text: String, s: AppSettings): Reply {
        pendingCall.value?.let { pc ->
            val words = text.lowercase().split(Regex("[^\\p{L}\\p{M}]+")).toSet()
            val yes = setOf("haan", "ha", "han", "yes", "yeah", "ok", "okay", "karo", "lagao", "confirm", "sure", "हाँ", "हां", "जी", "करो")
            val no = setOf("nahi", "nahin", "no", "mat", "cancel", "ruko", "stop", "नहीं", "मत")
            if (words.any { it in no }) { pendingCall.value = null; return Reply("Theek hai Boss, call cancel kar diya 💕") }
            if (words.any { it in yes }) { pendingCall.value = null; return Reply(say(g.device.placeCall(pc.number))) }
            pendingCall.value = null // unrelated message: drop the pending call, never call without a clear yes
        }
        val cmd = g.router.parse(text)
        return if (cmd != null) Reply(execute(cmd, s)) else askGemini(s)
    }

    private fun say(o: DeviceController.Outcome) =
        if (o.ok) pick("Ho gaya Boss! ✨ ${o.message}", "Ji Boss, ${o.message} 😊", "${o.message} ❤️ Aur kuch?", "Done Boss! ${o.message} 💕")
        else pick("Sorry Boss 🥺 ${o.message}", "Boss, ye nahi ho paaya 😔 ${o.message}")

    private fun execute(cmd: Command, s: AppSettings): String = when (cmd) {
        is Command.OpenApp -> say(g.device.openApp(cmd.name))
        is Command.OpenSettingsPage -> say(g.device.openSettingsPage(cmd.page))
        Command.Camera -> say(g.device.openCamera())
        Command.Gallery -> say(g.device.openGallery())
        is Command.Torch -> say(g.device.setTorch(cmd.on))
        is Command.Volume -> say(g.device.adjustVolume(cmd.mode, cmd.percent))
        is Command.PlayMusic -> {
            val q = if (cmd.favourite) s.favouriteSong else cmd.query
            when {
                q.isBlank() && cmd.favourite -> "Boss, abhi tak favourite song set nahi hai 🥺 Settings → Personality me naam daal dijiye, phir main bajaungi 🎶"
                q.isBlank() -> "Kaunsa gaana bajaun Boss? 🎶"
                else -> say(g.device.playMusic(q))
            }
        }
        is Command.Call -> {
            val digits = cmd.target.filter { it.isDigit() || it == '+' }
            if (digits.count { it.isDigit() } >= 5) {
                pendingCall.value = PendingCall(digits, digits)
                "Boss, $digits par call lagaun? Haan ya Nahi boliye 📞"
            } else when (val c = g.device.lookupContact(cmd.target)) {
                is DeviceController.Contact.Found -> {
                    pendingCall.value = PendingCall(c.name, c.number)
                    "Boss, ${c.name} (${c.number}) ko call lagaun? Haan ya Nahi boliye 📞"
                }
                DeviceController.Contact.NoPermission -> "Boss, naam se call karne ke liye Contacts permission chahiye 🥺 Settings → App permissions me de dijiye, ya number bol dijiye."
                DeviceController.Contact.NotFound -> "Boss, '${cmd.target}' naam ka contact nahi mila 🥺"
            }
        }
        is Command.SaveNote -> {
            g.notes.add(TextItem(System.nanoTime(), cmd.text, System.currentTimeMillis()))
            pick("Note kar liya Boss 📝: ${cmd.text}", "Ho gaya, likh liya ✨: ${cmd.text}")
        }
        Command.ShowNotes -> {
            val n = g.notes.items.value.takeLast(5)
            if (n.isEmpty()) "Abhi koi note nahi hai Boss 📝" else "Aapke notes:\n" + n.joinToString("\n") { "• ${it.text}" }
        }
        is Command.Remind -> say(g.device.scheduleReminder(cmd.minutes, cmd.text))
        is Command.Calc -> try {
            "${cmd.expr} = ${Calculator.format(Calculator.eval(cmd.expr))} 🧮"
        } catch (e: Exception) { "Boss, ye hisaab mujhse nahi hua 🥺 (${e.message})" }
        is Command.Remember ->
            if (!s.memoryEnabled) "Boss, memory abhi band hai. Settings → Privacy & memory me on kijiye, phir yaad rakhungi 💭"
            else { g.memory.add(TextItem(System.nanoTime(), cmd.text, System.currentTimeMillis())); "Yaad rakh liya Boss 💕: ${cmd.text}" }
    }

    private suspend fun askGemini(s: AppSettings): Reply {
        if (!g.gemini.hasKey())
            return Reply("Boss, mujhe sochne ke liye Gemini API key chahiye 🥺 Settings → Gemini API me daal dijiye.")
        val system = Persona.build(s, g.memory.items.value.map { it.text })
        val hist = g.chat.items.value.takeLast(16).dropWhile { !it.isUser }
        return when (val r = g.gemini.generate(s.model, system, hist, s.webSearch)) {
            is AiResult.Ok -> Reply(
                if (r.sources.isEmpty()) r.text else r.text + "\n\n🔎 Sources: " + r.sources.joinToString(", "),
                r.text
            )
            is AiResult.Err -> Reply("Boss, Gemini se baat nahi ho paayi 🥺\n${r.message}", "Boss, Gemini se baat nahi ho paayi. Settings me error dekh lijiye.")
        }
    }

    suspend fun confirmCall(accept: Boolean) {
        val pc = pendingCall.value ?: return
        pendingCall.value = null
        val t = if (accept) say(g.device.placeCall(pc.number)) else "Theek hai Boss, call cancel kar diya 💕"
        addMsg("model", t)
        speakReply(t)
    }

    // ------------------------------------------------------------------ voice turns
    suspend fun greet() {
        val t = "Ji Boss! ❤️ Main yahin hoon. Bataiye kya karna hai?"
        addMsg("model", t)
        markHappy(t)
        speakReply(t)
    }

    private fun isStop(text: String): Boolean {
        val w = text.lowercase().split(Regex("[^\\p{L}\\p{M}]+")).filter { it.isNotEmpty() }
        return w.size <= 3 && w.any { it in setOf("stop", "bas", "bye", "alvida", "ruko", "बस", "रुको", "चुप") }
    }

    /** One spoken conversation (several turns when "continuous mode" is on). */
    suspend fun voiceConversation(firstText: String? = null) {
        if (!hasMic()) { addMsg("model", "Boss, microphone permission chahiye 🎤 Settings → App permissions me allow kijiye."); return }
        micLock.withLock {
            try {
                var seed = firstText
                var turns = 0
                while (true) {
                    val text: String = seed ?: run {
                        g.tts.stop()
                        avatarState.value = AvatarState.Listening
                        when (val r = g.stt.listenOnce()) {
                            is SttResult.Text -> r.text
                            is SttResult.Error -> {
                                if (turns == 0 && r.code != -2) addMsg("model", "🎤 ${r.message} Phir se mic dabaiye Boss.")
                                return
                            }
                        }
                    }
                    seed = null
                    if (isStop(text)) { val t = pick("Theek hai Boss, main yahin hoon 💕", "Okay Boss, bas bula lena ❤️"); addMsg("model", t); speakReply(t); return }
                    handleUserText(text)
                    turns++
                    if (!g.settings.settings.value.continuous) return
                }
            } finally {
                if (avatarState.value == AvatarState.Listening) avatarState.value = AvatarState.Idle
            }
        }
    }

    // ------------------------------------------------------------------ wake word
    suspend fun listenForWakeWord(): WakeResult {
        if (!hasMic()) return WakeResult.Fatal("Microphone permission missing")
        while (g.tts.speaking.value || busy.value) delay(300) // don't listen to ourselves
        val s = g.settings.settings.value
        wakeStatus.value = "Listening for \"${s.wakeWord}\"…"
        val r = micLock.withLock { g.stt.listenOnce(foreground = false) }
        return when (r) {
            is SttResult.Text -> WakeWord.match(r.text, s.wakeWord)?.let { WakeResult.Detected(it) } ?: WakeResult.None(250)
            is SttResult.Error -> when {
                r.fatal -> WakeResult.Fatal(r.message)
                r.code == 8 -> WakeResult.None(1500)
                r.benign -> WakeResult.None(250)
                else -> WakeResult.None(4000)
            }
        }
    }

    suspend fun wakeTurn(rest: String) {
        wakeStatus.value = "Wake word heard!"
        if (rest.length >= 3) voiceConversation(rest)
        else { greet(); voiceConversation() }
    }

    @Suppress("unused")
    private fun now() = SimpleDateFormat("HH:mm", Locale.US).format(Date())
}
