package dev.jesusmolano.alisio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

private val TileRing = 2.dp
private val TilePreviewHeight = 72.dp

/**
 * La marca de «elegido» de una tesela: un anillo de acento que no cambia su relleno. Vale también
 * para lo que está bajo el dedo (la celda de Trazo, la isla donde se va a soltar una app). Es el
 * único trazo de contorno de la app, junto con el aro del punto de no leído.
 */
fun Modifier.selectedTile(selected: Boolean, shape: Shape = AlisioTokens.CardShape): Modifier =
    if (selected) border(TileRing, AlisioTokens.Accent, shape) else this

/**
 * El anillo de [selectedTile] para una placa que se pinta por tramos apilados (la isla Resto de
 * Zonas, cuyas filas son elementos de una lista perezosa): cada tramo dibuja solo su parte del
 * contorno, y juntos forman un único anillo.
 *
 * @param top el tramo es el primero: lleva el borde superior y sus esquinas.
 * @param bottom el tramo es el último: lleva el borde inferior y sus esquinas.
 */
fun Modifier.selectedTileSegment(
    selected: Boolean,
    top: Boolean,
    bottom: Boolean,
    shape: CornerBasedShape = AlisioTokens.CardShape
): Modifier = if (!selected) {
    this
} else {
    drawWithContent {
        drawContent()
        val ring = TileRing.toPx()
        val radius = shape.topStart.toPx(size, this)
        // El contorno se dibuja entero, más alto que el tramo por el lado que continúa, y se
        // recorta al tramo: lo que sobresale son las esquinas y el borde que tocan a otro tramo.
        val overflow = radius + ring
        val start = if (top) 0f else -overflow
        val end = if (bottom) size.height else size.height + overflow
        clipRect {
            drawRoundRect(
                color = AlisioTokens.Accent,
                topLeft = Offset(ring / 2, start + ring / 2),
                size = Size(size.width - ring, end - start - ring),
                cornerRadius = CornerRadius(radius - ring / 2),
                style = Stroke(ring)
            )
        }
    }
}

/**
 * Una opción que se elige mirando (docs/DISENO.md §4.5): una tesela con el resultado dibujado con
 * las piezas reales y su nombre debajo, sin descripción. Varias juntas van en una fila de pesos
 * iguales o, si son más de tres, en una fila que se desplaza.
 *
 * @param previewHeight alto mínimo del área del dibujo.
 * @param enabled sin su requisito cumplido la tesela se atenúa y no responde.
 * @param backdrop lo que pinta el fondo de la tesela en lugar del relleno de siempre, cuando el
 *   resultado que se enseña es un fondo (el velo del fondo de pantalla).
 * @param preview el resultado de elegir esta opción, a escala.
 */
@Composable
fun PreviewChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    previewHeight: Dp = TilePreviewHeight,
    enabled: Boolean = true,
    backdrop: Modifier? = null,
    preview: @Composable BoxScope.() -> Unit
) {
    val shape = AlisioTokens.CardShape
    Column(
        modifier
            .alpha(if (enabled) 1f else AlisioTokens.DISABLED_ALPHA)
            .clip(shape)
            .then(backdrop ?: Modifier.background(AlisioTokens.Fill, shape))
            .selectedTile(selected && enabled, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = previewHeight),
            contentAlignment = Alignment.Center,
            content = preview
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            // Sobre un fondo propio el nombre va siempre en claro: el gris no se leería.
            color = if (selected || backdrop != null) AlisioTokens.Text else AlisioTokens.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
