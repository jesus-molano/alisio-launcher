package dev.jesusmolano.alisio.feature.home

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.notify.packagesWithDot
import dev.jesusmolano.alisio.core.now.NowRule
import dev.jesusmolano.alisio.core.now.NowSuggestion
import dev.jesusmolano.alisio.core.search.filterByName
import dev.jesusmolano.alisio.core.search.moved
import dev.jesusmolano.alisio.core.text.takeGraphemes
import dev.jesusmolano.alisio.core.zones.Pinch
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.toSections
import dev.jesusmolano.alisio.data.home.StackIcon
import dev.jesusmolano.alisio.data.home.SwipeTarget
import dev.jesusmolano.alisio.data.settings.Prefs
import dev.jesusmolano.alisio.feature.appmenu.AppMenuSheet
import dev.jesusmolano.alisio.feature.appmenu.AppUsage
import dev.jesusmolano.alisio.feature.appmenu.DockPlacement
import dev.jesusmolano.alisio.feature.appmenu.SlotActions
import dev.jesusmolano.alisio.feature.search.appLabel
import dev.jesusmolano.alisio.feature.search.searchHint
import dev.jesusmolano.alisio.feature.zones.ZonesViewModel
import dev.jesusmolano.alisio.feature.zones.zoneMove
import dev.jesusmolano.alisio.ui.components.AlisioSheet
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.components.AppPicker
import dev.jesusmolano.alisio.ui.components.DockIcon
import dev.jesusmolano.alisio.ui.components.FolderTileIcon
import dev.jesusmolano.alisio.ui.components.IconLabel
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.LocalUndo
import dev.jesusmolano.alisio.ui.components.PillTextField
import dev.jesusmolano.alisio.ui.components.PreviewChoice
import dev.jesusmolano.alisio.ui.components.RoundIconButton
import dev.jesusmolano.alisio.ui.components.SectionLabel
import dev.jesusmolano.alisio.ui.components.SheetTitle
import dev.jesusmolano.alisio.ui.components.SymbolTileField
import dev.jesusmolano.alisio.ui.components.SymbolTileIcon
import dev.jesusmolano.alisio.ui.components.TextAction
import dev.jesusmolano.alisio.ui.components.TitleTextField
import dev.jesusmolano.alisio.ui.components.UnreadDot
import dev.jesusmolano.alisio.ui.components.VectorTileIcon
import dev.jesusmolano.alisio.ui.components.clockAutoSize
import dev.jesusmolano.alisio.ui.components.formatClock
import dev.jesusmolano.alisio.ui.components.onPinch
import dev.jesusmolano.alisio.ui.components.rememberLightWallpaper
import dev.jesusmolano.alisio.ui.components.rememberNow
import dev.jesusmolano.alisio.ui.components.wallpaperVeil
import dev.jesusmolano.alisio.ui.theme.AlisioMotion
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import dev.jesusmolano.alisio.ui.theme.onWallpaper
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val DockIconSize = AlisioTokens.DockIconSize

// Ancho de un hueco del dock: el icono y un poco de aire a cada lado para que el nombre respire.
internal val DockItemWidth = DockIconSize + 8.dp
private val PillMinHeight = 56.dp
private val StackOrderRowMinHeight = 56.dp
private val DockHeight = 86.dp
private val OpenSearchDrag = 48.dp
private val ShowNotificationsDrag = 48.dp

// Recorrido hacia arriba sobre un icono del dock que abre su destino secundario.
private val SwipeOpenDrag = 40.dp
private val SwipeMaxLift = 14.dp
private const val SWIPE_LIFT_RATIO = 0.5f

private const val STACK_GLYPH_LENGTH = 2
private const val TAG = "HomeScreen"

// La pila abierta: cuatro columnas de filo a filo, con sitio para el nombre bajo cada icono.
private const val STACK_COLUMNS = 4
private val StackCellWidth = DockIconSize + 24.dp

/** La app cuyo Interior se abre: [slot] es su hueco del dock, o `null` si es una app de una pila. */
private class MenuTarget(val key: String, val slot: Int?)

