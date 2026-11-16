package dev.jesusmolano.alisio.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.theme.CUSTOM_ACCENT
import dev.jesusmolano.alisio.core.theme.Hsv
import dev.jesusmolano.alisio.core.theme.accentProposals
import dev.jesusmolano.alisio.core.theme.contrastRatio
import dev.jesusmolano.alisio.core.theme.hexOf
import dev.jesusmolano.alisio.core.theme.hsvOf
import dev.jesusmolano.alisio.core.theme.isLegibleAccent
import dev.jesusmolano.alisio.core.theme.legibleAccent
import dev.jesusmolano.alisio.core.theme.toArgb
import dev.jesusmolano.alisio.ui.components.Chip
import dev.jesusmolano.alisio.ui.components.ColorSlider
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.Note
import dev.jesusmolano.alisio.ui.components.PrimaryButton
import dev.jesusmolano.alisio.ui.components.TonalButton
import dev.jesusmolano.alisio.ui.components.rememberWallpaperColors
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import kotlin.math.roundToInt

internal val AccentSwatchSize = 36.dp
private val CheckSize = 20.dp

// Muestras del tono: cada 60° hay un primario o un secundario, y la pista los enseña todos.
private const val HUE_STOPS = 7
private const val FULL_TURN = 360f
private const val PERCENT = 100

/**
 * Los acentos: los cuatro de la paleta y un quinto círculo, el color propio. Tocarlo abre debajo
 * el editor ([CustomAccentEditor]); mientras se mueve un deslizador, toda la página y el Inicio en
 * miniatura de arriba se pintan con el color del dedo.
 */
@Composable
internal fun AccentChoices(context: SettingsContext) {
    val prefs = context.prefs
    val settings = context.settings
    val custom = prefs.customAccent
    // El editor se abre con el color propio elegido, o al tocar su círculo para crearlo.
    var editing by remember { mutableStateOf(prefs.accent == CUSTOM_ACCENT) }
    // El círculo va centrado en su toque de 48 dp: el margen deja el primero en el filo.
    Row(Modifier.padding(horizontal = AlisioTokens.Outset - (AlisioTokens.MinTouch - AccentSwatchSize) / 2)) {
        AlisioTokens.Accents.forEachIndexed { index, color ->
            AccentSwatch(
                name = stringResource(accentName(index)),
                chosen = index == prefs.accent,
                onClick = {
                    editing = false
                    settings.setAccent(index)
                }
            ) { Box(Modifier.size(AccentSwatchSize).background(color, CircleShape)) }
        }
        AccentSwatch(
            name = stringResource(R.string.accent_custom),
            chosen = prefs.accent == CUSTOM_ACCENT && custom != null,
            onClick = {
                editing = true
                if (custom != null) settings.setCustomAccent(custom)
            }
        ) {
            // Sin color propio todavía, el círculo es la paleta: dice qué pasa al tocarlo.
            if (custom == null) {
                Box(Modifier.size(AccentSwatchSize).background(AlisioTokens.Fill, CircleShape), Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_palette),
                        contentDescription = null,
                        tint = AlisioTokens.Text,
                        modifier = Modifier.size(CheckSize)
                    )
                }
            } else {
                Box(Modifier.size(AccentSwatchSize).background(Color(custom), CircleShape))
            }
        }
    }
    if (editing) {
        val start = custom ?: AlisioTokens.Accent.toArgb()
        CustomAccentEditor(
            start = start,
            onPreview = { settings.accentDraft.value = it },
            onCommit = settings::setCustomAccent,
            onClose = { settings.accentDraft.value = null }
        )
    }
}

/** Un círculo de acento en su toque de 48 dp, con la marca encima si es el elegido. */
@Composable
internal fun AccentSwatch(name: String, chosen: Boolean, onClick: () -> Unit, swatch: @Composable () -> Unit) {
    Box(
        Modifier
            .padding(end = 4.dp)
            .size(AlisioTokens.MinTouch)
            .clip(CircleShape)
            .selectable(selected = chosen, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center
    ) {
        swatch()
        if (chosen) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = AlisioTokens.Background,
                modifier = Modifier.size(CheckSize)
            )
        }
    }
}

/**
 * El color propio: una muestra grande con el texto encima tal como irá en el botón principal, los
 * tres deslizadores y los colores del fondo de pantalla como propuestas. Al mover un deslizador el
 * color se ve en toda la app ([onPreview]); al soltarlo se guarda ([onCommit]). Si el texto oscuro no
 * se lee sobre él, lo dice y ofrece el más cercano que sí se lee.
 *
 * @param onClose al salir de la página: lo que se pinta vuelve a ser lo guardado.
 */
