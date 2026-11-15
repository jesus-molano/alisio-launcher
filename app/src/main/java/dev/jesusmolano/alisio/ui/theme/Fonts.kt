package dev.jesusmolano.alisio.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.jesusmolano.alisio.R

// Fuentes variables empaquetadas (OFL, docs/licenses): cada peso se pide por su eje `wght`.
private fun variable(resource: Int, vararg weights: FontWeight) = FontFamily(
    weights.map { Font(resource, it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight))) }
)

internal val Inter =
    variable(R.font.inter, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
internal val SpaceGrotesk = variable(R.font.space_grotesk, FontWeight.Light, FontWeight.Medium)
