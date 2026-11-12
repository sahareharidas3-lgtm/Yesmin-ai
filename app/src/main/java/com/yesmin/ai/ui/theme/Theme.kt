package com.yesmin.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val YPink = Color(0xFFFF6FA8)
val YDeepPink = Color(0xFFD6336C)
val YLavender = Color(0xFFB79CFF)
val YViolet = Color(0xFF7C5CDB)

private val Light = lightColorScheme(
    primary = YDeepPink, onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD6E8), onPrimaryContainer = Color(0xFF3D0A22),
    secondary = YViolet, onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9DEFF), onSecondaryContainer = Color(0xFF24104F),
    background = Color(0xFFFFF5FA), onBackground = Color(0xFF2B1B3D),
    surface = Color(0xFFFFF5FA), onSurface = Color(0xFF2B1B3D),
    surfaceVariant = Color(0xFFF3E6F5), onSurfaceVariant = Color(0xFF5B4A66)
)
private val Dark = darkColorScheme(
    primary = Color(0xFFFF8FBF), onPrimary = Color(0xFF3D0A22),
    primaryContainer = Color(0xFF5B2243), onPrimaryContainer = Color(0xFFFFD6E8),
    secondary = Color(0xFFC7B2FF), onSecondary = Color(0xFF24104F),
    secondaryContainer = Color(0xFF3B2E66), onSecondaryContainer = Color(0xFFE9DEFF),
    background = Color(0xFF150F22), onBackground = Color(0xFFF4E9FF),
    surface = Color(0xFF150F22), onSurface = Color(0xFFF4E9FF),
    surfaceVariant = Color(0xFF2A2040), onSurfaceVariant = Color(0xFFD0C2DD)
)

@Composable
fun YesminTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
