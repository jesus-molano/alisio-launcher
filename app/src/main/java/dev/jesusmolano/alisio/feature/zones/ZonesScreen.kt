package dev.jesusmolano.alisio.feature.zones

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesusmolano.alisio.R
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
import dev.jesusmolano.alisio.feature.home.Trace
import dev.jesusmolano.alisio.feature.home.TraceRailWidth
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.components.RoundIconButton
import dev.jesusmolano.alisio.ui.components.onPinch
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import kotlin.math.roundToInt

private val StartMargin = 20.dp

// Ancho mínimo de un icono con su nombre: de aquí sale cuántos caben a lo ancho.
private val MinCellWidth = 76.dp
private const val MIN_COLUMNS = 3
private val IslandGap = 8.dp
private val IslandPadding = 6.dp
private val IslandTitleHeight = 48.dp
private val CellIconSize = 48.dp
private val DragIconSize = 64.dp

// Franja junto al borde superior e inferior en la que, con una app cogida, la lista se desplaza sola.
private val AutoScrollEdge = 88.dp
private val AutoScrollStep = 14.dp
private val IslandShape = AlisioTokens.CardShape
private val RestTopShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
private val RestBottomShape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
private val SquareShape = RoundedCornerShape(0.dp)

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
 * @param onGrantUsage abre el acceso a uso del sistema, para el uso de hoy del Interior de app.
 */
@Composable
fun ZonesScreen(
    viewModel: ZonesViewModel,
    prefs: Prefs,
    onClose: () -> Unit,
    onGrantUsage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
    val usageGranted by viewModel.usageGranted.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val view = LocalView.current
    val density = LocalDensity.current
    val bars = WindowInsets.systemBars.asPaddingValues()
    val listState = rememberLazyListState()
    val geometry = remember { Geometry() }
    var drag by remember { mutableStateOf<DragState?>(null) }
    // Clave de la app cuyo Interior está abierto: si la app desaparece, la hoja se cierra sola.
    var menuKey by remember { mutableStateOf<String?>(null) }
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
            .drawBehind {
                // El fondo de pantalla se intuye detrás, como en el Inicio, pero aquí manda el contenido.
                drawRect(AlisioTokens.Background.copy(alpha = 0.93f))
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(AlisioTokens.Accent.copy(alpha = 0.09f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = size.width * 1.1f
                    )
                )
            }
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
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        }
                        if (dropped) {
                            val target = picked.over
                            when {
                                target != null && picked.accepts(target) -> {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    viewModel.move(picked.app, target)
                                }

                                !picked.moved -> menuKey = picked.app.key.toString()
                            }
                        }
                    } finally {
                        // También si el sistema cancela el gesto a medias.
                        drag = null
                    }
                }
            }
    ) {
        val contentWidth = maxWidth - StartMargin - TraceRailWidth
        val columns = (contentWidth / MinCellWidth).toInt().coerceAtLeast(MIN_COLUMNS)
        val zones = state?.zones.orEmpty()
        val islands = remember(zones) { zones.filter { it.id != ZoneId.Rest } }
        val rest = remember(zones) { zones.firstOrNull { it.id == ZoneId.Rest } }
        val rows = remember(islands, columns) { packZones(islands.map { it.apps.size }, columns) }
        val restRows = remember(rest, columns) { rest?.apps.orEmpty().chunked(columns) }
        val restSections = remember(rest) { rest?.apps.orEmpty().toSections { it.label } }
        val dragging = drag

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = StartMargin,
                    // El índice de Trazo ocupa el borde derecho, como en el Inicio.
                    end = TraceRailWidth,
                    top = bars.calculateTopPadding() + 24.dp,
                    bottom = bars.calculateBottomPadding() + 24.dp
                )
        ) {
            item(key = "header") { Header(onNew = { editing = ZoneEdit.New }) }

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
                            onMenu = { menuKey = it.key.toString() },
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
                item(key = "rest-title") {
                    RestPart("title", RestTopShape, dragging, geometry) {
                        IslandTitle(zoneName(ZoneId.Rest, null))
                    }
                }
                items(restRows.size, key = { "rest-$it" }) { index ->
                    RestPart(index, SquareShape, dragging, geometry) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = IslandPadding)) {
                            Cells(
                                apps = restRows[index],
                                columns = columns,
                                zone = ZoneId.Rest,
                                showLabels = prefs.zonesLabels,
                                drag = dragging,
                                geometry = geometry,
                                onLaunch = ::launch,
                                onMenu = { menuKey = it.key.toString() }
                            )
                        }
                    }
                }
                item(key = "rest-end") {
                    RestPart("end", RestBottomShape, dragging, geometry) {
                        if (restRows.isEmpty()) EmptyIsland() else Spacer(Modifier.height(IslandPadding))
                    }
                }
            }

            item(key = "footer") {
                Text(
                    text = stringResource(R.string.zones_footer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlisioTokens.TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)
                )
            }
        }

        // Con una app cogida el índice se retira: el borde derecho es entonces sitio para soltar.
        if (dragging == null) {
            Trace(
                sections = restSections,
                railPadding =
                    PaddingValues(
                        top = bars.calculateTopPadding() + 104.dp,
                        bottom = bars.calculateBottomPadding() + 24.dp
                    ),
                onLaunch = ::launch
            )
        } else {
            DraggedIcon(dragging)
        }
    }

    val current = state
    val menuApp =
        remember(menuKey, current) {
            current?.zones?.firstNotNullOfOrNull { zone -> zone.apps.firstOrNull { it.key.toString() == menuKey } }
        }
    LaunchedEffect(menuKey, menuApp, current) {
        if (menuKey != null && menuApp == null && current != null) menuKey = null
    }
    if (menuApp != null) {
        AppMenuSheet(
            app = menuApp,
            apps = emptyList(),
            shortcuts = shortcuts,
            target = null,
            // El destino al deslizar es cosa de los iconos del dock: aquí no hay gesto que configurar.
            gesturesEnabled = false,
            usage = AppUsage(usageGranted, today = { viewModel.usageToday(menuApp) }, onGrant = onGrantUsage),
            slot = null,
            zones = zoneMove(current, menuApp) { viewModel.move(menuApp, it) },
            shortcutIcon = viewModel::shortcutIcon,
            onOpenShortcut = { shortcut ->
                menuKey = null
                if (!viewModel.launch(shortcut)) {
                    val message = resources.getString(R.string.app_launch_failed, shortcut.label)
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            onSetTarget = {},
            onAppInfo = {
                menuKey = null
                viewModel.openAppInfo(menuApp)
            },
            onDismiss = { menuKey = null }
        )
    }

    editing?.let { edit ->
        ZoneEditSheet(
            edit = edit,
            onSave = { name ->
                when (edit) {
                    ZoneEdit.New -> viewModel.createZone(name)
                    is ZoneEdit.Existing -> viewModel.renameZone(edit.id, name)
                }
                editing = null
            },
            onDelete = {
                (edit as? ZoneEdit.Existing)?.let { viewModel.deleteZone(it.id) }
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun Header(onNew: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = stringResource(R.string.zones_title),
                style = MaterialTheme.typography.headlineLarge,
                color = AlisioTokens.Text
            )
            Text(
                text = stringResource(R.string.zones_hint_back),
                style = MaterialTheme.typography.bodyMedium,
                color = AlisioTokens.TextSecondary
            )
        }
        RoundIconButton(R.drawable.ic_add, stringResource(R.string.zone_new), AlisioTokens.Text, onNew)
    }
}

/** Fondo de una isla según lo que pase con la app cogida: se enciende bajo el dedo si puede recibirla. */
private fun Modifier.islandSurface(zone: ZoneId, drag: DragState?, shape: Shape): Modifier {
    val hovered = drag != null && drag.over == zone && drag.accepts(zone)
    // Las islas a las que la app no puede ir se apagan mientras dura el arrastre; la suya no.
    val blocked = drag != null && zone != drag.from && !drag.accepts(zone)
    return this
        .alpha(if (blocked) 0.45f else 1f)
        .background(AlisioTokens.Surface.copy(alpha = 0.78f), shape)
        .then(if (hovered) Modifier.background(AlisioTokens.AccentSoft, shape) else Modifier)
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
            .islandSurface(zone.id, drag, IslandShape)
            .padding(bottom = IslandPadding)
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
                Row(Modifier.fillMaxWidth().padding(horizontal = IslandPadding)) {
                    Cells(rowApps, span, zone.id, showLabels, drag, geometry, onLaunch, onMenu)
                }
            }
        }
    }
}

