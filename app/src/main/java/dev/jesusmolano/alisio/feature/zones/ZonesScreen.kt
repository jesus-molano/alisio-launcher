package dev.jesusmolano.alisio.feature.zones

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.trace.TraceRail
import dev.jesusmolano.alisio.core.zones.AssignmentChange
import dev.jesusmolano.alisio.core.zones.Pinch
import dev.jesusmolano.alisio.core.zones.ZoneId
import dev.jesusmolano.alisio.core.zones.assignmentChange
import dev.jesusmolano.alisio.core.zones.packZones
import dev.jesusmolano.alisio.core.zones.zoneSpan
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.toSections
import dev.jesusmolano.alisio.data.settings.Prefs
import dev.jesusmolano.alisio.feature.appmenu.AppMenuSheet
import dev.jesusmolano.alisio.feature.appmenu.AppUsage
import dev.jesusmolano.alisio.feature.home.RestMinPitch
import dev.jesusmolano.alisio.feature.home.Trace
import dev.jesusmolano.alisio.feature.home.TraceRailWidth
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.components.IconLabel
import dev.jesusmolano.alisio.ui.components.LocalUndo
import dev.jesusmolano.alisio.ui.components.RoundIconButton
import dev.jesusmolano.alisio.ui.components.onPinch
import dev.jesusmolano.alisio.ui.components.selectedTile
import dev.jesusmolano.alisio.ui.components.selectedTileSegment
import dev.jesusmolano.alisio.ui.theme.AlisioHaptics
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import dev.jesusmolano.alisio.ui.theme.onWallpaper
import kotlin.math.roundToInt

private val StartMargin = AlisioTokens.Outset

// Lo que Resto se retira además del margen para que su índice de Trazo quepa a su derecha.
private val RestRailGap = TraceRailWidth - StartMargin

// El índice no sube más allá de la cabecera de Zonas ni baja hasta el borde de la pantalla.
private val RailMinTop = 104.dp

// Abajo deja sitio para que, al tocar las últimas letras, se abran hacia abajo sin pisar la zona
// del gesto de inicio del sistema.
private val RailEdgeGap = 72.dp

// Claves de Resto en la lista: de ellas sale si Resto está a la vista y dónde empieza.
private const val REST_TITLE_KEY = "rest-title"
private const val REST_ROW_PREFIX = "rest-"

// Ancho mínimo de un icono con su nombre: de aquí sale cuántos caben a lo ancho.
private val MinCellWidth = 76.dp
private const val MIN_COLUMNS = 3
private val IslandGap = AlisioTokens.PlateGap

// Relleno interior de la placa: con el margen exterior, el nombre y el primer icono caen en el filo.
private val IslandInset = AlisioTokens.Edge - StartMargin
private val IslandBottom = 4.dp
private val CellIconSize = AlisioTokens.DockIconSize

// La celda es más ancha que el icono para que quepa su nombre, y el icono va centrado en ella: la
// fila se mete solo lo que falta para que el primer icono, y no su celda, empiece en el relleno.
private val CellWidth = 72.dp
private val CellRowInset = IslandInset - (CellWidth - CellIconSize) / 2

// La app cogida crece lo justo para notarse levantada (docs/DISENO.md §2.6).
private val DragIconSize = CellIconSize * 1.04f

// Franja junto al borde superior e inferior en la que, con una app cogida, la lista se desplaza sola.
private val AutoScrollEdge = 88.dp
private val AutoScrollStep = 14.dp
private val IslandShape = AlisioTokens.CardShape
private val Square = CornerSize(0.dp)

// Resto se pinta por tramos: el primero y el último llevan las esquinas de una isla; los demás, ninguna.
private val RestTopShape = IslandShape.copy(bottomStart = Square, bottomEnd = Square)
private val RestBottomShape = IslandShape.copy(topStart = Square, topEnd = Square)
private val SquareShape = RoundedCornerShape(Square)

/** Una app cogida con el dedo: de qué isla sale, dónde va y sobre qué isla está. */
private class DragState(val app: AppEntry, val from: ZoneId, val default: ZoneId, val start: Offset) {
    var position by mutableStateOf(start)
    var over by mutableStateOf<ZoneId?>(null)

    // Si no se ha movido del sitio, soltar no es arrastrar a ninguna parte: abre el menú de la app.
    var moved = false