@Composable
private fun CustomAccentEditor(start: Int, onPreview: (Int) -> Unit, onCommit: (Int) -> Unit, onClose: () -> Unit) {
    // Se lleva en tono, saturación y brillo, no en ARGB: un gris no tiene tono y, si se derivara del
    // color, el deslizador del tono saltaría a 0 al bajar la saturación.
    var hsv by remember { mutableStateOf(hsvOf(start)) }
    val argb = hsv.toArgb()
    val onAccent = AlisioTokens.Background.toArgb()
    val legible = isLegibleAccent(argb, onAccent)
    DisposableEffect(Unit) { onDispose(onClose) }

    fun move(next: Hsv) {
        hsv = next
        onPreview(next.toArgb())
    }

    fun choose(color: Int) {
        hsv = hsvOf(color)
        onPreview(color)
        onCommit(color)
    }

    Spacer(Modifier.height(AlisioTokens.LabelGap))
    AccentSample(argb, contrastRatio(argb, onAccent))
    Spacer(Modifier.height(AlisioTokens.LabelGap))
    val commit = { onCommit(hsv.toArgb()) }
    ColorSlider(
        label = stringResource(R.string.accent_hue),
        value = hsv.hue,
        onValueChange = { move(hsv.copy(hue = it)) },
        onValueChangeFinished = commit,
        track = gradient(*Array(HUE_STOPS) { Hsv(FULL_TURN * it / (HUE_STOPS - 1), hsv.saturation, hsv.value) }),
        thumb = Color(argb),
        valueText = stringResource(R.string.accent_degrees, hsv.hue.roundToInt()),
        valueRange = 0f..FULL_TURN
    )
    ColorSlider(
        label = stringResource(R.string.accent_saturation),
        value = hsv.saturation,
        onValueChange = { move(hsv.copy(saturation = it)) },
        onValueChangeFinished = commit,
        track = gradient(hsv.copy(saturation = 0f), hsv.copy(saturation = 1f)),
        thumb = Color(argb),
        valueText = stringResource(R.string.accent_percent, (hsv.saturation * PERCENT).roundToInt())
    )
    ColorSlider(
        label = stringResource(R.string.accent_brightness),
        value = hsv.value,
        onValueChange = { move(hsv.copy(value = it)) },
        onValueChangeFinished = commit,
        track = gradient(hsv.copy(value = 0f), hsv.copy(value = 1f)),
        thumb = Color(argb),
        valueText = stringResource(R.string.accent_percent, (hsv.value * PERCENT).roundToInt())
    )
    // Aviso suave, debajo de los deslizadores para que aparecer no los mueva bajo el dedo. No impide
    // usar el color: dice qué pasa y ofrece el arreglo en un toque. La acción no va en el acento,
    // porque en el elegido tampoco se leería.
    // La acción es un botón tonal (relleno gris, texto claro): se lee con cualquier acento y tiene forma
    // de control, no de etiqueta.
    if (!legible) {
        Note(stringResource(R.string.accent_illegible))
        TonalButton(
            text = stringResource(R.string.accent_make_legible),
            onClick = { choose(legibleAccent(argb, onAccent)) },
            modifier = Modifier.padding(horizontal = AlisioTokens.Outset, vertical = 4.dp)
        )
    }
    WallpaperProposals(current = argb, onAccent = onAccent, onChoose = ::choose)
}

/** La pista de un deslizador: el color pasando por [stops], de izquierda a derecha. */
private fun gradient(vararg stops: Hsv): Brush = Brush.horizontalGradient(stops.map { Color(it.toArgb()) })

/**
 * La muestra: las piezas reales que llevan el acento (el botón principal, un interruptor encendido y
 * una opción elegida), a su tamaño, y debajo el código y el contraste. Un color se lee mejor en grande
 * que en un botón de 14 sp: por eso se enseña donde de verdad va. El tema ya pinta el color del dedo
 * mientras se elige; son un dibujo y no responden a los toques.
 */
@Composable
private fun AccentSample(argb: Int, contrast: Double) {
    val description = stringResource(R.string.accent_code, hexOf(argb), contrast)
    Box(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Column {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.accent_sample_switch)),
                checked = true,
                onClick = {}
            )
            Row(
                Modifier.padding(horizontal = AlisioTokens.Outset),
                horizontalArrangement = Arrangement.spacedBy(AlisioTokens.IconGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrimaryButton(text = stringResource(R.string.accent_sample), onClick = {})
                Chip(label = stringResource(R.string.accent_sample_chip), selected = true, onClick = {})
            }
            Note(description)
        }
        // Encima de todo: las piezas son una muestra, no controles.
        Box(Modifier.matchParentSize().pointerInput(Unit) {})
    }
}

/**
 * Los colores principales del fondo de pantalla como propuestas, ya legibles: un toque elige uno y
 * los deslizadores se colocan en él. Sin fondo con colores (uno animado, uno gris) no sale nada.
 */
@Composable
private fun WallpaperProposals(current: Int, onAccent: Int, onChoose: (Int) -> Unit) {
    val wallpaper = rememberWallpaperColors()
    val proposals = remember(wallpaper, onAccent) { accentProposals(wallpaper, onAccent) }
    if (proposals.isEmpty()) return
    Note(stringResource(R.string.accent_from_wallpaper), Modifier.padding(top = AlisioTokens.LabelGap))
    Row(Modifier.padding(horizontal = AlisioTokens.Outset - (AlisioTokens.MinTouch - AccentSwatchSize) / 2)) {
        proposals.forEach { proposal ->
            AccentSwatch(
                name = stringResource(R.string.accent_proposal, hexOf(proposal)),
                chosen = proposal == current,
                onClick = { onChoose(proposal) }
            ) { Box(Modifier.size(AccentSwatchSize).background(Color(proposal), CircleShape)) }
        }
    }
}
