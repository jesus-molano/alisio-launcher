package dev.jesusmolano.alisio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.jesusmolano.alisio.core.theme.CUSTOM_ACCENT
import dev.jesusmolano.alisio.core.theme.FontChoice
import dev.jesusmolano.alisio.core.theme.PlateOpacity
import dev.jesusmolano.alisio.core.theme.dayAccent
import dev.jesusmolano.alisio.core.theme.daylight
import dev.jesusmolano.alisio.core.theme.sunMinutes
import dev.jesusmolano.alisio.core.time.ClockSize
import java.time.ZonedDateTime

/**
 * Las placas del pie del Inicio (píldora, Ahora, En curso y reproductor) con la opacidad elegida en
 * los ajustes (`Prefs.plateOpacity`). `MainActivity` la fija para toda la actividad, pero solo deben
 * leerla esas placas, en el Inicio y en su miniatura: lo demás flota con [AlisioTokens.Plate].
 */
val LocalHomePlate = staticCompositionLocalOf { HomePlate(AlisioTokens.Plate, AlisioTokens.TextSecondary) }

/**
 * El relleno de una placa del Inicio y el color de su texto secundario (motivo, artista, pista y
 * botones grises). Con la placa translúcida el gris de siempre no se lee sobre un fondo claro.
 */
data class HomePlate(val fill: Color, val secondary: Color)

/**
 * Tokens de docs/DISENO.md §2 que Material 3 no tiene con ese nombre. Ninguna pantalla define
 * colores, alfas, márgenes, radios ni tamaños de letra propios: se cambian aquí.
 */
object AlisioTokens {
    // Color y superficies (§2.4).
    val Background = Color(0xFF0B0C0E)
    val Surface = Color(0xFF15171A)
    val Text = Color(0xFFF2F3F5)
    val TextSecondary = Color(0xFF9AA0A8)

    /** Solo para el texto de marcador de un campo. */
    val TextTertiary = Color(0xFF8A9099)

    /**
     * Único relleno dentro de fondo opaco, hoja o placa: campo, chip sin elegir, botón tonal, avatar
     * sin foto, fondo de tesela, asa de hoja y pista del interruptor apagado.
     */
    val Fill = Text.copy(alpha = 0.06f)

    /** Todo lo que flota sobre el fondo de pantalla: píldora, Ahora, reproductor, islas, pila y hueco vacío. */
    val Plate = Surface.copy(alpha = 0.86f)

    /**
     * Las placas del Inicio con la opacidad elegida. Medias y translúcidas llevan el texto secundario
     * en [Text] al 80 %: sobre una zona blanca del fondo con el velo «Poco», el gris quedaría cerca de
     * 2,5:1 y este pasa de 4,5:1. Por debajo de [PlateOpacity.LOW] ni el texto principal se leería.
     */
    fun homePlate(opacity: PlateOpacity): HomePlate = when (opacity) {
        PlateOpacity.SOLID -> HomePlate(Plate, TextSecondary)
        PlateOpacity.MEDIUM -> HomePlate(Surface.copy(alpha = 0.62f), Text.copy(alpha = 0.8f))
        PlateOpacity.LOW -> HomePlate(Surface.copy(alpha = 0.40f), Text.copy(alpha = 0.8f))
    }

    /**
     * Las manchas de color que se mueven tras la bienvenida: los acentos a esta alfa sobre
     * [Background]. Con menos, los tres se mezclaban en un gris apagado (visto en el S26); el texto
     * se sigue leyendo porque el pie lleva [Scrim].
     */
    const val WELCOME_GLOW_ALPHA = 0.7f

    /** Velo uniforme de Zonas sobre el fondo de pantalla. */
    val ZonesVeil = Background.copy(alpha = 0.70f)

    /** Velo de Trazo abierto: tapa el Inicio mientras dura el gesto. */
    val Scrim = Background.copy(alpha = 0.86f)

