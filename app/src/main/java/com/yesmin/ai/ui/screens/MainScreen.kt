package com.yesmin.ai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.luminance
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yesmin.ai.assistant.AvatarState
import com.yesmin.ai.data.ChatMessage
import com.yesmin.ai.ui.MainViewModel
import com.yesmin.ai.ui.avatar.YesminAvatar
import com.yesmin.ai.ui.theme.YDeepPink
import com.yesmin.ai.ui.theme.YLavender
import com.yesmin.ai.ui.theme.YPink
import com.yesmin.ai.ui.theme.YViolet
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun MainScreen(vm: MainViewModel, onSettings: () -> Unit, onNotes: () -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val msgs by vm.messages.collectAsStateWithLifecycle()
    val avatar by vm.avatar.collectAsStateWithLifecycle()
    val happy by vm.happy.collectAsStateWithLifecycle()
    val kick by vm.mouthKick.collectAsStateWithLifecycle()
    val listening by vm.listening.collectAsStateWithLifecycle()
    val partial by vm.partial.collectAsStateWithLifecycle()
    val rms by vm.rms.collectAsStateWithLifecycle()
    val status by vm.apiStatus.collectAsStateWithLifecycle()
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    val pending by vm.pendingCall.collectAsStateWithLifecycle()
    val wake by vm.wakeStatus.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var showConsent by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.lastIndex) }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.toggleVoice() }
    fun onMic() {
        when {
            !s.voiceConsent -> showConsent = true
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ->
                micPerm.launch(Manifest.permission.RECORD_AUDIO)
            else -> vm.toggleVoice()
        }
    }

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bg = if (dark) listOf(Color(0xFF2A1240), Color(0xFF150F22)) else listOf(Color(0xFFFFE4F1), Color(0xFFEFE6FF))

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(bg)).statusBarsPadding().navigationBarsPadding().imePadding()
    ) {
        // ---- top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("YESMIN AI", style = TextStyle(brush = Brush.horizontalGradient(listOf(YDeepPink, YViolet)), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold))
                val (label, color) = when {
                    !hasKey -> "No API key" to Color(0xFFD32F2F)
                    status.connected == true -> "Connected" to Color(0xFF2E9E5B)
                    status.connected == false -> "Gemini error" to Color(0xFFD32F2F)
                    else -> "Key saved · untested" to Color(0xFFE08A00)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onSettings() }) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(6.dp))
                    Text(label + if (s.wakeWordEnabled) "  ·  Wake: $wake" else "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            IconButton(onClick = onNotes) { Icon(Icons.Filled.StickyNote2, "Notes") }
            IconButton(onClick = { vm.toggleMute() }) {
                Icon(if (s.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, if (s.muted) "Unmute voice" else "Mute voice")
            }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
        }

        // ---- avatar
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
            YesminAvatar(avatar, happy, kick, s.hairColor, s.eyeColor)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 4.dp)) {
                if (listening) {
                    ListeningBars(rms)
                    if (partial.isNotBlank()) Text("“$partial”", fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(horizontal = 24.dp))
                } else {
                    val t = when (avatar) {
                        AvatarState.Thinking -> "Yesmin soch rahi hai…"
                        AvatarState.Talking -> "Yesmin bol rahi hai…"
                        else -> if (busy) "…" else ""
                    }
                    if (t.isNotEmpty()) Text(t, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (!hasKey) {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).clickable { onSettings() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) { Text("🔑 Boss, Gemini API key daaliye taaki main baat kar sakun (Settings → Gemini API). Phone commands bina key ke bhi chalte hain.", Modifier.padding(10.dp), fontSize = 12.sp) }
            Spacer(Modifier.height(4.dp))
        }

        // ---- chat
        LazyColumn(
            state = listState, modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (msgs.isEmpty()) item {
                Text("Boss, mujhse kuch bhi poochiye ya boliye: “open YouTube”, “torch on karo”, “romantic songs bajao” ❤️",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            }
            items(msgs, key = { it.id }) { Bubble(it) }
        }

        // ---- suggestions
        val chips = remember { suggestions() }
        LazyRow(Modifier.padding(vertical = 4.dp), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chips) { c -> AssistChip(onClick = { vm.sendText(c) }, label = { Text(c, fontSize = 12.sp) }) }
        }

        // ---- input
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Yesmin se baat kijiye…") }, shape = RoundedCornerShape(28.dp), maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.sendText(input); input = "" })
            )
            Spacer(Modifier.width(8.dp))
            if (input.isNotBlank()) {
                FilledIconButton(onClick = { vm.sendText(input); input = "" }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            } else MicButton(active = listening, onClick = ::onMic)
        }
    }

    if (pending != null) {
        AlertDialog(
            onDismissRequest = { vm.confirmCall(false) },
            title = { Text("Call confirm karein?") },
            text = { Text("${pending!!.name}\n${pending!!.number}") },
            confirmButton = { TextButton(onClick = { vm.confirmCall(true) }) { Text("Haan, call karo") } },
            dismissButton = { TextButton(onClick = { vm.confirmCall(false) }) { Text("Nahi") } }
        )
    }
    if (showConsent) {
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text("Microphone use ki ijazat 🎤") },
            text = {
                Text("Boss, voice ke liye main aapki awaaz sunungi. Speech recognition Android/Google ki speech service se hota hai, jo awaaz Google ke servers par bhej sakti hai (Settings me 'Prefer offline' se on-device try kar sakte hain). " +
                    "YESMIN khud awaaz record ya save nahi karti. Sirf mic button dabane par (ya aapke wake-word ON karne par) sunti hai. Kabhi bhi Settings → Privacy me band kar sakte hain.")
            },
            confirmButton = { TextButton(onClick = { vm.update { it.copy(voiceConsent = true) }; showConsent = false; onMic() }) { Text("Haan, allow") } },
            dismissButton = { TextButton(onClick = { showConsent = false }) { Text("Abhi nahi") } }
        )
    }
}