    /** Si soltar aquí cambiaría algo: otra isla, y una a la que esta app pueda ir. */
    fun accepts(zone: ZoneId): Boolean = zone != from && assignmentChange(zone, default) != AssignmentChange.Invalid
}

/**
 * Dónde está cada cosa en pantalla, para saber qué app hay bajo el dedo y sobre qué isla se suelta.
 * No es estado de Compose a propósito: cambia en cada desplazamiento y solo se consulta desde los gestos.
 */
private class Geometry {
    class Cell(val app: AppEntry, val zone: ZoneId, val owner: Any, var coords: LayoutCoordinates)

    class Area(val zone: ZoneId, var coords: LayoutCoordinates)

    var root: LayoutCoordinates? = null
    val cells = HashMap<String, Cell>()
    val areas = HashMap<Any, Area>()

    fun cellAt(position: Offset): Cell? = cells.values.firstOrNull { position in it.coords }

    fun zoneAt(position: Offset): ZoneId? = areas.values.firstOrNull { position in it.coords }?.zone

    private operator fun LayoutCoordinates.contains(position: Offset): Boolean {
        val root = root ?: return false
        return isAttached && root.isAttached && root.localBoundingBoxOf(this).contains(position)
    }
}

/**
 * Zonas (§5.7, §6): todas las apps repartidas en islas, en lugar de un cajón. Tocar una app la abre;
 * mantenerla y arrastrarla a otra isla la cambia de sitio; mantenerla y soltarla abre su Interior.
 * Pellizcar hacia fuera vuelve al Inicio. La isla Resto lleva el índice de Trazo.
 *
 * @param onClose vuelve al Inicio; atrás e «inicio» los atiende quien muestra la pantalla.
 * @param onAppMenu abre el Interior de una app. La hoja la pinta el Inicio, que tiene sus datos y
 *   el dock donde ponerla.
 */
