package dev.jesusmolano.alisio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colores base de docs/PLAN.md §6. El tema completo (tipografías y formas) llega en la fase 1.
private val AlisioColors =
    darkColorScheme(
        primary = Color(0xFF6FD6C6),
        onPrimary = Color(0xFF0B0C0E),
        background = Color(0xFF0B0C0E),
        onBackground = Color(0xFFF2F3F5),
        surface = Color(0xFF15171A),
        onSurface = Color(0xFFF2F3F5),
        onSurfaceVariant = Color(0xFF9AA0A8),
        outline = Color(0xFF2A2E34)
    )

@Composable
fun AlisioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AlisioColors, content = content)
}