    /** Contenido de una opción a la que le falta un requisito y de un destino de arrastre imposible. */
    const val DISABLED_ALPHA = 0.38f

    /** Acentos; el primero es el de por defecto. */
    val Accents = listOf(Color(0xFF6FD6C6), Color(0xFF8FB4FF), Color(0xFFF2B872), Color(0xFFC9A7FF))

    /**
     * Acento elegido en los ajustes. Es estado observable: lo fija [AlisioTheme] y todo lo que lo
     * lee al componerse o al dibujarse se repinta cuando cambia. Solo marca lo que hará Enter, lo
     * elegido, la acción principal y una señal viva (§2.5); nunca decora.
     */
    val Accent: Color get() = accentState.value

    private val accentState = mutableStateOf(Accents.first())

    internal fun useAccent(color: Color) {
        accentState.value = color
    }

    /** Relleno del destino de Enter y del chip elegido. */
    val AccentSoft: Color get() = Accent.copy(alpha = 0.14f)

    /**
     * Verde de WhatsApp en el canal principal: sus canales se reconocen por el color aunque el
     * dibujo sea el mismo que el de la llamada normal.
     */
    val WhatsAppGreen = Color(0xFF25D366)

    // Espaciado (§2.2), en rejilla de 4 dp.

    /** El filo: donde empieza todo el contenido, en todas las pantallas. */
    val Edge = 24.dp

    /** Margen exterior de placas, filas, campos y teselas; con sus 12 dp de relleno, su contenido cae en [Edge]. */
    val Outset = 12.dp

    /** Hueco del icono de una fila. */
    val IconSlot = 44.dp

    /** Del hueco del icono al texto: el segundo filo queda a [Edge] + [IconSlot] + [IconGap]. */
    val IconGap = 16.dp
    val RowMinHeight = 56.dp

    /** Aire antes de una etiqueta de sección. */
    val GroupGap = 28.dp

    /** Aire después de una etiqueta de sección. */
    val LabelGap = 8.dp

    /** Bajo un título de pantalla. */
    val TitleGap = 24.dp

    /** Bajo un título de hoja. */
    val SheetTitleGap = 20.dp

    /** Entre las piezas del pie del Inicio y entre islas. */
    val PlateGap = 12.dp
    val MinTouch = 48.dp

    /**
     * Alto de cada renglón bajo la hora (fecha, piezas y notas). Excepción a [MinTouch] elegida por el
     * dueño para que vayan juntos (docs/PLAN.md §11): Compose solo amplía el toque hacia donde no hay
     * otro renglón, así que los de en medio se tocan en unos 32 dp y solo el último gana algo abajo.
     */
    val InfoRow = 32.dp

    /** Lado del icono de una app en el dock, en Zonas y en las teselas. */
    val DockIconSize = 52.dp

    /** Dibujo de cada pieza de la línea bajo la hora: a la altura de las mayúsculas de la fecha. */
    val GlanceIconSize = 18.dp

    /** Lo que mide la hora del Inicio en cada tamaño, como fracción de [Clock]. */
    fun clockScale(size: ClockSize): Float = when (size) {
        ClockSize.SMALL -> 0.6f
        ClockSize.MEDIUM -> 0.8f
        ClockSize.LARGE -> 1f
    }

    // Radios (§2.3). No hay un quinto: los avatares van en círculo.
    val PillShape = RoundedCornerShape(28.dp)
    val CardShape = RoundedCornerShape(22.dp)
    val RowShape = RoundedCornerShape(20.dp)
    val DockIconShape = RoundedCornerShape(17.dp)

    /** Esquinas superiores de una hoja y del cuerpo de los ajustes. */
    val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    // Tipografía (§2.1) que no cabe en las ranuras de Material. Las familias salen de la elección
    // de los ajustes, por eso cada estilo se lee al componer.