@Composable
fun ZonesScreen(
    viewModel: ZonesViewModel,
    prefs: Prefs,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val view = LocalView.current
    val density = LocalDensity.current
    val bars = WindowInsets.systemBars.asPaddingValues()
    val listState = rememberLazyListState()
    val geometry = remember { Geometry() }
    var drag by remember { mutableStateOf<DragState?>(null) }
    // El gesto de coger una app vive lo que vive la pantalla: lee siempre la orden de ahora.
    val openMenu by rememberUpdatedState(onAppMenu)
    var editing by remember { mutableStateOf<ZoneEdit?>(null) }

    LaunchedEffect(Unit) { viewModel.onOpened() }

    fun launch(app: AppEntry) {
        if (!viewModel.launch(app)) {
            Toast.makeText(context, resources.getString(R.string.app_launch_failed, app.label), Toast.LENGTH_SHORT)
                .show()
        }
    }

    // Con una app cogida cerca del borde, la lista se desplaza sola: la isla de destino puede no
    // estar a la vista. El dedo no se mueve, así que la isla bajo él se vuelve a mirar en cada paso.
    LaunchedEffect(drag) {
        val current = drag ?: return@LaunchedEffect
        val edge = with(density) { AutoScrollEdge.toPx() }
        val step = with(density) { AutoScrollStep.toPx() }
        while (true) {
            withFrameNanos {}
            // Una app cogida junto al borde y aún sin mover no desplaza nada: si no, otra isla
            // pasaría bajo el dedo y soltar para abrir el Interior movería la app a esa isla.
            if (!current.moved) continue
            val height = geometry.root?.takeIf { it.isAttached }?.size?.height ?: continue
            val y = current.position.y
            val delta =
                when {
                    y < edge -> -step
                    y > height - edge -> step
                    else -> 0f
                }
            if (delta != 0f && listState.scrollBy(delta) != 0f) current.over = geometry.zoneAt(current.position)
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            // El fondo de pantalla se ve detrás, como en el Inicio; las islas son placas sobre él.
            .background(AlisioTokens.ZonesVeil)
            .onGloballyPositioned { geometry.root = it }
            .onPinch { if (it == Pinch.OUT) onClose() }
            // Coger una app se detecta aquí y no en cada icono: la lista recicla sus filas al
            // desplazarse sola, y un gesto que viviera en el icono moriría con él a medio arrastre.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val cell = geometry.cellAt(down.position) ?: return@awaitEachGesture
                    // Hasta que pasa el tiempo de la pulsación larga el toque es de la lista y del
                    // icono: si se levanta, se mueve, lo consume el desplazamiento o llega otro dedo,
                    // no se coge nada.
                    val released =
                        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                val still =
                                    change != null && change.pressed && !change.isConsumed &&
                                        event.changes.size == 1 &&
                                        (change.position - down.position).getDistance() <= viewConfiguration.touchSlop
                                if (!still) break
                            }
                            true
                        }
                    if (released != null) return@awaitEachGesture
                    val current = state ?: return@awaitEachGesture
                    val picked = DragState(cell.app, cell.zone, current.defaultZone(cell.app), down.position)
                    AlisioHaptics.dragStart(view)
                    drag = picked
                    var dropped = false
                    try {
                        // Desde aquí el dedo es del arrastre: se mira antes que la lista (pasada
                        // inicial) y se consume todo, también el levantar, para que el icono no
                        // lo tome por un toque.
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            event.changes.forEach { it.consume() }
                            if (!change.pressed) {
                                dropped = true
                                break
                            }
                            picked.position = change.position
                            if ((change.position - picked.start).getDistance() > viewConfiguration.touchSlop * 2) {
                                picked.moved = true
                            }
                            val over = geometry.zoneAt(change.position)
                            if (over != picked.over) {
                                picked.over = over
                                if (over != null && picked.accepts(over)) {
                                    AlisioHaptics.step(view)
                                }
                            }
                        }
                        if (dropped) {
                            val target = picked.over
                            when {
                                target != null && picked.accepts(target) -> {
                                    AlisioHaptics.done(view)
                                    viewModel.move(picked.app, target)
                                }

                                // Soltada sobre una isla que no la admite: no se mueve, y se nota.
                                target != null && target != picked.from -> AlisioHaptics.limit(view)

                                !picked.moved -> openMenu(picked.app)
                            }
                        }
                    } finally {
                        // También si el sistema cancela el gesto a medias.
                        drag = null
                    }
                }
            }
    ) {
        // Las columnas salen del ancho de Resto, la más estrecha: así todas las islas tienen las mismas.
        val contentWidth = maxWidth - StartMargin - TraceRailWidth
        val columns = (contentWidth / MinCellWidth).toInt().coerceAtLeast(MIN_COLUMNS)
        val zones = state?.zones.orEmpty()
        val islands = remember(zones) { zones.filter { it.id != ZoneId.Rest } }
        val rest = remember(zones) { zones.firstOrNull { it.id == ZoneId.Rest } }
        val rows = remember(islands, columns) { packZones(islands.map { it.apps.size }, columns) }
        val restRows = remember(rest, columns) { rest?.apps.orEmpty().chunked(columns) }
        val restSections = remember(rest) { rest?.apps.orEmpty().toSections { it.label } }
        val dragging = drag
        var tracing by remember { mutableStateOf(false) }

        LazyColumn(
            state = listState,
            // Durante un trazo sobre el índice de Resto, las islas se retiran para dejar ver su rejilla.
            modifier =
                Modifier
                    .fillMaxSize()
                    // La lista acaba donde empieza la barra de estado: al desplazarse, las islas no
                    // pasan por debajo de la hora.
                    .padding(top = bars.calculateTopPadding())
                    .graphicsLayer { alpha = if (tracing) 0f else 1f },
            contentPadding =
                PaddingValues(
                    start = StartMargin,
                    // Márgenes iguales a los dos lados; solo Resto deja sitio a su índice (RestPart).
                    end = StartMargin,
                    top = 24.dp,
                    bottom = bars.calculateBottomPadding() + 24.dp
                )
        ) {
            item(key = "header") { Header(onSettings = onOpenSettings, onNew = { editing = ZoneEdit.New }) }

            items(rows.size, key = { index -> rows[index].joinToString("+") { islands[it].id.encode() } }) { index ->
                val row = rows[index]
                val used = row.sumOf { zoneSpan(islands[it].apps.size, columns) }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = IslandGap).height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(IslandGap)
                ) {
                    row.forEach { zoneIndex ->
                        val zone = islands[zoneIndex]
                        val span = zoneSpan(zone.apps.size, columns)
                        Island(
                            zone = zone,
                            span = span,
                            showLabels = prefs.zonesLabels,
                            drag = dragging,
                            geometry = geometry,
                            onLaunch = ::launch,
                            onMenu = onAppMenu,
                            onEdit = { id, name -> editing = ZoneEdit.Existing(id, name) },
                            modifier = Modifier.weight(span.toFloat()).fillMaxHeight()
                        )
                    }
                    // Una isla pequeña sola no se estira: ocupa lo que pide su tamaño.
                    if (used < columns) Spacer(Modifier.weight((columns - used).toFloat()))
                }
            }

            if (rest != null) {
                // Resto puede tener más de cien apps: cada fila es un elemento de la lista, y el
                // fondo de la isla se compone con el de sus filas.
                item(key = REST_TITLE_KEY) {
                    RestPart("title", top = true, bottom = false, dragging, geometry) {
                        IslandTitle(zoneName(ZoneId.Rest, null))
                    }
                }
                if (restRows.isEmpty()) {
                    item(key = "rest-empty") {
                        RestPart("empty", top = false, bottom = true, dragging, geometry) { EmptyIsland() }
                    }
                }
                items(restRows.size, key = { REST_ROW_PREFIX + it }) { index ->
                    // La última fila cierra la isla: lleva sus esquinas y el aire del pie. Un tramo
                    // aparte solo para el pie sería más bajo que el radio y no las dibujaría.
                    val last = index == restRows.lastIndex
                    RestPart(index, top = false, bottom = last, dragging, geometry) {
                        CellRow(Modifier.padding(bottom = if (last) IslandBottom else 0.dp)) {
                            Cells(
                                apps = restRows[index],
                                columns = columns,
                                zone = ZoneId.Rest,
                                showLabels = prefs.zonesLabels,
                                drag = dragging,
                                geometry = geometry,
                                onLaunch = ::launch,
                                onMenu = onAppMenu
                            )
                        }
                    }
                }
            }
        }

        // El índice es de Resto: solo sale con Resto a la vista y no sube de su altura, para no quedar
        // junto a islas que no indexa.
        val railBottom = bars.calculateBottomPadding() + RailEdgeGap
        val railMinTop = bars.calculateTopPadding() + RailMinTop
        val restTop by remember(listState) {
            derivedStateOf {
                val info = listState.layoutInfo
                val visible = info.visibleItemsInfo
                val title = visible.firstOrNull { it.key == REST_TITLE_KEY }
                when {
                    title != null -> with(density) { (title.offset - info.viewportStartOffset).toDp() }
                    visible.any { (it.key as? String)?.startsWith(REST_ROW_PREFIX) == true } -> 0.dp
                    else -> null
                }
            }
        }
        // Con una app cogida el índice se retira: el borde derecho es entonces sitio para soltar.
        // Si Resto aún asoma tan abajo que sus letras no caben a su altura, el índice espera a que se
        // desplace: subirlo lo dejaba encima de las islas de arriba, que no indexa.
        val railTop =
            restTop
                ?.let { (bars.calculateTopPadding() + it).coerceAtLeast(railMinTop) }
                ?.takeIf { maxHeight - railBottom - it >= RestMinPitch * restSections.size }
        if (dragging == null && railTop != null) {
            Trace(
                sections = restSections,
                // La preferencia es del Inicio: aquí el índice es la forma de recorrer Resto y se ve siempre.
                mode = TraceRail.ALWAYS,
                railTop = railTop,
                railBottom = railBottom,
                railTopLimit = railTop,
                onLaunch = ::launch,
                onTracing = { tracing = it }
            )
        } else if (dragging != null) {
            DraggedIcon(dragging)
        }
    }

    editing?.let { edit -> ZoneEditSheet(edit = edit, viewModel = viewModel, onDismiss = { editing = null }) }
}