/**
 * Inicio de §6: reloj y fecha arriba; abajo, el dock y la píldora de la barra. Cada hueco del dock
 * es una app o una pila con nombre; mantenerlo pulsado lo configura. Deslizar hacia arriba en una
 * zona vacía o tocar la píldora abre la barra; mantener pulsada una zona vacía abre los ajustes de
 * Alisio; pellizcar abre Zonas. Trazo va en el borde derecho. Con el servicio de accesibilidad
 * activado, dos toques en una zona vacía bloquean la pantalla y deslizar hacia abajo baja las
 * notificaciones.
 *
 * @param onGrantHomeRole pide ser la app de inicio; `null` si Alisio ya lo es.
 * @param onLockScreen bloquea la pantalla; `null` si el gesto está apagado o no hay servicio.
 * @param onShowNotifications baja las notificaciones; `null` en los mismos casos.
 * @param zones de dónde sale en qué isla está cada app, para «Mover a otra isla» del Interior de app.
 * @param homePresses sube cada vez que se pulsa «inicio» estando ya en Alisio; cierra las hojas abiertas.
 * @param menuRequest clave de una app cuyo Interior pide abrir otra pantalla (la barra o Zonas, al
 *   mantener una app); [onMenuRequestTaken] avisa de que ya se ha atendido.
 * @param searchOpen con la barra encima, Trazo se retira: su índice seguiría recibiendo toques y
 *   quitándole al sistema el gesto atrás del borde derecho.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    zones: ZonesViewModel,
    onOpenZones: () -> Unit,
    homePresses: Int,
    searchOpen: Boolean,
    prefs: Prefs,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onGrantUsage: () -> Unit,
    onGrantHomeRole: (() -> Unit)?,
    onLockScreen: (() -> Unit)?,
    onShowNotifications: (() -> Unit)?,
    modifier: Modifier = Modifier,
    menuRequest: String? = null,
    onMenuRequestTaken: () -> Unit = {}
) {
    val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
    val swipeTargets by viewModel.swipeTargets.collectAsStateWithLifecycle()
    val usageGranted by viewModel.usageGranted.collectAsStateWithLifecycle()
    val zonesState by zones.state.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val loadedDock by viewModel.dock.collectAsStateWithLifecycle()
    val dock = loadedDock.orEmpty()
    val nowSuggestion by viewModel.now.collectAsStateWithLifecycle()
    val now = nowSuggestion?.takeIf { prefs.nowEnabled }
    val nowPlaying by viewModel.player.collectAsStateWithLifecycle()
    val player = nowPlaying?.takeIf { prefs.playerEnabled }
    val unread by viewModel.unread.collectAsStateWithLifecycle()
    val dotted =
        remember(unread, prefs.unreadDots, prefs.unreadMuted) {
            if (prefs.unreadDots) packagesWithDot(unread, prefs.unreadMuted) else emptySet()
        }
    val context = LocalContext.current
    val resources = LocalResources.current
    val bars = WindowInsets.systemBars.asPaddingValues()
    val density = LocalDensity.current
    val undo = LocalUndo.current
    val lightWallpaper = rememberLightWallpaper()
    // Alto real del pie (Ahora, reproductor, dock y píldora): el índice de Trazo acaba donde empieza.
    // Se mide en vez de sumar constantes porque el pie crece con la letra del sistema.
    var footerHeight by remember { mutableStateOf(0.dp) }
    // Con algo encima (Zonas, los ajustes, un trazo) el contenido del Inicio se retira: los velos de
    // esas pantallas dejan ver el fondo de pantalla, y el reloj y el dock asomarían detrás.
    var tracing by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (searchOpen || tracing) 0f else 1f,
        animationSpec = AlisioMotion.standard(),
        label = "home-content"
    )
    // Posición del hueco que se está configurando y de la pila abierta.
    var editing by remember { mutableStateOf<Int?>(null) }
    var openStack by remember { mutableStateOf<Int?>(null) }
    // La app cuyo Interior está abierto: una del dock o una de una pila.
    var appMenu by remember { mutableStateOf<MenuTarget?>(null) }
    val dockSuggestions by viewModel.dockSuggestions.collectAsStateWithLifecycle()

    LaunchedEffect(homePresses) {
        editing = null
        openStack = null
        appMenu = null
    }
    // La barra pide el Interior de una app que no está en el dock: se abre aquí, donde ya están sus
    // datos y sus acciones, como el de una app de una pila.
    LaunchedEffect(menuRequest) {
        if (menuRequest != null) {
            appMenu = MenuTarget(menuRequest, slot = null)
            onMenuRequestTaken()
        }
    }

    // Los detectores de gestos viven lo que vive la pantalla: leen siempre la orden de ahora.
    val lockScreen by rememberUpdatedState(onLockScreen)
    val showNotifications by rememberUpdatedState(onShowNotifications)
    val canLock = onLockScreen != null

    fun launchFailed(label: String) {
        Toast.makeText(context, resources.getString(R.string.app_launch_failed, label), Toast.LENGTH_SHORT).show()
    }

    fun launch(app: AppEntry) {
        if (!viewModel.launch(app)) launchFailed(app.label)
    }

    fun launch(target: SwipeTarget) {
        if (!viewModel.launch(target)) launchFailed(target.label)
    }

    // Vaciar un hueco se hace al primer toque y se puede deshacer: vuelve su app o su pila entera.
    fun clearSlot(position: Int) {
        val message =
            resources.getString(
                if (dock.getOrNull(
                        position
                    ) is DockSlot.Stack
                ) {
                    R.string.undo_stack_removed
                } else {
                    R.string.undo_slot_cleared
                }
            )
        undo.show(message, viewModel.clearDockSlot(position))
    }

    Box(
        modifier
            .fillMaxSize()
            .wallpaperVeil(prefs.wallpaperDim.factor, lightWallpaper)
            // Pellizcar hacia dentro abre Zonas (§5.7). Va antes que los demás gestos: con dos dedos
            // en la pantalla ni se abre la barra ni saltan los ajustes.
            .onPinch(enabled = prefs.zonesPinch && !searchOpen) { if (it == Pinch.IN) onOpenZones() }
            .pointerInput(onOpenSearch) {
                var dragged = 0f
                var pulledDown = false
                detectVerticalDragGestures(
                    onDragStart = {
                        dragged = 0f
                        pulledDown = false
                    },
                    onVerticalDrag = { _, amount ->
                        dragged += amount
                        // Hacia abajo se actúa al pasar el umbral, sin esperar a soltar, como al tirar
                        // de la barra de estado. Una vez por gesto.
                        if (!pulledDown && dragged > ShowNotificationsDrag.toPx()) {
                            showNotifications?.let {
                                pulledDown = true
                                it()
                            }
                        }
                    },
                    // Un gesto que ya ha bajado las notificaciones no abre además la barra.
                    onDragEnd = { if (!pulledDown && dragged < -OpenSearchDrag.toPx()) onOpenSearch() }
                )
            }
            // El detector de dos toques solo existe con el gesto disponible: sin él queda el de
            // siempre, que no espera a un segundo toque. Aquí no hay acción de un solo toque, así que
            // esa espera no retrasa nada; los iconos y el reloj se quedan sus toques antes.
            .pointerInput(onOpenSettings, canLock) {
                detectTapGestures(
                    onLongPress = { onOpenSettings() },
                    onDoubleTap = if (canLock) ({ lockScreen?.invoke() }) else null
                )
            }
    ) {
        ClockAndDate(
            Modifier
                .align(Alignment.TopStart)
                .graphicsLayer { alpha = contentAlpha }
                .padding(
                    top = bars.calculateTopPadding() + 40.dp,
                    start = AlisioTokens.Edge,
                    end = AlisioTokens.Edge
                )
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = contentAlpha }
                .onSizeChanged { footerHeight = with(density) { it.height.toDp() } }
                .padding(bottom = bars.calculateBottomPadding() + 16.dp)
        ) {
            now?.let { (suggestion, app) ->
                NowCard(
                    suggestion = suggestion,
                    app = app,
                    onOpen = { launch(app) },
                    onDismiss = {
                        val dismissal = viewModel.dismissNow(suggestion)
                        val message =
                            resources.getString(
                                if (dismissal.silenced) R.string.undo_now_silenced else R.string.undo_now_hidden
                            )
                        undo.show(message, dismissal.undo)
                    },
                    modifier = Modifier.padding(horizontal = AlisioTokens.Outset)
                )
                Spacer(Modifier.height(AlisioTokens.PlateGap))
            }
            player?.let { playing ->
                val playingApp = apps.orEmpty().firstOrNull { it.component.packageName == playing.packageName }
                PlayerBar(
                    player = playing,
                    appIcon = playingApp?.icon,
                    // Por la lista de apps siempre que se pueda: así la apertura cuenta en el historial.
                    onOpen = { if (playingApp != null) launch(playingApp) else viewModel.openPlayerApp() },
                    onToggle = viewModel::togglePlayback,
                    onNext = viewModel::skipToNext,
                    onHide = viewModel::hidePlayer,
                    modifier = Modifier.padding(horizontal = AlisioTokens.Outset)
                )
                Spacer(Modifier.height(AlisioTokens.PlateGap))
            }
            DockRow {
                dock.forEachIndexed { position, slot ->
                    val itemModifier = Modifier.width(DockItemWidth)
                    val showLabel = prefs.dockLabels
                    when (slot) {
                        DockSlot.Empty ->
                            DockItem(
                                label = "",
                                description = stringResource(R.string.dock_empty_description),
                                onClick = { editing = position },
                                onLongClick = { editing = position },
                                clickLabel = stringResource(R.string.dock_empty_action),
                                // Un hueco vacío no tiene nombre que enseñar.
                                showLabel = false,
                                modifier = itemModifier
                            ) { VectorTileIcon(R.drawable.ic_add) }

                        is DockSlot.App -> {
                            // Con los gestos apagados el destino sigue guardado, pero el icono no lo anuncia.
                            val target = swipeTargets[slot.app.key.toString()]?.takeIf { prefs.dockGestures }
                            DockItem(
                                label = slot.app.label,
                                description = null,
                                onClick = { launch(slot.app) },
                                onLongClick = { appMenu = MenuTarget(slot.app.key.toString(), position) },
                                longClickLabel = stringResource(R.string.dock_app_menu),
                                showLabel = showLabel,
                                unread = slot.app.component.packageName in dotted,
                                swipe = target?.let { DockSwipe(it.label) { launch(it) } },
                                modifier = itemModifier
                            ) { AppIcon(slot.app.icon, DockIconSize) }
                        }

                        is DockSlot.Stack ->
                            DockItem(
                                label = slot.name,
                                description = null,
                                onClick = { if (slot.apps.isEmpty()) editing = position else openStack = position },
                                onLongClick = { editing = position },
                                showLabel = showLabel,
                                unread = slot.apps.any { it.component.packageName in dotted },
                                modifier = itemModifier
                            ) { StackSlotIcon(slot) }
                    }
                }
            }
            Spacer(Modifier.height(AlisioTokens.PlateGap))
            // También depende de las apps instaladas: instalar o quitar el asistente cambia el texto.
            val assistant = remember(prefs.assistantPackage, apps) { context.appLabel(prefs.assistantPackage) }
            SearchPill(searchHint(assistant), onOpenSearch, Modifier.padding(horizontal = AlisioTokens.Outset))
        }

        val sections = remember(apps) { apps.orEmpty().toSections { it.label } }
        if (!searchOpen) {
            Trace(
                sections = sections,
                railPadding =
                    PaddingValues(
                        top = bars.calculateTopPadding() + 16.dp,
                        bottom = footerHeight + AlisioTokens.PlateGap
                    ),
                onLaunch = ::launch,
                onOpenZones = onOpenZones,
                onTracing = { tracing = it }
            )
        }
    }

    openStack?.let { position ->
        val stack = dock.getOrNull(position) as? DockSlot.Stack
        if (stack == null) {
            openStack = null
        } else {
            StackSheet(
                stack = stack,
                onLaunch = {
                    openStack = null
                    launch(it)
                },
                // Nunca una hoja sobre otra: el Interior sustituye a la pila abierta.
                onMenu = {
                    openStack = null
                    appMenu = MenuTarget(it.key.toString(), slot = null)
                },
                onEdit = {
                    openStack = null
                    editing = position
                },
                onDismiss = { openStack = null }
            )
        }
    }

    val defaultStackName = stringResource(R.string.stack_default_name)
    appMenu?.let { menu ->
        val position = menu.slot
        val app =
            if (position != null) {
                (dock.getOrNull(position) as? DockSlot.App)?.app?.takeIf { it.key.toString() == menu.key }
            } else {
                apps.orEmpty().firstOrNull { it.key.toString() == menu.key }
            }
        if (app == null) {
            // La app se ha desinstalado o el hueco ha cambiado con la hoja abierta.
            appMenu = null
        } else {
            AppMenuSheet(
                app = app,
                apps = apps.orEmpty(),
                shortcuts = shortcuts,
                // El destino al deslizar es cosa de los iconos sueltos del dock: una app de una pila no lo tiene.
                target = swipeTargets[app.key.toString()].takeIf { position != null },
                gesturesEnabled = prefs.dockGestures && position != null,
                usage = AppUsage(usageGranted, today = { viewModel.usageToday(app) }, onGrant = onGrantUsage),
                slot =
                    position?.let {
                        SlotActions(
                            onChangeApp = {
                                appMenu = null
                                editing = position
                            },
                            onMakeStack = {
                                // Como en la hoja del hueco: se convierte y se pasa a elegir sus apps.
                                viewModel.makeDockStack(position, defaultStackName)
                                appMenu = null
                                editing = position
                            },
                            onClear = {
                                clearSlot(position)
                                appMenu = null
                            }
                        )
                    },
                // Desde Zonas, la barra o una pila, la app no tiene hueco propio: se le ofrecen todos.
                dock =
                    if (position == null && dock.isNotEmpty()) {
                        DockPlacement(
                            slots = dock.size,
                            holds = { slot -> dock[slot].holds(app) },
                            description = { slot -> dockSlotName(dock[slot]) },
                            icon = { slot, size -> DockSlotIcon(dock[slot], size) },
                            onPlace = { slot ->
                                // La hoja se cierra: el dock no se ve desde Zonas ni desde la barra, y
                                // la barra de deshacer es la que dice dónde ha quedado la app.
                                appMenu = null
                                when (val target = dock[slot]) {
                                    // Una pila no se pisa: la app entra en ella.
                                    is DockSlot.Stack -> {
                                        viewModel.toggleInStack(slot, app)
                                        undo.show(
                                            resources.getString(R.string.undo_stack_added, app.label, target.name)
                                        ) { viewModel.toggleInStack(slot, app) }
                                    }

                                    else ->
                                        undo.show(
                                            resources.getString(R.string.undo_dock_placed, app.label),
                                            viewModel.setDockApp(slot, app)
                                        )
                                }
                            }
                        )
                    } else {
                        null
                    },
                onGrantHomeRole = onGrantHomeRole,
                zones = zoneMove(zonesState, app) { zones.move(app, it) },
                shortcutIcon = viewModel::shortcutIcon,
                onOpenShortcut = { shortcut ->
                    appMenu = null
                    launch(SwipeTarget.Shortcut(shortcut))
                },
                onSetTarget = { viewModel.setSwipeTarget(app, it) },
                onAppInfo = {
                    appMenu = null
                    viewModel.openAppInfo(app)
                },
                onDismiss = { appMenu = null }
            )
        }
    }

    editing?.let { position ->
        SlotSheet(
            slot = dock.getOrNull(position) ?: DockSlot.Empty,
            apps = apps.orEmpty(),
            // Solo con el dock entero vacío: después, cada hueco se elige a mano.
            fillWith = dockSuggestions.takeIf { dock.all { it == DockSlot.Empty } }.orEmpty(),
            onFill = {
                undo.show(resources.getString(R.string.undo_dock_filled), viewModel.fillDock())
                editing = null
            },
            onPickApp = { app ->
                val replaced = dock.getOrNull(position) is DockSlot.App
                val restore = viewModel.setDockApp(position, app)
                // Sustituir una app por otra también se puede deshacer; llenar un hueco vacío no lo necesita.
                if (replaced) undo.show(resources.getString(R.string.undo_slot_replaced), restore)
                editing = null
            },
            onToggleInStack = { viewModel.toggleInStack(position, it) },
            onMakeStack = { viewModel.makeDockStack(position, defaultStackName) },
            onRenameStack = { viewModel.renameDockStack(position, it) },
            onSetStackIcon = { icon, glyph -> viewModel.setStackIcon(position, icon, glyph) },
            onReorderStack = { viewModel.reorderStack(position, it) },
            onClear = {
                clearSlot(position)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun ClockAndDate(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = rememberNow()
    val time = formatClock(now)
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(now, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        now.format(DateTimeFormatter.ofPattern(pattern, locale)).replaceFirstChar { it.titlecase(locale) }
    }
    val noRipple = remember { MutableInteractionSource() }
    val clock = AlisioTokens.Clock.onWallpaper()
    Column(modifier) {
        Text(
            text = time,
            style = clock,
            maxLines = 1,
            autoSize = clockAutoSize(clock),
            modifier =
                Modifier.clickable(
                    interactionSource = noRipple,
                    indication = null,
                    onClickLabel = stringResource(R.string.home_open_clock),
                    role = Role.Button
                ) {
                    context.openClockApp()
                }
        )
        Text(
            text = date,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium).onWallpaper(),
            modifier =
                Modifier
                    .clickable(
                        interactionSource = noRipple,
                        indication = null,
                        onClickLabel = stringResource(R.string.home_open_calendar),
                        role = Role.Button
                    ) {
                        context.open(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
                    }
                    .heightIn(min = 48.dp)
                    .padding(start = 6.dp, top = 4.dp, bottom = 12.dp, end = 12.dp)
        )
    }
}

/**
 * Abre la app de Reloj. `ACTION_SHOW_ALARMS` exige el permiso `SET_ALARM` (en One UI falla sin él),
 * así que solo se usa para saber qué app es el reloj y se abre por su icono de inicio.
 */
