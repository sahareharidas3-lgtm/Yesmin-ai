package com.yesmin.ai.assistant

/** True while MainActivity is visible. Android 10+ only lets visible apps start other apps' screens. */
object AppState { @Volatile var foreground = false }

enum class AvatarState { Idle, Listening, Thinking, Talking }

sealed class WakeResult {
    data class None(val pauseMs: Long = 300) : WakeResult()
    data class Detected(val rest: String) : WakeResult()
    data class Fatal(val message: String) : WakeResult()
}

data class PendingCall(val name: String, val number: String)