/** Título, botones y, debajo, la única pista: cómo se mueve una app. Todo sobre el fondo, sin placa. */
@Composable
private fun Header(onSettings: () -> Unit, onNew: () -> Unit) {
    // Las islas empiezan en el margen exterior; el título, como su contenido, en el filo.
    Column(Modifier.fillMaxWidth().padding(start = IslandInset, bottom = AlisioTokens.TitleGap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.zones_title),
                style = MaterialTheme.typography.headlineLarge.onWallpaper(),
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            // Los ajustes no tenían ninguna salida a la vista: aquí y en la barra hay una.
            RoundIconButton(
                R.drawable.ic_settings,
                stringResource(R.string.settings_title),
                AlisioTokens.Text,
                onSettings
            )
            RoundIconButton(R.drawable.ic_add, stringResource(R.string.zone_new), AlisioTokens.Text, onNew)
        }
        Text(text = stringResource(R.string.zones_hint), style = MaterialTheme.typography.bodyMedium.onWallpaper())
    }
}

/**
 * Placa de una isla según lo que pase con la app cogida: bajo el dedo, si puede recibirla, lleva la
 * marca de tesela elegida.
 *
 * @param ring dibuja esa marca; los tramos de Resto dibujan cada uno su parte del anillo.
 */
private fun Modifier.islandSurface(
    zone: ZoneId,
    drag: DragState?,
    shape: Shape,
    ring: Modifier.(hovered: Boolean) -> Modifier
): Modifier {
    val hovered = drag != null && drag.over == zone && drag.accepts(zone)
    // Las islas a las que la app no puede ir se apagan mientras dura el arrastre; la suya no.
    val blocked = drag != null && zone != drag.from && !drag.accepts(zone)
    return this
        .alpha(if (blocked) AlisioTokens.DISABLED_ALPHA else 1f)
        .background(AlisioTokens.Plate, shape)
        .ring(hovered)
}