private fun Context.openClockApp() {
    // Con varias apps de reloj y ninguna predeterminada, `resolveActivity` devuelve el selector del
    // sistema (paquete «android»), que no tiene icono de inicio: se toma la primera app real.
    val clockPackage =
        packageManager.queryIntentActivities(Intent(AlarmClock.ACTION_SHOW_ALARMS), 0)
            .map { it.activityInfo.packageName }
            .firstOrNull { it != "android" }
    val intent = clockPackage?.let(packageManager::getLaunchIntentForPackage)
    if (intent == null) Log.w(TAG, "No hay app de reloj que abrir") else open(intent)
}

private fun Context.open(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: RuntimeException) {
        // Sin app de reloj o de calendario que lo atienda: no hay nada que abrir.
        Log.w(TAG, "No se ha podido abrir $intent", e)
    }
}

/**
 * La fila del dock, igual en el Inicio y en su miniatura. Mientras carga guarda su sitio para que la
 * píldora no salte. Los huecos se reparten de filo a filo: el primer icono empieza donde empiezan el
 * reloj y la fecha.
 */
@Composable
internal fun DockRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = DockHeight)
            .padding(horizontal = AlisioTokens.Edge - (DockItemWidth - DockIconSize) / 2),
        horizontalArrangement = Arrangement.SpaceBetween,
        content = content
    )
}

