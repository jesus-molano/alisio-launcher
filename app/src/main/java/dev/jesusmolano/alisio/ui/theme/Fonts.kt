package dev.jesusmolano.alisio.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.theme.FontChoice

// Fuentes variables empaquetadas (OFL, docs/licenses): cada peso se pide por su eje `wght`.
private fun variable(resource: Int, vararg weights: FontWeight) = FontFamily(
    weights.map { Font(resource, it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight))) }
)

internal val Inter =
    variable(R.font.inter, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

// Lleva también los pesos de lectura: con «Todo en Space Grotesk» hace de familia de texto.
internal val SpaceGrotesk =
    variable(
        R.font.space_grotesk,
        FontWeight.Light,
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold
    )

/**
 * Las dos voces de la interfaz (docs/DISENO.md §1).
 *
 * @param display lo que se mira: hora, títulos y cifras.
 * @param text lo que se lee: filas, etiquetas y botones.
 */
@Immutable
class AlisioFonts(val display: FontFamily, val text: FontFamily)

/** Las familias de una elección. Vale también para dibujar la vista previa de una que no es la activa. */
fun fontsFor(choice: FontChoice): AlisioFonts = when (choice) {
    FontChoice.PAIR -> AlisioFonts(display = SpaceGrotesk, text = Inter)
    FontChoice.INTER -> AlisioFonts(display = Inter, text = Inter)
    FontChoice.GROTESK -> AlisioFonts(display = SpaceGrotesk, text = SpaceGrotesk)
    FontChoice.SYSTEM -> AlisioFonts(display = FontFamily.Default, text = FontFamily.Default)
}

/** Las familias activas; las fija [AlisioTheme] y las leen los estilos de [AlisioTokens]. */
val LocalAlisioFonts = staticCompositionLocalOf { fontsFor(FontChoice.PAIR) }