/** Una isla que no es Resto: su nombre y sus apps en tantas columnas como ocupa de ancho. */
@Composable
private fun Island(
    zone: ZoneView,
    span: Int,
    showLabels: Boolean,
    drag: DragState?,
    geometry: Geometry,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onEdit: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val name = zoneName(zone.id, zone.name)
    DisposableEffect(zone.id) { onDispose { geometry.areas.remove(zone.id) } }
    Column(
        modifier
            .onGloballyPositioned { coords ->
                geometry.areas.getOrPut(zone.id) { Geometry.Area(zone.id, coords) }.coords = coords
            }
            .islandSurface(zone.id, drag, IslandShape) { selectedTile(it, IslandShape) }
            .padding(bottom = IslandBottom)
    ) {
        val custom = zone.id as? ZoneId.Custom
        if (custom == null) {
            IslandTitle(name)
        } else {
            // El nombre de una isla propia es también su botón: tocarlo la renombra o la borra.
            val description = stringResource(R.string.zone_edit, name)
            IslandTitle(
                name = name,
                editable = true,
                modifier =
                    Modifier
                        .clip(IslandShape)
                        .clickable(role = Role.Button) { onEdit(custom.id, name) }
                        .semantics { contentDescription = description }
            )
        }
        if (zone.apps.isEmpty()) {
            EmptyIsland()
        } else {
            zone.apps.chunked(span).forEach { rowApps ->
                CellRow { Cells(rowApps, span, zone.id, showLabels, drag, geometry, onLaunch, onMenu) }
            }
        }
    }
}

/** Una fila de apps de una isla, con el primer icono y el último a la misma distancia del borde. */
@Composable
private fun CellRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = CellRowInset),
        horizontalArrangement = Arrangement.SpaceBetween,
        content = content
    )
}