/** Destino secundario de un icono del dock: [label] es lo que abre [open], para decirlo en voz alta. */
private class DockSwipe(val label: String, val open: () -> Unit)

/**
 * @param swipe con destino secundario, deslizar hacia arriba sobre el icono lo abre y el icono lleva
 *   una marca; sin él, el gesto sigue de largo y abre la barra, como en una zona vacía.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockItem(
    label: String,
    description: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    clickLabel: String? = null,
    longClickLabel: String? = null,
    unread: Boolean = false,
    swipe: DockSwipe? = null,
    icon: @Composable () -> Unit
) {
    val name = description ?: label
    // El punto no se lee solo: lo que significa va en la descripción del icono.
    val spoken = if (unread) stringResource(R.string.dock_unread, name) else name
    val swipeAction = swipe?.let { stringResource(R.string.dock_swipe_open, it.label) }
    // El detector vive lo que vive el icono; así abre siempre el destino de ahora, no el de cuando empezó.
    val currentSwipe by rememberUpdatedState(swipe)
    val lift = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    DockIcon(
        label = label,
        showLabel = showLabel,
        unread = unread,
        swipeMark = swipe != null,
        onWallpaper = true,
        iconModifier = Modifier.graphicsLayer { translationY = lift.value },
        modifier =
            modifier
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = clickLabel,
                    onClick = onClick,
                    onLongClickLabel = longClickLabel,
                    onLongClick = onLongClick
                )
                .then(
                    if (swipe == null) {
                        Modifier
                    } else {
                        // Va en el propio icono para ganarle el arrastre al Inicio: al consumirlo aquí,
                        // el detector de fuera (el que abre la barra) lo da por perdido.
                        Modifier.pointerInput(Unit) {
                            val openAt = -SwipeOpenDrag.toPx()
                            val maxLift = SwipeMaxLift.toPx()
                            var dragged = 0f
                            detectVerticalDragGestures(
                                onDragStart = { dragged = 0f },
                                onVerticalDrag = { _, amount ->
                                    val wasArmed = dragged <= openAt
                                    dragged += amount
                                    // Un toque al cruzar el umbral: soltar a partir de aquí abre el destino.
                                    if (!wasArmed && dragged <= openAt) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                    // El icono acompaña al dedo a medio paso y con tope: se nota que
                                    // responde sin que parezca que se puede arrastrar a otro sitio.
                                    val target = (dragged * SWIPE_LIFT_RATIO).coerceIn(-maxLift, 0f)
                                    scope.launch { lift.snapTo(target) }
                                },
                                onDragEnd = {
                                    if (dragged <= openAt) currentSwipe?.open?.invoke()
                                    scope.launch { lift.animateTo(0f, AlisioMotion.follow()) }
                                },
                                onDragCancel = { scope.launch { lift.animateTo(0f, AlisioMotion.follow()) } }
                            )
                        }
                    }
                )
                // Sin etiqueta visible, el nombre sigue llegando a TalkBack.
                .semantics {
                    contentDescription = spoken
                    // El gesto no existe para TalkBack: el destino secundario va como acción con nombre.
                    if (swipeAction != null) {
                        customActions =
                            listOf(
                                CustomAccessibilityAction(swipeAction) {
                                    currentSwipe?.open?.invoke()
                                    true
                                }
                            )
                    }
                }
                .padding(vertical = 4.dp),
        icon = icon
    )
}

/**
 * Un hueco del dock sin nombre ni gestos, al lado que se pida: lo que enseñan «Poner en el dock», la
 * miniatura del Inicio y las vistas previas de los ajustes.
 */