    /** Reloj del Inicio. */
    val Clock: TextStyle
        @Composable @ReadOnlyComposable
        get() =
            TextStyle(
                fontFamily = LocalAlisioFonts.current.display,
                fontWeight = FontWeight.Light,
                fontSize = 112.sp,
                lineHeight = 112.sp,
                letterSpacing = (-0.045).em,
                // Cifras de ancho fijo: la hora no baila al cambiar de minuto. Inter, Space Grotesk y
                // Roboto traen «tnum» (comprobado en su tabla GSUB); Roboto ya las tiene así de serie.
                fontFeatureSettings = "tnum"
            )

    /** Cifra: el resultado de un cálculo y la hora de una vista previa. */
    val Numeral: TextStyle
        @Composable @ReadOnlyComposable
        get() =
            TextStyle(
                fontFamily = LocalAlisioFonts.current.display,
                fontWeight = FontWeight.Light,
                fontSize = 40.sp,
                lineHeight = 44.sp,
                letterSpacing = (-0.02).em
            )

    /** Etiqueta de sección: mayúscula solo inicial, sin interletrado. */
    val SectionLabel: TextStyle
        @Composable @ReadOnlyComposable
        get() =
            TextStyle(
                fontFamily = LocalAlisioFonts.current.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = TextSecondary
            )

    /** Nombre bajo un icono: dock, Zonas, Trazo y teselas. */
    val DockLabel: TextStyle
        @Composable @ReadOnlyComposable
        get() =
            TextStyle(
                fontFamily = LocalAlisioFonts.current.text,
                fontWeight = FontWeight.Medium,
                fontSize = 11.5.sp,
                lineHeight = 14.sp
            )

