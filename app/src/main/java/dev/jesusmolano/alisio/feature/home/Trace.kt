package dev.jesusmolano.alisio.feature.home

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.AppSection
import kotlin.math.roundToInt

val TraceRailWidth = 48.dp
private val RailLetterHeight = 22.dp
private val CellWidth = 80.dp
private val CellHeight = 92.dp
private val IconSize = 48.dp
private val CellIconTop = 10.dp
private val HeaderHeight = 112.dp
private val GridGap = 8.dp

/** Un trazo en curso: letra elegida, su rejilla y, con el dedo ya fuera del índice, la app bajo él. */
private data class TraceState(val section: Int, val grid: TraceGrid, val locked: Boolean, val selected: Int?)

/**
 * Trazo: índice de letras en el borde derecho. Tocar una letra, deslizar a la izquierda sobre sus
 * apps y soltar encima de una la abre, todo en un gesto. Soltar sobre la letra o lejos de la rejilla
 * cancela. Ocupa todo el [BoxScope] para poder tapar el Inicio mientras dura el trazo.
 *
 * @param railPadding márgenes del índice dentro de la pantalla (barras del sistema, dock).
 */
@Composable
fun BoxScope.Trace(sections: List<AppSection<AppEntry>>, railPadding: PaddingValues, onLaunch: (AppEntry) -> Unit) {
    if (sections.isEmpty()) return
    val view = LocalView.current
    val density = LocalDensity.current
    val insets = WindowInsets.systemBars
    // Si la lista de apps cambia a mitad de un trazo, el trazo se descarta: sus índices ya no valen.
    var trace by remember(sections) { mutableStateOf<TraceState?>(null) }
    var boxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var railCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(Modifier.matchParentSize().onGloballyPositioned { boxCoords = it }) {
        trace?.let { current -> sections.getOrNull(current.section)?.let { TraceOverlay(current, it) } }

        val railDescription = stringResource(R.string.trace_rail_description)
        Column(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(railPadding)
                    .width(TraceRailWidth)
                    .heightIn(max = RailLetterHeight * sections.size)
                    .fillMaxHeight()
                    .onGloballyPositioned { railCoords = it }
                    // El índice pisa la franja del gesto atrás del borde derecho: sin esto, One UI
                    // se queda el trazo. El tope de 200 dp por borde no se aplica a la app de inicio.
                    .systemGestureExclusion()
                    .semantics { contentDescription = railDescription }
                    .pointerInput(sections) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var current: TraceState? = null

                            fun update(position: Offset) {
                                val box = boxCoords ?: return
                                val rail = railCoords ?: return
                                val finger = box.localPositionOf(rail, position)
                                val railLeft = box.localPositionOf(rail, Offset.Zero).x
                                val previous = current
                                val next =
                                    if (previous == null || finger.x >= railLeft) {
                                        // En el índice: el dedo elige letra.
                                        val letterHeight = size.height.toFloat() / sections.size
                                        val index = (position.y / letterHeight).toInt().coerceIn(0, sections.lastIndex)
                                        if (previous != null && !previous.locked && previous.section == index) {
                                            previous
                                        } else {
                                            val letterCenter = Offset(0f, (index + 0.5f) * letterHeight)
                                            val cellWidth = CellWidth.toPx()
                                            val cellHeight = CellHeight.toPx()
                                            val right = railLeft - GridGap.toPx()
                                            val minTop = insets.getTop(density) + HeaderHeight.toPx()
                                            val maxBottom = box.size.height - insets.getBottom(density) - GridGap.toPx()
                                            TraceState(
                                                section = index,
                                                grid =
                                                    TraceGrid(
                                                        count = sections[index].items.size,
                                                        maxColumns = ((right - GridGap.toPx()) / cellWidth).toInt(),
                                                        maxRows = ((maxBottom - minTop) / cellHeight).toInt(),
                                                        cellWidth = cellWidth,
                                                        cellHeight = cellHeight,
                                                        right = right,
                                                        centerY = box.localPositionOf(rail, letterCenter).y,
                                                        minTop = minTop,
                                                        maxBottom = maxBottom
                                                    ),
                                                locked = false,
                                                selected = null
                                            )
                                        }
                                    } else {
                                        // Fuera del índice: la letra queda fijada y el dedo elige app.
                                        val selected = previous.grid.indexAt(finger.x, finger.y)
                                        previous.copy(locked = true, selected = selected)
                                    }
                                val letterChanged = next.section != previous?.section
                                val appChanged = next.selected != null && next.selected != previous?.selected
                                if (letterChanged || appChanged) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                                current = next
                                trace = next
                            }

                            try {
                                down.consume()
                                update(down.position)
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    update(change.position)
                                }
                                val end = current
                                if (end != null && end.locked && end.selected != null) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    onLaunch(sections[end.section].items[end.selected])
                                }
                            } finally {
                                // También si el sistema cancela el gesto a medias.
                                trace = null
                            }
                        }
                    }
        ) {
            sections.forEachIndexed { index, section ->
                val active = trace?.section == index
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = section.letter,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color =
                            if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
                            }
                    )
                }
            }
        }
    }
}

/** Lo que se ve durante un trazo: el Inicio tapado, la letra en grande y la rejilla de sus apps. */
@Composable
private fun TraceOverlay(trace: TraceState, section: AppSection<AppEntry>) {
    val grid = trace.grid
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val selectedApp = trace.selected?.let(section.items::getOrNull)
    Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.94f))) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, (grid.top - HeaderHeight.toPx()).roundToInt()) }
                    .height(HeaderHeight)
                    .padding(start = 24.dp, end = TraceRailWidth + GridGap + 8.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(text = section.letter, style = MaterialTheme.typography.displayMedium, color = colors.primary)
            Text(
                text =
                    when {
                        selectedApp != null -> selectedApp.label
                        trace.locked -> stringResource(R.string.trace_hint_cancel)
                        else -> stringResource(R.string.trace_hint_slide)
                    },
                style = MaterialTheme.typography.titleMedium,
                color = if (selectedApp != null) colors.onBackground else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        section.items.take(grid.shown).forEachIndexed { index, app ->
            val selected = trace.selected == index
            val shape = RoundedCornerShape(20.dp)
            Column(
                modifier =
                    Modifier
                        .offset { IntOffset(grid.cellLeft(index).roundToInt(), grid.cellTop(index).roundToInt()) }
                        .size(with(density) { grid.cellWidth.toDp() }, with(density) { grid.cellHeight.toDp() })
                        .padding(2.dp)
                        .then(
                            if (selected) {
                                Modifier
                                    .background(colors.primary.copy(alpha = 0.14f), shape)
                                    .border(1.dp, colors.primary, shape)
                            } else {
                                Modifier
                            }
                        )
                        .padding(top = CellIconTop - 2.dp, start = 4.dp, end = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Decorativo: el nombre va debajo.
                Image(
                    bitmap = remember(app.icon) { app.icon.asImageBitmap() },
                    contentDescription = null,
                    modifier = Modifier.size(IconSize)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = app.label,
                    fontSize = 11.sp,
                    color = if (selected) colors.onBackground else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