@Composable
internal fun DockSlotIcon(slot: DockSlot, size: Dp) {
    when (slot) {
        DockSlot.Empty -> VectorTileIcon(R.drawable.ic_add, size = size)
        is DockSlot.App -> AppIcon(slot.app.icon, size)
        is DockSlot.Stack -> StackSlotIcon(slot, size)
    }
}

@Composable
private fun dockSlotName(slot: DockSlot): String = when (slot) {
    DockSlot.Empty -> stringResource(R.string.dock_empty_description)
    is DockSlot.App -> slot.app.label
    is DockSlot.Stack -> slot.name
}

/** Si [app] ya está en el hueco: es su app o una de las de su pila. */
private fun DockSlot.holds(app: AppEntry): Boolean = when (this) {
    DockSlot.Empty -> false
    is DockSlot.App -> this.app.key == app.key
    is DockSlot.Stack -> apps.any { it.key == app.key }
}

/**
 * Icono de pila según su modo: carpeta con sus cuatro primeras apps, el icono de la primera o el
 * símbolo que le ha escrito el usuario. Sin apps, o sin símbolo escrito, es el dibujo de una pila.
 *
 * @param size lado del icono; el contenido se escala con él, para dibujarlo también en una vista previa.
 */
@Composable
internal fun StackSlotIcon(stack: DockSlot.Stack, size: Dp = DockIconSize) {
    when {
        stack.icon == StackIcon.SYMBOL && stack.glyph.isNotBlank() -> SymbolTileIcon(stack.glyph, size = size)
        stack.icon == StackIcon.SYMBOL || stack.apps.isEmpty() -> VectorTileIcon(R.drawable.ic_stacks, size = size)
        stack.icon == StackIcon.FIRST_APP -> AppIcon(stack.apps.first().icon, size)
        else -> FolderTileIcon(stack.apps.map { it.icon }, size = size)
    }
}