@Composable
private fun IslandTitle(name: String, modifier: Modifier = Modifier, editable: Boolean = false) {
    Row(
        modifier.fillMaxWidth().heightIn(min = AlisioTokens.MinTouch).padding(horizontal = IslandInset),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = AlisioTokens.SectionLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).semantics { heading() }
        )
        if (editable) {
            Spacer(Modifier.width(6.dp))
            // Decorativo: lo que hace el toque lo dice la descripción de la fila.
            Icon(
                painter = painterResource(R.drawable.ic_edit),
                contentDescription = null,
                tint = AlisioTokens.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** Lo que enseña una isla sin apps: que es un sitio donde soltarlas. */
@Composable
private fun EmptyIsland() {
    Box(
        Modifier.heightIn(min = AlisioTokens.MinTouch).padding(horizontal = IslandInset),
        contentAlignment = Alignment.TopStart
    ) {
        Text(
            text = stringResource(R.string.zone_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = AlisioTokens.TextSecondary
        )
    }
}

/**
 * Un tramo de la isla Resto. Cada tramo es un elemento de la lista perezosa y todos cuentan como
 * la misma isla al soltar.
 *
 * @param part lo que distingue a este tramo de los demás.
 * @param top es el primer tramo: lleva las esquinas de arriba de la isla.
 * @param bottom es el último: lleva las de abajo.
 */
@Composable
private fun RestPart(
    part: Any,
    top: Boolean,
    bottom: Boolean,
    drag: DragState?,
    geometry: Geometry,
    content: @Composable () -> Unit
) {
    val key = remember(part) { "rest" to part }
    val shape =
        when {
            top -> RestTopShape
            bottom -> RestBottomShape
            else -> SquareShape
        }
    DisposableEffect(key) { onDispose { geometry.areas.remove(key) } }
    Box(
        Modifier
            .fillMaxWidth()
            // Solo Resto deja sitio a su índice, que va fuera de la placa, a la derecha.
            .padding(end = RestRailGap)
            .onGloballyPositioned { coords ->
                geometry.areas.getOrPut(key) { Geometry.Area(ZoneId.Rest, coords) }.coords = coords
            }
            .islandSurface(ZoneId.Rest, drag, shape) { selectedTileSegment(it, top, bottom, IslandShape) }
    ) { content() }
}

/** Una fila de apps de una isla; los huecos del final guardan su sitio para que las columnas cuadren. */
@Composable
private fun RowScope.Cells(
    apps: List<AppEntry>,
    columns: Int,
    zone: ZoneId,
    showLabels: Boolean,
    drag: DragState?,
    geometry: Geometry,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit
) {
    apps.forEach { app ->
        AppCell(
            app = app,
            zone = zone,
            showLabel = showLabels,
            picked = drag?.app?.key == app.key,
            geometry = geometry,
            onClick = { onLaunch(app) },
            onMenu = { onMenu(app) },
            modifier = Modifier.width(CellWidth)
        )
    }
    repeat(columns - apps.size) { Spacer(Modifier.width(CellWidth)) }
}

/**
 * Una app de una isla. El toque la abre; cogerla lo detecta la pantalla, que sabe dónde está cada
 * icono por [geometry]. Con TalkBack no hay arrastre: la acción «Mover a otra isla» abre su Interior.
 */
@Composable
private fun AppCell(
    app: AppEntry,
    zone: ZoneId,
    showLabel: Boolean,
    picked: Boolean,
    geometry: Geometry,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val key = app.key.toString()
    val owner = remember { Any() }
    // Al cambiar de isla, el icono nuevo puede registrarse antes de que el viejo se retire: solo
    // borra su sitio quien sigue siendo su dueño.
    DisposableEffect(key) { onDispose { if (geometry.cells[key]?.owner === owner) geometry.cells.remove(key) } }
    val moveLabel = stringResource(R.string.appmenu_move)
    Column(
        modifier =
            modifier
                .onGloballyPositioned { coords ->
                    // Se llama en cada desplazamiento: mientras el icono sea el mismo solo cambia su sitio.
                    val known = geometry.cells[key]
                    if (known != null && known.owner === owner && known.app === app && known.zone == zone) {
                        known.coords = coords
                    } else {
                        geometry.cells[key] = Geometry.Cell(app, zone, owner, coords)
                    }
                }
                .clip(AlisioTokens.RowShape)
                .clickable(onClick = onClick)
                .semantics {
                    // Con los nombres a la vista, el del icono ya lo lee su texto.
                    if (!showLabel) contentDescription = app.label
                    customActions =
                        listOf(
                            CustomAccessibilityAction(moveLabel) {
                                onMenu()
                                true
                            }
                        )
                }
                // La app cogida deja su hueco a la vista, atenuado: ahí vuelve si se suelta fuera.
                .alpha(if (picked) AlisioTokens.DISABLED_ALPHA else 1f)
                // Más aire debajo que encima: el nombre de la isla ya trae el suyo, y entre filas
                // el nombre de una app no debe pegarse al icono de la siguiente.
                .padding(top = 4.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(app.icon, CellIconSize)
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            IconLabel(app.label)
        }
    }
}

/** La app cogida, algo más grande y por encima del dedo para que no la tape. */
@Composable
private fun DraggedIcon(drag: DragState) {
    val half = with(LocalDensity.current) { (DragIconSize / 2).toPx() }
    AppIcon(
        icon = drag.app.icon,
        size = DragIconSize,
        modifier =
            Modifier.offset {
                IntOffset((drag.position.x - half).roundToInt(), (drag.position.y - half * 2.4f).roundToInt())
            }
    )
}
