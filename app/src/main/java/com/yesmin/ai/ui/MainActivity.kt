package com.yesmin.ai.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yesmin.ai.assistant.AppState
import com.yesmin.ai.ui.screens.MainScreen
import com.yesmin.ai.ui.screens.NotesScreen
import com.yesmin.ai.ui.screens.SettingsRouter
import com.yesmin.ai.ui.theme.YesminTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val s by vm.settings.collectAsStateWithLifecycle()
            val dark = when (s.theme) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
            YesminTheme(dark) { AppRoot(vm) }
        }
    }

    override fun onStart() { super.onStart(); AppState.foreground = true }
    override fun onStop() { super.onStop(); AppState.foreground = false }
}

@Composable
private fun AppRoot(vm: MainViewModel) {
    var screen by rememberSaveable { mutableStateOf("main") }   // main | notes | settings | settings:<section>
    BackHandler(screen != "main") { screen = if (screen.startsWith("settings:")) "settings" else "main" }
    when {
        screen == "main" -> MainScreen(vm, onSettings = { screen = "settings" }, onNotes = { screen = "notes" })
        screen == "notes" -> NotesScreen(vm) { screen = "main" }
        else -> SettingsRouter(screen, vm, onNavigate = { screen = it })
    }
}