/** La píldora del pie del Inicio: tiene el aspecto del campo de la barra y la abre al tocarla. */
@Composable
internal fun SearchPill(hint: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = AlisioTokens.PillShape
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = PillMinHeight)
            .clip(shape)
            .background(AlisioTokens.Plate, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = AlisioTokens.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyLarge,
            color = AlisioTokens.TextSecondary
        )
    }
}

/**
 * Hoja con las apps de una pila, en rejilla como el dock del que sale: un toque abre la app y
 * mantenerla abre su Interior.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StackSheet(
    stack: DockSlot.Stack,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    val menuLabel = stringResource(R.string.dock_app_menu)
    AlisioSheet(onDismiss = onDismiss, expanded = false) {
        SheetTitle(stack.name)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            stack.apps.chunked(STACK_COLUMNS).forEach { row ->
                // De filo a filo, como el dock: el primer icono cae bajo el título.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AlisioTokens.Edge - (StackCellWidth - DockIconSize) / 2),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    row.forEach { app ->
                        Column(
                            Modifier
                                .width(StackCellWidth)
                                .clip(AlisioTokens.RowShape)
                                .combinedClickable(
                                    role = Role.Button,
                                    onLongClickLabel = menuLabel,
                                    onLongClick = { onMenu(app) },
                                    onClick = { onLaunch(app) }
                                )
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AppIcon(app.icon, DockIconSize)
                            Spacer(Modifier.height(6.dp))
                            IconLabel(app.label)
                        }
                    }
                    // Una fila incompleta guarda los huecos: sus iconos siguen en las mismas columnas.
                    repeat(STACK_COLUMNS - row.size) { Spacer(Modifier.width(StackCellWidth)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextAction(
                stringResource(R.string.stack_edit),
                onClick = onEdit,
                modifier = Modifier.padding(horizontal = AlisioTokens.Outset)
            )
        }
    }
}

/**
 * Hoja para configurar un hueco del dock: elegir su app, convertirlo en pila o vaciarlo. Si ya es
 * una pila, cambia su nombre y marca qué apps contiene; en ese caso elegir una app no cierra la hoja.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotSheet(
    slot: DockSlot,
    apps: List<AppEntry>,
    fillWith: List<AppEntry>,
    onFill: () -> Unit,
    onPickApp: (AppEntry) -> Unit,
    onToggleInStack: (AppEntry) -> Unit,
    onMakeStack: () -> Unit,
    onRenameStack: (String) -> Unit,
    onSetStackIcon: (StackIcon, String) -> Unit,
    onReorderStack: (List<AppEntry>) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val stack = slot as? DockSlot.Stack
    AlisioSheet(onDismiss = onDismiss) {
        if (stack == null) {
            val current = (slot as? DockSlot.App)?.app?.key
            SheetTitle(stringResource(R.string.slot_title))
            AppPicker(
                items = apps,
                key = { it.key.toString() },
                label = { it.label },
                icon = { it.icon },
                chosen = { it.key == current },
                onPick = onPickApp,
                modifier = Modifier.weight(1f)
            ) {
                if (fillWith.isNotEmpty()) {
                    ItemRow(
                        title = AnnotatedString(stringResource(R.string.slot_fill)),
                        leadingIcon = R.drawable.ic_auto_awesome,
                        // Las apps que pondrá, no una explicación: se elige sabiendo el resultado.
                        subtitle = fillWith.joinToString(stringResource(R.string.list_separator)) { it.label },
                        onClick = onFill
                    )
                }
                ItemRow(
                    title =
                        AnnotatedString(
                            stringResource(if (current == null) R.string.slot_new_stack else R.string.slot_make_stack)
                        ),
                    leadingIcon = R.drawable.ic_stacks,
                    subtitle = stringResource(R.string.slot_make_stack_hint),
                    onClick = onMakeStack
                )
                if (current != null) {
                    ItemRow(
                        title = AnnotatedString(stringResource(R.string.picker_clear)),
                        leadingIcon = R.drawable.ic_close,
                        secondary = true,
                        onClick = onClear
                    )
                }
            }
        } else {
            StackEditor(stack, apps, onToggleInStack, onRenameStack, onSetStackIcon, onReorderStack, onClear)
        }
    }
}

/**
 * El contenido de la hoja de una pila: su nombre, cómo se dibuja su icono, sus apps en orden (cada
 * una con su botón de quitar), las que se pueden añadir y, al pie junto al teclado, el buscador de
 * esas. Añadir o quitar no cierra la hoja.
 */