@Composable
private fun IslandTitle(name: String, modifier: Modifier = Modifier, editable: Boolean = false) {
    Row(
        modifier.fillMaxWidth().height(IslandTitleHeight).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name.uppercase(),
            style = AlisioTokens.SectionLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (editable) {
            Spacer(Modifier.width(6.dp))
            // Decorativo: lo que hace el toque lo dice la descripción de la fila.
            Icon(
                painter = painterResource(R.drawable.ic_edit),
                contentDescription = null,
                tint = AlisioTokens.TextTertiary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** Lo que enseña una isla sin apps: que es un sitio donde soltarlas. */
@Composable
private fun EmptyIsland() {
    Text(
        text = stringResource(R.string.zone_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = AlisioTokens.TextTertiary,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 18.dp)
    )
}

/**
 * Un tramo de la isla Resto. Cada tramo es un elemento de la lista perezosa y todos cuentan como
 * la misma isla al soltar.
 *
 * @param part lo que distingue a este tramo de los demás.
 */
@Composable
private fun RestPart(part: Any, shape: Shape, drag: DragState?, geometry: Geometry, content: @Composable () -> Unit) {
    val key = remember(part) { "rest" to part }
    DisposableEffect(key) { onDispose { geometry.areas.remove(key) } }
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                geometry.areas.getOrPut(key) { Geometry.Area(ZoneId.Rest, coords) }.coords = coords
            }
            .islandSurface(ZoneId.Rest, drag, shape)
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
            modifier = Modifier.weight(1f)
        )
    }
    repeat(columns - apps.size) { Spacer(Modifier.weight(1f)) }
}

/**
 * Una app de una isla. El toque la abre; cogerla lo detecta la pantalla, que sabe dónde está cada
 * icono por [geometry]. Con TalkBack no hay arrastre: la acción «Mover a otra zona» abre su Interior.
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
                .alpha(if (picked) 0.25f else 1f)
                .heightIn(min = 64.dp)
                .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppIcon(app.icon, CellIconSize)
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.label,
                style = AlisioTokens.DockLabel,
                color = AlisioTokens.Text.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
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