private fun suggestions(): List<String> {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val first = when (h) { in 5..11 -> "Good morning Yesmin ☀️"; in 12..16 -> "Lunch me kya khaun?"; in 17..21 -> "Romantic songs bajao"; else -> "Mujhe neend nahi aa rahi" }
    return listOf(first, "Open YouTube", "Torch on karo", "Ek joke sunao", "Translate to English: mujhe tum pasand ho", "Explain Kotlin coroutines", "10 minute baad yaad dilao chai", "25 times 4")
}

@Composable
private fun Bubble(m: ChatMessage) {
    val user = m.isUser
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (user) 18.dp else 4.dp, bottomEnd = if (user) 4.dp else 18.dp),
            color = if (user) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            MessageText(m.text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun MessageText(text: String, modifier: Modifier) {
    Column(modifier) {
        text.split("```").forEachIndexed { i, part ->
            if (part.isBlank()) return@forEachIndexed
            if (i % 2 == 1) {
                val code = part.substringAfter('\n', part).trim()
                Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color(0x22000000), RoundedCornerShape(8.dp))
                        .horizontalScroll(rememberScrollState()).padding(8.dp))
            } else Text(part.trim(), fontSize = 15.sp)
        }
    }
}

@Composable
private fun ListeningBars(level: Float) {
    val t = rememberInfiniteTransition(label = "bars")
    val ph by t.animateFloat(0f, 6.283f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "ph")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(40.dp)) {
        repeat(11) { i ->
            val h = 6f + 34f * (0.2f + 0.8f * level) * abs(sin(ph + i * 0.6f))
            Box(Modifier.width(5.dp).height(h.dp).clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(YPink, YLavender))))
        }
    }
}

@Composable
private fun MicButton(active: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "mic")
    val p by t.animateFloat(1f, 1.14f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    Box(
        Modifier.size(58.dp).scale(if (active) p else 1f).clip(CircleShape)
            .background(Brush.linearGradient(listOf(YPink, YViolet))).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(if (active) Icons.Filled.Stop else Icons.Filled.Mic, if (active) "Stop listening" else "Speak", tint = Color.White) }
}
