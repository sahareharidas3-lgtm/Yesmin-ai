@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.yesmin.ai.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yesmin.ai.ui.MainViewModel
import com.yesmin.ai.ui.avatar.AvatarPalette
import com.yesmin.ai.ui.avatar.YesminAvatar
import com.yesmin.ai.assistant.AvatarState

private val sections = listOf(
    "gemini" to "🧠  Gemini API", "voice" to "🎙️  Voice", "wake" to "👂  Wake word", "avatar" to "💖  Avatar appearance",
    "language" to "🌐  Language", "personality" to "🎀  Personality", "permissions" to "🔐  App permissions",
    "privacy" to "🛡️  Privacy & memory", "theme" to "🎨  Theme", "about" to "ℹ️  About YESMIN"
)

@Composable
fun SettingsRouter(screen: String, vm: MainViewModel, onNavigate: (String) -> Unit) {
    val section = screen.removePrefix("settings:").takeIf { screen.startsWith("settings:") }
    if (section == null) {
        SettingsScaffold("Settings", onBack = { onNavigate("main") }) {
            sections.forEach { (id, label) ->
                Card(Modifier.fillMaxWidth().clickable { onNavigate("settings:$id") }) {
                    Text(label, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        return
    }
    val title = sections.first { it.first == section }.second.trim()
    SettingsScaffold(title, onBack = { onNavigate("settings") }) {
        when (section) {
            "gemini" -> GeminiSection(vm)
            "voice" -> VoiceSection(vm)
            "wake" -> WakeSection(vm)
            "avatar" -> AvatarSection(vm)
            "language" -> LanguageSection(vm)
            "personality" -> PersonalitySection(vm)
            "permissions" -> PermissionsSection()
            "privacy" -> PrivacySection(vm)
            "theme" -> ThemeSection(vm)
            else -> AboutSection()
        }
    }
}

@Composable
private fun GeminiSection(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    val status by vm.apiStatus.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val msg by vm.testMsg.collectAsStateWithLifecycle()
    val working by vm.working.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    var key by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }

    InfoCard("Your key is encrypted with the Android Keystore and stored only on this phone. It is sent only to Google's Gemini API (in a header) and is never written into the app's code. Create a free key at Google AI Studio.")
    TextButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) { Text("Open aistudio.google.com/apikey") }
    OutlinedTextField(
        key, { key = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(if (hasKey) "Replace API key" else "Gemini API key") },
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Show/hide") } }
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { vm.saveKey(key); key = "" }, enabled = key.isNotBlank()) { Text("Save key") }
        if (hasKey) OutlinedButton(onClick = { vm.clearKey() }) { Text("Remove key") }
    }
    if (hasKey) Text("Saved key: ${vm.keyTail()}", style = MaterialTheme.typography.bodySmall)

    SectionTitle("Model")
    Dropdown("Gemini model", s.model, models.map { it to it }) { m -> vm.update { it.copy(model = m) } }
    OutlinedButton(onClick = { vm.fetchModels() }, enabled = hasKey && !working) { Text("Fetch available models from my key") }

    SectionTitle("Connection")
    Button(onClick = { vm.testApi() }, enabled = hasKey && !working) { Text(if (working) "Please wait…" else "Test connection") }
    if (msg.isNotBlank()) Text(msg)
    Card { Column(Modifier.padding(12.dp)) {
        Text("Status: ${status.message}", style = MaterialTheme.typography.bodyMedium)
        if (status.log.isNotEmpty()) {
            Spacer(Modifier.height(6.dp)); Text("Recent errors", style = MaterialTheme.typography.labelMedium)
            status.log.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    } }

    SectionTitle("Response preferences")
    SwitchRow("Google Search grounding", "Lets Gemini look up current information on the web (needs a model that supports it).", s.webSearch) { v -> vm.update { it.copy(webSearch = v) } }
    ChoiceRow(listOf("short" to "Short (best for voice)", "normal" to "Normal", "detailed" to "Detailed"), s.responseLength) { v -> vm.update { it.copy(responseLength = v) } }
}

@Composable
private fun VoiceSection(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val voices by vm.voices.collectAsStateWithLifecycle()
    InfoCard("Android does not tell apps whether a voice is male or female. Pick a voice, press Preview, and choose the one you like. Raising pitch makes it cuter. Install more voices in Android Settings → Text-to-speech.")
    Dropdown("Voice", s.voiceName, listOf("" to "Automatic (by language)") + voices.map { it.name to it.label }) { v -> vm.update { it.copy(voiceName = v) } }
    Text("Pitch: ${"%.2f".format(s.pitch)}"); Slider(s.pitch, { v -> vm.update { it.copy(pitch = v) } }, valueRange = 0.7f..1.8f)
    Text("Speed: ${"%.2f".format(s.speechRate)}"); Slider(s.speechRate, { v -> vm.update { it.copy(speechRate = v) } }, valueRange = 0.6f..1.5f)
    Button(onClick = { vm.previewVoice() }) { Text("Preview voice") }
    SwitchRow("Continuous conversation", "After she answers, YESMIN listens again until you say “stop” or stay silent.", s.continuous) { v -> vm.update { it.copy(continuous = v) } }
    SwitchRow("Prefer offline recognition", "Asks Android to recognise speech on-device when a language pack is installed (keeps audio off Google servers). Falls back to online if unavailable.", s.preferOffline) { v -> vm.update { it.copy(preferOffline = v) } }
    SwitchRow("Mute voice replies", null, s.muted) { v -> vm.update { it.copy(muted = v) } }
}

@Composable
private fun WakeSection(vm: MainViewModel) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val wake by vm.wakeStatus.collectAsStateWithLifecycle()
    var note by remember { mutableStateOf("") }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        note = if (ok) { if (vm.setWake(true)) "" else "Android refused to start the listening service. Keep YESMIN open and try again." }
        else "Microphone permission is required."
    }
    InfoCard("How it works: when ON, a foreground service (with a visible notification) listens using Android's speech recognizer and reacts when it hears “Yesmin”. This is NOT a dedicated low-power wake-word chip. It uses battery, may send audio to Google's speech service, can be stopped by Vivo/Android battery managers, and may make the recognizer beep. Always-on listening cannot be guaranteed on every phone. The mic button always works as a fallback.")
    SwitchRow("Enable wake word “${s.wakeWord}”", "Status: $wake", s.wakeWordEnabled) { on ->
        note = ""
        if (!on) vm.setWake(false)
        else if (!s.voiceConsent) note = "First press the mic button on the main screen once and accept the microphone consent."
        else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) micPerm.launch(Manifest.permission.RECORD_AUDIO)
        else if (!vm.setWake(true)) note = "Android refused to start the listening service. Keep YESMIN open and try again."
    }
    if (note.isNotBlank()) Text(note, color = MaterialTheme.colorScheme.error)
    var w by remember(s.wakeWord) { mutableStateOf(s.wakeWord) }
    OutlinedTextField(w, { w = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Wake word (default: yesmin)") })
    Button(onClick = { vm.update { it.copy(wakeWord = w.trim().lowercase().ifBlank { "yesmin" }) } }) { Text("Save wake word") }
    SwitchRow("Listen only while screen is on", "Saves battery. Android usually blocks microphone listening when the screen is off anyway.", s.wakeOnlyScreenOn) { v -> vm.update { it.copy(wakeOnlyScreenOn = v) } }
    SectionTitle("Keep it alive (battery optimisation)")
    OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open battery optimisation list") }
    Text("Vivo (Funtouch/OriginOS): Settings → Battery → Background power consumption → YESMIN AI → Allow. Also allow Autostart (i Manager / App permissions) and lock the app in Recents. Menu names vary by version.", style = MaterialTheme.typography.bodySmall)
    InfoCard("Android limit: from the background, Android 10+ will not let apps open other apps' screens. When the wake word triggers while YESMIN isn't visible, voice answers, torch, volume, notes and reminders work, and “open app / music / call” actions arrive as a tap-to-open notification.")
}

@Composable
private fun AvatarSection(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxWidth().height(260.dp)) { YesminAvatar(AvatarState.Idle, true, 0, s.hairColor, s.eyeColor) }
    Dropdown("Hair colour", s.hairColor.toString(), AvatarPalette.hair.mapIndexed { i, p -> i.toString() to p.first }) { v -> vm.update { it.copy(hairColor = v.toInt()) } }
    Dropdown("Eye colour", s.eyeColor.toString(), AvatarPalette.eyes.mapIndexed { i, p -> i.toString() to p.first }) { v -> vm.update { it.copy(eyeColor = v.toInt()) } }
}