@Composable
private fun ColumnScope.StackEditor(
    stack: DockSlot.Stack,
    apps: List<AppEntry>,
    onToggleInStack: (AppEntry) -> Unit,
    onRenameStack: (String) -> Unit,
    onSetStackIcon: (StackIcon, String) -> Unit,
    onReorderStack: (List<AppEntry>) -> Unit,
    onRemove: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    // El nombre se edita en local y se guarda en cada cambio: releerlo de la base movería el cursor.
    var name by remember { mutableStateOf(stack.name) }
    var glyph by remember { mutableStateOf(stack.glyph) }
    val inStack = remember(stack.apps) { stack.apps.map { it.key }.toSet() }
    // «Añadir» solo ofrece lo que falta: lo que ya está en la pila se ve y se quita arriba.
    val addable = remember(apps, inStack, query) { apps.filter { it.key !in inStack }.filterByName(query) { it.label } }
    val symbolFocus = remember { FocusRequester() }
    // Elegir «Símbolo» lleva el teclado a su tesela; abrir la hoja de una pila que ya lo usa, no.
    var focusSymbol by remember { mutableStateOf(false) }
    LaunchedEffect(focusSymbol, stack.icon) {
        if (focusSymbol && stack.icon == StackIcon.SYMBOL) {
            symbolFocus.requestFocus()
            focusSymbol = false
        }
    }
    val symbolHint = stringResource(R.string.stack_symbol_hint)
    val addLabel = stringResource(R.string.stack_add_app)

    TitleTextField(
        value = name,
        onValueChange = {
            name = it
            if (it.isNotBlank()) onRenameStack(it.trim())
        },
        placeholder = stringResource(R.string.stack_name_hint)
    )
    Spacer(Modifier.height(AlisioTokens.SheetTitleGap - 8.dp))
    // Cada opción se enseña dibujada con las apps de esta pila, no descrita con palabras.
    Row(
        Modifier.fillMaxWidth().padding(horizontal = AlisioTokens.Outset),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StackIcon.entries.forEach { mode ->
            PreviewChoice(
                label =
                    stringResource(
                        when (mode) {
                            StackIcon.FOLDER -> R.string.stack_icon_folder
                            StackIcon.FIRST_APP -> R.string.stack_icon_first
                            StackIcon.SYMBOL -> R.string.stack_icon_symbol
                        }
                    ),
                selected = stack.icon == mode,
                onClick = {
                    onSetStackIcon(mode, glyph)
                    if (mode == StackIcon.SYMBOL) focusSymbol = true
                },
                modifier = Modifier.weight(1f)
            ) {
                if (mode == StackIcon.SYMBOL && stack.icon == StackIcon.SYMBOL) {
                    // El símbolo se escribe en su propia tesela: no hace falta otro campo en la hoja.
                    SymbolTileField(
                        value = glyph,
                        onValueChange = {
                            // Un símbolo o un emoji: no cabe más en el icono.
                            glyph = it.takeGraphemes(STACK_GLYPH_LENGTH)
                            onSetStackIcon(StackIcon.SYMBOL, glyph)
                        },
                        description = symbolHint,
                        focusRequester = symbolFocus
                    )
                } else {
                    StackSlotIcon(DockSlot.Stack(stack.name, stack.apps, mode, glyph))
                }
            }
        }
    }
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = AlisioTokens.Outset, vertical = 8.dp)
    ) {
        // Al buscar, la lista es solo lo que se puede añadir: lo que ya está no se mezcla con ello.
        if (query.isBlank() && stack.apps.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.stack_order_title)) }
            item { StackOrder(stack.apps, onReorderStack, onRemove = onToggleInStack) }
        }
        if (addable.isNotEmpty()) item { SectionLabel(stringResource(R.string.stack_add_title)) }
        items(addable, key = { it.key.toString() }) { app ->
            ItemRow(
                title = AnnotatedString(app.label),
                icon = app.icon,
                onClick = { onToggleInStack(app) },
                actions = {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = addLabel,
                        tint = AlisioTokens.TextSecondary,
                        // A la altura del asa de las filas de arriba.
                        modifier = Modifier.padding(start = 12.dp).size(24.dp)
                    )
                }
            )
        }
        if (query.isBlank()) {
            // Lo que borra va lo último y se puede deshacer.
            item {
                Spacer(Modifier.height(AlisioTokens.GroupGap))
                TextAction(stringResource(R.string.slot_remove_stack), onClick = onRemove, secondary = true)
            }
        }
    }
    PillTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = stringResource(R.string.picker_search),
        leadingIcon = R.drawable.ic_search,
        modifier =
            Modifier
                .imePadding()
                .padding(start = AlisioTokens.Outset, end = AlisioTokens.Outset, bottom = 12.dp)
    )
}

