package dev.jesusmolano.alisio.ui.components

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import dev.jesusmolano.alisio.ui.theme.onWallpaper

private const val FOLDER_APPS = 4

// Proporciones sobre el lado del icono: valen igual en el dock (52 dp) que en una vista previa.
private const val FOLDER_ICON_RATIO = 20f / 52f
private const val FOLDER_GAP_RATIO = 4f / 52f
private const val GLYPH_ICON_RATIO = 24f / 52f

/**
 * El nombre de una app bajo su icono: dock, Zonas, Trazo y teselas. Una sola línea.
 *
 * @param onWallpaper va directo sobre el fondo de pantalla, no sobre una placa ni un fondo opaco.
 */
@Composable
fun IconLabel(
    label: String,
    modifier: Modifier = Modifier,
    onWallpaper: Boolean = false,
    color: Color = AlisioTokens.Text
) {
    val style = AlisioTokens.DockLabel
    Text(
        text = label,
        style = if (onWallpaper) style.onWallpaper() else style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(horizontal = 2.dp)
    )
}

// Separación de la marca de deslizar respecto al borde superior del icono.
private val SwipeMarkGap = 7.dp

/**
 * Un icono como se ve en el dock: con su punto de no leído, su marca de destino al deslizar y su
 * nombre debajo. Solo dibuja; los toques y los gestos los pone quien lo usa en [modifier]. Lo usan
 * el dock del Inicio, su miniatura y las filas de apps de los ajustes.
 *
 * @param swipeMark el icono tiene destino al deslizar hacia arriba.
 * @param onWallpaper el nombre va directo sobre el fondo de pantalla.
 * @param iconModifier se aplica solo al icono, no al nombre: el desplazamiento que sigue al dedo.
 */
@Composable
fun DockIcon(
    label: String,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = AlisioTokens.DockIconSize,
    unread: Boolean = false,
    swipeMark: Boolean = false,
    onWallpaper: Boolean = false,
    iconModifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(iconModifier.size(size), contentAlignment = Alignment.Center) {
            icon()
            if (unread) UnreadDot()
            if (swipeMark) SwipeMark()
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            IconLabel(label, onWallpaper = onWallpaper)
        }
    }
}

/**
 * Marca de que el icono tiene destino secundario: un trazo corto de acento justo encima, como un
 * tirador. Decorativa; TalkBack recibe el destino como acción del icono.
 */
@Composable
private fun BoxScope.SwipeMark() {
    Box(
        Modifier
            .align(Alignment.TopCenter)
            // Por fuera del icono, para no tapar su dibujo ni chocar con el punto de no leído.
            .offset(y = -SwipeMarkGap)
            .size(width = 14.dp, height = 3.dp)
            .background(AlisioTokens.Accent, CircleShape)
    )
}

/** La placa con forma de icono de app: base del hueco vacío, de la carpeta de una pila y de su símbolo. */
@Composable
private fun IconPlate(size: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.size(size).background(AlisioTokens.Plate, AlisioTokens.DockIconShape),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Un dibujo dentro de la placa de icono: el hueco vacío del dock o una pila que aún no tiene apps. */
@Composable
fun VectorTileIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, size: Dp = AlisioTokens.DockIconSize) {
    IconPlate(size, modifier) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = AlisioTokens.TextSecondary,
            modifier = Modifier.size(GLYPH_ICON_RATIO * size)
        )
    }
}

/**
 * El símbolo que el usuario ha escrito para su pila. Es contenido suyo, no un icono de la interfaz:
 * por eso es el único carácter que se pinta como icono.
 */
@Composable
fun SymbolTileIcon(symbol: String, modifier: Modifier = Modifier, size: Dp = AlisioTokens.DockIconSize) {
    IconPlate(size, modifier) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.headlineSmall,
            color = AlisioTokens.Text,
            maxLines = 1
        )
    }
}

/**
 * La placa del símbolo de una pila mientras se escribe: el campo va dentro del propio icono, y lo
 * que se teclea se ve ya como quedará en el dock. Vacío y sin foco enseña un lápiz: ahí se escribe.
 *
 * @param description lo que lee TalkBack; la placa no lleva texto al lado que lo diga.
 * @param focusRequester para llevar el teclado al campo cuando se elige esta forma de icono.
 */
@Composable
fun SymbolTileField(
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    size: Dp = AlisioTokens.DockIconSize
) {
    var focused by remember { mutableStateOf(false) }
    IconPlate(size, modifier) {
        // Con el foco, el cursor ya dice dónde se escribe; el lápiz caería justo debajo de él.
        if (value.isEmpty() && !focused) {
            Icon(
                painter = painterResource(R.drawable.ic_edit),
                contentDescription = null,
                tint = AlisioTokens.TextSecondary,
                modifier = Modifier.size(GLYPH_ICON_RATIO * size)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle =
                MaterialTheme.typography.headlineSmall.copy(color = AlisioTokens.Text, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(AlisioTokens.Accent),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = description }
        )
    }
}

/** Carpeta de una pila: sus cuatro primeras apps en una rejilla de 2 × 2 sobre la placa de icono. */
@Composable
fun FolderTileIcon(icons: List<Bitmap>, modifier: Modifier = Modifier, size: Dp = AlisioTokens.DockIconSize) {
    val gap = FOLDER_GAP_RATIO * size
    IconPlate(size, modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            icons.take(FOLDER_APPS).chunked(2).forEach { row ->
                // Una fila con un solo icono lo deja a la izquierda, como si faltara el siguiente.
                Row(Modifier.size(width = 2 * FOLDER_ICON_RATIO * size + gap, height = FOLDER_ICON_RATIO * size)) {
                    row.forEachIndexed { index, icon ->
                        AppIcon(icon, FOLDER_ICON_RATIO * size, Modifier.padding(start = if (index == 0) 0.dp else gap))
                    }
                }
            }
        }
    }
}