@Composable
private fun LanguageSection(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    InfoCard("Controls how YESMIN replies and which language the speech recogniser expects. Hinglish/Hindi use hi-IN recognition (it also understands English words); English uses en-IN.")
    ChoiceRow(listOf("hinglish" to "Hinglish (Hindi + English, Roman script)", "hindi" to "हिन्दी (Devanagari)", "english" to "English"), s.language) { v -> vm.update { it.copy(language = v) } }
}

@Composable
private fun PersonalitySection(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    ChoiceRow(listOf("romantic" to "Romantic & sweet", "playful" to "Playful & teasing", "caring" to "Caring & supportive", "smart" to "Smart & professional"), s.personality) { v -> vm.update { it.copy(personality = v) } }
    var fav by remember(s.favouriteSong) { mutableStateOf(s.favouriteSong) }
    OutlinedTextField(fav, { fav = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("My favourite song (for “play my favourite song”)") })
    Button(onClick = { vm.update { it.copy(favouriteSong = fav.trim()) } }) { Text("Save favourite song") }
}

@Composable
private fun PermissionsSection() {
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    key(tick) {
        PermRow("Microphone", "Voice commands and wake word.", has(Manifest.permission.RECORD_AUDIO)) { launcher.launch(Manifest.permission.RECORD_AUDIO) }
        if (Build.VERSION.SDK_INT >= 33)
            PermRow("Notifications", "Reminders, listening-service notice, tap-to-open actions.", has(Manifest.permission.POST_NOTIFICATIONS)) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        PermRow("Contacts", "Optional: call people by name.", has(Manifest.permission.READ_CONTACTS)) { launcher.launch(Manifest.permission.READ_CONTACTS) }
        PermRow("Phone (direct call)", "Optional: place the call directly after you confirm. Without it, the dialer opens.", has(Manifest.permission.CALL_PHONE)) { launcher.launch(Manifest.permission.CALL_PHONE) }
        val pm = ctx.getSystemService(PowerManager::class.java)
        val ignoring = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        PermRow("Unrestricted battery", "Helps wake-word survive in background (optional).", ignoring) {
            ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    OutlinedButton(onClick = {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }) { Text("Open Android app settings") }
    Text("Flashlight, volume and opening apps need no permission. If you denied a permission twice, Android won't ask again — use the button above.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PermRow(name: String, why: String, granted: Boolean, onGrant: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(why, style = MaterialTheme.typography.bodySmall)
            }
            if (granted) Text("✅ On") else Button(onClick = onGrant) { Text("Allow") }
        }
    }
}