/**
 * Las apps de la pila en su orden, que es el de la hoja y el del icono de carpeta. Se reordenan
 * arrastrando el asa de la derecha; TalkBack ofrece «subir» y «bajar» en el asa.
 *
 * El orden se lleva en local mientras dura el arrastre y se guarda al soltar: escribir en cada
 * cruce de fila haría saltar la lista con cada respuesta de la base de datos.
 */
@Composable
private fun StackOrder(apps: List<AppEntry>, onReorder: (List<AppEntry>) -> Unit, onRemove: (AppEntry) -> Unit) {
    var order by remember(apps) { mutableStateOf(apps) }
    var dragging by remember { mutableStateOf<AppEntry?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    // Alto real de una fila: con la letra del sistema grande crece, y el arrastre cuenta en filas.
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    val moveUp = stringResource(R.string.stack_move_up)
    val moveDown = stringResource(R.string.stack_move_down)

    fun move(app: AppEntry, by: Int): Boolean {
        val index = order.indexOf(app)
        val target = index + by
        if (index < 0 || target !in order.indices) return false
        order = order.moved(index, target)
        return true
    }

    Column {
        order.forEach { app ->
            key(app.key.toString()) {
                val isDragging = dragging === app
                val handle = stringResource(R.string.stack_drag, app.label)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = StackOrderRowMinHeight)
                            .onSizeChanged { rowHeightPx = it.height.toFloat() }
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer { translationY = if (isDragging) offset else 0f }
                            .background(
                                // Opaca mientras se arrastra: pasa por encima de las otras filas.
                                if (isDragging) {
                                    AlisioTokens.Fill.compositeOver(
                                        AlisioTokens.Surface
                                    )
                                } else {
                                    Color.Transparent
                                },
                                AlisioTokens.RowShape
                            )
                            .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(app.icon, AlisioTokens.IconSlot)
                    Spacer(Modifier.width(AlisioTokens.IconGap))
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = AlisioTokens.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    RoundIconButton(
                        R.drawable.ic_close,
                        stringResource(R.string.stack_remove_app, app.label),
                        AlisioTokens.TextSecondary,
                        onClick = { onRemove(app) }
                    )
                    // Con una sola app no hay orden que cambiar.
                    if (order.size > 1) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .semantics {
                                    contentDescription = handle
                                    customActions =
                                        listOf(
                                            CustomAccessibilityAction(moveUp) {
                                                move(app, -1).also { if (it) onReorder(order) }
                                            },
                                            CustomAccessibilityAction(moveDown) {
                                                move(app, 1).also { if (it) onReorder(order) }
                                            }
                                        )
                                }
                                .pointerInput(app.key) {
                                    detectDragGestures(
                                        onDragStart = {
                                            dragging = app
                                            offset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            offset += amount.y
                                            // Al pasar de media fila, la app cambia de puesto y el arrastre sigue desde ahí.
                                            val step =
                                                when {
                                                    rowHeightPx <= 0f -> 0
                                                    offset > rowHeightPx / 2 -> 1
                                                    offset < -rowHeightPx / 2 -> -1
                                                    else -> 0
                                                }
                                            if (step != 0 && move(app, step)) offset -= step * rowHeightPx
                                        },
                                        onDragEnd = {
                                            dragging = null
                                            offset = 0f
                                            onReorder(order)
                                        },
                                        onDragCancel = {
                                            dragging = null
                                            offset = 0f
                                            order = apps
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_drag_indicator),
                                contentDescription = null,
                                tint = AlisioTokens.TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Placa de Ahora: una sola sugerencia con su motivo. Tocarla abre la app; el aspa la descarta seis
 * horas, y al tercer descarte no vuelve a salir. No lleva la palabra «Ahora» a la vista: va en lo
 * que lee TalkBack.
 */
@Composable
internal fun NowCard(
    suggestion: NowSuggestion,
    app: AppEntry,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = AlisioTokens.CardShape
    val reason =
        when (suggestion.rule) {
            NowRule.NEW_VERSION -> {
                // La hora compartida avanza cada minuto: el «hace cuánto» no se queda parado.
                val nowMillis = rememberNow().toInstant().toEpochMilli()
                val minutes = ((nowMillis - suggestion.since) / 60_000L).coerceAtLeast(0)
                if (minutes < 60) {
                    stringResource(R.string.now_reason_new_version_minutes, minutes.toInt())
                } else {
                    stringResource(R.string.now_reason_new_version_hours, (minutes / 60).toInt())
                }
            }

            NowRule.BLUETOOTH -> stringResource(R.string.now_reason_bluetooth, suggestion.device.orEmpty())

            NowRule.HABIT -> stringResource(R.string.now_reason_habit)
        }
    val spoken = stringResource(R.string.now_description, app.label, reason)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .clip(shape)
                .background(AlisioTokens.Plate, shape)
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.icon, AlisioTokens.IconSlot)
        Spacer(Modifier.width(AlisioTokens.IconGap))
        Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = spoken }) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                color = AlisioTokens.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = AlisioTokens.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RoundIconButton(
            R.drawable.ic_close,
            stringResource(R.string.now_dismiss),
            AlisioTokens.TextSecondary,
            onDismiss,
            iconSize = 20.dp
        )
    }
}