    /** Letra del índice de Trazo y de Resto. */
    val RailLetter: TextStyle
        @Composable @ReadOnlyComposable
        get() =
            TextStyle(
                fontFamily = LocalAlisioFonts.current.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
}

private val OnWallpaperShadowColor = Color.Black.copy(alpha = 0.45f)
private val OnWallpaperShadowBlur = 10.dp

/**
 * El estilo para un texto que va directo sobre el fondo de pantalla: siempre claro y opaco, con una
 * sombra sin desplazamiento que lo separa de un fondo claro (§3). No se usa dentro de una placa ni
 * sobre fondo opaco.
 */
@Composable
@ReadOnlyComposable
fun TextStyle.onWallpaper(): TextStyle {
    val blur = with(LocalDensity.current) { OnWallpaperShadowBlur.toPx() }
    return copy(color = AlisioTokens.Text, shadow = Shadow(OnWallpaperShadowColor, Offset.Zero, blur))
}

private fun colorScheme(accent: Color): androidx.compose.material3.ColorScheme {
    // Lo que Material pinta como contenedor o contorno usa el único relleno de la guía.
    val fill = AlisioTokens.Fill.compositeOver(AlisioTokens.Surface)
    return darkColorScheme(
        primary = accent,
        onPrimary = AlisioTokens.Background,
        background = AlisioTokens.Background,
        onBackground = AlisioTokens.Text,
        surface = AlisioTokens.Surface,
        onSurface = AlisioTokens.Text,
        surfaceContainerLow = AlisioTokens.Surface,
        surfaceContainer = fill,
        inverseSurface = AlisioTokens.Surface,
        inverseOnSurface = AlisioTokens.Text,
        onSurfaceVariant = AlisioTokens.TextSecondary,
        outline = fill,
        outlineVariant = fill
    )
}

private fun typography(fonts: AlisioFonts) = Typography().run {
    copy(
        displayMedium = displayMedium.copy(fontFamily = fonts.display, fontWeight = FontWeight.Light),
        displaySmall = displaySmall.copy(fontFamily = fonts.display, fontWeight = FontWeight.Medium),
        // Título de pantalla.
        headlineLarge =
            headlineLarge.copy(
                fontFamily = fonts.display,
                fontWeight = FontWeight.Medium,
                fontSize = 32.sp,
                lineHeight = 38.sp
            ),
        // Título de hoja y cabecera de persona.
        headlineSmall =
            headlineSmall.copy(
                fontFamily = fonts.display,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                lineHeight = 30.sp
            ),
        titleLarge = titleLarge.copy(fontFamily = fonts.text, fontWeight = FontWeight.Medium),
        // Título de fila. La jerarquía sale del tamaño y del color, no de la negrita.
        titleMedium =
            titleMedium.copy(
                fontFamily = fonts.text,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.sp
            ),
        titleSmall = titleSmall.copy(fontFamily = fonts.text),
        // Texto y campo.
        bodyLarge = bodyLarge.copy(fontFamily = fonts.text, fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
        // Secundario.
        bodyMedium =
            bodyMedium.copy(fontFamily = fonts.text, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
        bodySmall = bodySmall.copy(fontFamily = fonts.text),
        // Acción, botón y chip.
        labelLarge =
            labelLarge.copy(
                fontFamily = fonts.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp
            ),
        labelMedium = labelMedium.copy(fontFamily = fonts.text, fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(fontFamily = fonts.text, fontWeight = FontWeight.Medium)
    )
}

private val AlisioShapes =
    Shapes(
        small = AlisioTokens.RowShape,
        medium = AlisioTokens.RowShape,
        large = AlisioTokens.CardShape,
        extraLarge = AlisioTokens.PillShape
    )

// Pulsado (§2.6): el texto al 8 %, con la forma del elemento. Se define una vez para toda la app.
private const val PRESSED_ALPHA = 0.08f

@OptIn(ExperimentalMaterial3Api::class)
private val Pressed =
    RippleConfiguration(
        color = AlisioTokens.Text,
        rippleAlpha = RippleAlpha(PRESSED_ALPHA, PRESSED_ALPHA, PRESSED_ALPHA, PRESSED_ALPHA)
    )

/**
 * El acento elegido: uno de [AlisioTokens.Accents] por su índice o, con [CUSTOM_ACCENT], el color
 * propio [custom]. Un índice fuera de rango, o el propio sin color guardado, es el de por defecto.
 */
fun accentColor(index: Int, custom: Int?): Color = when {
    index == CUSTOM_ACCENT && custom != null -> Color(custom)
    else -> AlisioTokens.Accents.getOrElse(index) { AlisioTokens.Accents.first() }
}

/**
 * El color del día: el acento elegido de día y el de la paleta [nightIndex] de noche, con el paso
 * suave de la salida y la puesta del sol de ese sitio ([daylight]). Sin coordenadas se calcula para
 * la zona horaria. Siempre legible con el texto de los botones encima.
 */
fun dayAccentColor(day: Color, nightIndex: Int, now: ZonedDateTime, latitude: Double?, longitude: Double?): Color {
    val sun = sunMinutes(now.toLocalDate(), now.zone, latitude, longitude)
    val night = AlisioTokens.Accents.getOrElse(nightIndex) { AlisioTokens.Accents.first() }
    val light = daylight(now.hour * 60 + now.minute, sun)
    return Color(dayAccent(day.toArgb(), night.toArgb(), light, AlisioTokens.Background.toArgb()))
}

/**
 * @param accent el acento de toda la app ([accentColor]).
 * @param font familias de toda la interfaz.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlisioTheme(
    accent: Color = AlisioTokens.Accents.first(),
    font: FontChoice = FontChoice.PAIR,
    content: @Composable () -> Unit
) {
    val color = accent
    SideEffect { AlisioTokens.useAccent(color) }
    val fonts = remember(font) { fontsFor(font) }
    MaterialTheme(
        colorScheme = remember(color) { colorScheme(color) },
        typography = remember(fonts) { typography(fonts) },
        shapes = AlisioShapes
    ) {
        CompositionLocalProvider(
            LocalAlisioFonts provides fonts,
            LocalRippleConfiguration provides Pressed,
            content = content
        )
    }
}