@Composable
private fun PrivacySection(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val mem by vm.memory.collectAsStateWithLifecycle()
    var newMem by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    InfoCard("Everything (chat, notes, memories, settings) is stored only on this phone. Text you send to YESMIN's AI goes to Google's Gemini API. YESMIN never records or saves your audio; speech recognition is done by Android's speech service.")
    SwitchRow("Microphone consent", "Required for any listening (mic button and wake word).", s.voiceConsent) { v ->
        vm.update { it.copy(voiceConsent = v) }; if (!v) vm.setWake(false)
    }
    SwitchRow("Save chat history", "Off = chat disappears when the app is closed.", s.saveHistory) { v -> vm.update { it.copy(saveHistory = v) } }
    SwitchRow("Long-term memory", "Lets YESMIN keep facts you approve (say “remember that …”) and use them in replies. Sent to Gemini as context.", s.memoryEnabled) { v -> vm.update { it.copy(memoryEnabled = v) } }
    if (s.memoryEnabled) {
        OutlinedTextField(newMem, { newMem = it }, Modifier.fillMaxWidth(), label = { Text("Add a memory") })
        Button(onClick = { vm.addMemory(newMem); newMem = "" }, enabled = newMem.isNotBlank()) { Text("Add") }
    }
    mem.forEach { m ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("• ${m.text}", Modifier.weight(1f))
            IconButton(onClick = { vm.deleteMemory(m.id) }) { Icon(Icons.Filled.Delete, "Forget") }
        }
    }
    OutlinedButton(onClick = { vm.clearChat() }) { Text("Clear chat history") }
    Button(onClick = { confirm = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete ALL my data") }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete everything?") },
        text = { Text("Chat, notes, memories, API key and settings will be erased from this phone.") },
        confirmButton = { TextButton(onClick = { vm.deleteEverything(); confirm = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
    )
}

@Composable
private fun ThemeSection(vm: MainViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    ChoiceRow(listOf("system" to "Follow system", "light" to "Light (soft pink)", "dark" to "Dark (lavender night)"), s.theme) { v -> vm.update { it.copy(theme = v) } }
}

@Composable
private fun AboutSection() {
    Text("YESMIN AI 1.0", style = MaterialTheme.typography.titleLarge)
    Text("A cute, romantic AI companion for Android, powered by Google Gemini.")
    InfoCard("Honest notes: the AI needs internet + your Gemini key. Speech recognition and most TTS voices come from Google/Android services on your phone. The avatar is a drawn 2.5D character, not a rigged 3D model. Wake-word uses Android's speech recognizer (not an offline engine) and cannot be guaranteed always-on. Android blocks background app launches, so some actions arrive as a tap-to-open notification.")
    Text("Voice commands: open <app>, torch on/off, volume up/down/50, call <name/number> (asks confirmation), play <song>, note: …, remind me in 10 minutes …, 25 times 4, open wifi/bluetooth/settings, open camera/gallery.", style = MaterialTheme.typography.bodySmall)
}
