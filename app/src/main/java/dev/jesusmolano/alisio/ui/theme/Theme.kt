package dev.jesusmolano.alisio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.jesusmolano.alisio.R

/** Tokens de docs/PLAN.md §6 que Material 3 no tiene con ese nombre. */
object AlisioTokens {
    val Background = Color(0xFF0B0C0E)
    val Surface = Color(0xFF15171A)
    val SurfaceElevated = Color(0xFF1B1E22)
    val CardBorder = Color(0xFF23272C)
    val ControlBorder = Color(0xFF2A2E34)
    val Text = Color(0xFFF2F3F5)
    val TextSecondary = Color(0xFF9AA0A8)
    val TextTertiary = Color(0xFF8A9099)

    /** Acentos de §6; el primero es el de por defecto. */
    val Accents = listOf(Color(0xFF6FD6C6), Color(0xFF8FB4FF), Color(0xFFF2B872), Color(0xFFC9A7FF))

    /**
     * Acento elegido en los ajustes. Es estado observable: lo fija [AlisioTheme] y todo lo que lo
     * lee al componerse o al dibujarse se repinta cuando cambia.
     */
    val Accent: Color get() = accentState.value

    private val accentState = mutableStateOf(Accents.first())

    internal fun useAccent(color: Color) {
        accentState.value = color
    }

    val AccentSoft: Color get() = Accent.copy(alpha = 0.14f)

    val CardShape = RoundedCornerShape(22.dp)
    val PillShape = RoundedCornerShape(28.dp)
    val DockIconShape = RoundedCornerShape(17.dp)
    val RowShape = RoundedCornerShape(20.dp)

    /** Reloj del Inicio: Space Grotesk Light, 112 sp. */
    val Clock =
        TextStyle(
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Light,
            fontSize = 112.sp,
            lineHeight = 112.sp,
            letterSpacing = (-0.045).em
        )

    /** Etiqueta de sección: se escribe en mayúsculas. */
    val SectionLabel =
        TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 0.12.em,
            color = TextTertiary
        )

    val DockLabel = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.5.sp)
}

private fun colorScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = AlisioTokens.Background,
    background = AlisioTokens.Background,
    onBackground = AlisioTokens.Text,
    surface = AlisioTokens.Surface,
    onSurface = AlisioTokens.Text,
    surfaceContainerLow = AlisioTokens.Surface,
    surfaceContainer = AlisioTokens.SurfaceElevated,
    onSurfaceVariant = AlisioTokens.TextSecondary,
    outline = AlisioTokens.ControlBorder,
    outlineVariant = AlisioTokens.CardBorder
)

private val AlisioTypography =
    Typography().run {
        copy(
            displayMedium = displayMedium.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Light),
            displaySmall = displaySmall.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium),
            // Título de pantalla.
            headlineLarge = headlineLarge.copy(
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Medium,
                fontSize = 32.sp
            ),
            // Título de fila.
            titleMedium = titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            bodyLarge = bodyLarge.copy(fontFamily = Inter, fontSize = 15.sp, letterSpacing = 0.sp),
            bodyMedium = bodyMedium.copy(fontFamily = Inter, fontSize = 13.sp),
            labelLarge = labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
            labelMedium = labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
            labelSmall = labelSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium)
        )
    }

private val AlisioShapes =
    Shapes(
        small = RoundedCornerShape(12.dp),
        medium = AlisioTokens.RowShape,
        large = AlisioTokens.CardShape,
        extraLarge = AlisioTokens.PillShape
    )

/** @param accent índice en [AlisioTokens.Accents]; uno fuera de rango usa el de por defecto. */
@Composable
fun AlisioTheme(accent: Int = 0, content: @Composable () -> Unit) {
    val color = AlisioTokens.Accents.getOrElse(accent) { AlisioTokens.Accents.first() }
    SideEffect { AlisioTokens.useAccent(color) }
    MaterialTheme(
        colorScheme = remember(color) { colorScheme(color) },
        typography = AlisioTypography,
        shapes = AlisioShapes,
        content = content
    )
}
