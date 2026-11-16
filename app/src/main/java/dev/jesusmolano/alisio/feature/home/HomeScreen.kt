package dev.jesusmolano.alisio.feature.home

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.TouchTrace
import dev.jesusmolano.alisio.core.dock.DOCK_ITEM_SPARE_DP
import dev.jesusmolano.alisio.core.dock.dockIconSize
import dev.jesusmolano.alisio.core.draw.InitialPick
import dev.jesusmolano.alisio.core.draw.StrokePoint
import dev.jesusmolano.alisio.core.draw.pickByInitial
import dev.jesusmolano.alisio.core.draw.recognizeLetter
import dev.jesusmolano.alisio.core.gesture.Candidate
import dev.jesusmolano.alisio.core.gesture.GestureOptions
import dev.jesusmolano.alisio.core.gesture.GestureThresholds
import dev.jesusmolano.alisio.core.gesture.Haptic
import dev.jesusmolano.alisio.core.gesture.HomeGestureArbiter
import dev.jesusmolano.alisio.core.gesture.StartArea
import dev.jesusmolano.alisio.core.notify.packagesWithDot
import dev.jesusmolano.alisio.core.now.HABIT_DAYS
import dev.jesusmolano.alisio.core.now.NowRule
import dev.jesusmolano.alisio.core.now.NowSuggestion
import dev.jesusmolano.alisio.core.search.filterByName
import dev.jesusmolano.alisio.core.search.moved
import dev.jesusmolano.alisio.core.text.takeGraphemes
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.ShortcutEntry
import dev.jesusmolano.alisio.data.apps.toSections
import dev.jesusmolano.alisio.data.contacts.Person
import dev.jesusmolano.alisio.data.db.HomeNote
import dev.jesusmolano.alisio.data.glance.GlanceData
import dev.jesusmolano.alisio.data.home.StackIcon
import dev.jesusmolano.alisio.data.home.SwipeTarget
import dev.jesusmolano.alisio.data.media.LiveNow
import dev.jesusmolano.alisio.data.settings.Prefs
import dev.jesusmolano.alisio.feature.appmenu.AppMenuSheet
import dev.jesusmolano.alisio.feature.appmenu.AppUsage
import dev.jesusmolano.alisio.feature.appmenu.DockPlacement
import dev.jesusmolano.alisio.feature.appmenu.SlotActions
import dev.jesusmolano.alisio.feature.people.contact
import dev.jesusmolano.alisio.feature.people.openContact
import dev.jesusmolano.alisio.feature.search.appLabel
import dev.jesusmolano.alisio.feature.search.searchHint
import dev.jesusmolano.alisio.feature.zones.ZonesViewModel
import dev.jesusmolano.alisio.feature.zones.zoneMove
import dev.jesusmolano.alisio.ui.components.AlisioSheet
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.components.AppPicker
import dev.jesusmolano.alisio.ui.components.ClockText
import dev.jesusmolano.alisio.ui.components.DockIcon
import dev.jesusmolano.alisio.ui.components.DrawTrail
import dev.jesusmolano.alisio.ui.components.FolderTileIcon
import dev.jesusmolano.alisio.ui.components.IconLabel
import dev.jesusmolano.alisio.ui.components.ImageTarget
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.LocalPickImage
import dev.jesusmolano.alisio.ui.components.LocalUndo
import dev.jesusmolano.alisio.ui.components.Note
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
import dev.jesusmolano.alisio.ui.components.liveLookFor
import dev.jesusmolano.alisio.ui.components.rememberLightWallpaper
import dev.jesusmolano.alisio.ui.components.rememberNow
import dev.jesusmolano.alisio.ui.components.wallpaperVeil
import dev.jesusmolano.alisio.ui.theme.AlisioHaptics
import dev.jesusmolano.alisio.ui.theme.AlisioMotion
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import dev.jesusmolano.alisio.ui.theme.LocalHomePlate
import dev.jesusmolano.alisio.ui.theme.onWallpaper
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.launch

private val DockIconSize = AlisioTokens.DockIconSize

// Ancho de un hueco del dock: el icono y un poco de aire a cada lado para que el nombre respire.
internal val DockItemWidth = DockIconSize + DOCK_ITEM_SPARE_DP.dp

// Lo que la hora, la fecha y su línea dejan libre a la derecha, además del filo: el ancho que toca
// el índice de Trazo, centrado a 24 dp del borde.
private val RailClearance = 28.dp
private val PillMinHeight = 56.dp
private val StackOrderRowMinHeight = 56.dp
private val DockHeight = 86.dp

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
@OptIn(ExperimentalLayoutApi::class)
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
    val liveNow by viewModel.live.collectAsStateWithLifecycle()
    val live = liveNow?.takeIf { prefs.nowEnabled && prefs.liveNow }
    // La sugerencia cuyo porqué se está leyendo, con la opción de olvidarla.
    var explaining by remember { mutableStateOf<Pair<NowSuggestion, AppEntry>?>(null) }
    val nowPlaying by viewModel.player.collectAsStateWithLifecycle()
    val player = nowPlaying?.takeIf { prefs.playerEnabled }
    val unread by viewModel.unread.collectAsStateWithLifecycle()
    val dotted =
        remember(unread, prefs.unreadDots, prefs.unreadMuted) {
            if (prefs.unreadDots) packagesWithDot(unread, prefs.unreadMuted) else emptySet()
        }
    val context = LocalContext.current
    val resources = LocalResources.current
    // Ignorando si se ven: con «Ocultar la barra de estado» (MainActivity) el margen de arriba valdría
    // 0, la hora subiría hasta la cámara y todo el Inicio saltaría unos 40 dp cada vez que la barra
    // asoma al deslizar desde arriba.
    val bars = WindowInsets.systemBarsIgnoringVisibility.asPaddingValues()
    val density = LocalDensity.current
    val undo = LocalUndo.current
    val lightWallpaper = rememberLightWallpaper()
    // Alto real del pie (Ahora, reproductor, dock y píldora): el índice de Trazo acaba donde empieza.
    // Se mide en vez de sumar constantes porque el pie crece con la letra del sistema.
    var footerHeight by remember { mutableStateOf(0.dp) }
    // Con algo encima (Zonas, los ajustes, un trazo) el contenido del Inicio se retira: los velos de
    // esas pantallas dejan ver el fondo de pantalla, y el reloj y el dock asomarían detrás.
    var tracing by remember { mutableStateOf(false) }
    val searchOpenNow by rememberUpdatedState(searchOpen)
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
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val timers by viewModel.timers.collectAsStateWithLifecycle()
    val photos = viewModel.photos.takeIf { prefs.contactPhotos }
    val liveApps = rememberLiveApps(apps, prefs.liveIcons)
    val liveLook = liveLookFor(prefs.iconStyle)
    // La persona favorita cuyos canales se enseñan, al mantener su cara.
    var channelsOf by remember { mutableStateOf<Person?>(null) }
    // Dibujar una inicial: el rastro del dedo y lo que hay que elegir.
    val trail = remember { mutableStateListOf<Offset>() }
    var initialChoice by remember { mutableStateOf<InitialChoice?>(null) }
    // Dónde acaba la hora con su línea y sus notas: por encima de ahí no se dibuja.
    var clockBottom by remember { mutableFloatStateOf(0f) }
    var homeSize by remember { mutableStateOf(IntSize.Zero) }
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(homePresses) {
        editing = null
        openStack = null
        appMenu = null
        channelsOf = null
        initialChoice = null
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

    // Reanudar la música puede tardar hasta 1,5 s sin nada que ver: la vibración dice que el toque
    // ha llegado. La app solo se abre después si el Inicio sigue delante.
    fun playMusic(app: AppEntry) {
        AlisioHaptics.done(view)
        viewModel.playMusic(app, stillShown = { lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }) {
            launchFailed(app.label)
        }
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

    /**
     * Contacta con una persona por su canal principal. Al dibujar su inicial ([direct] `false`) una
     * llamada se queda en el marcador, como con Enter: un trazo mal leído no debe llamar a nadie.
     */
    fun contactPerson(person: Person, direct: Boolean) {
        val channel = viewModel.channelsOf(person).firstOrNull()
        if (channel != null && context.contact(person, channel, direct)) {
            viewModel.recordContact(person, channel)
        } else {
            launchFailed(person.name)
        }
    }

    fun openInitial(item: InitialItem) {
        when (item) {
            is InitialItem.App -> launch(item.app)
            is InitialItem.Contact -> contactPerson(item.person, direct = false)
        }
    }

    // Lo que hace un trazo al soltarlo: abre lo más usado con esa inicial o deja elegir si empatan.
    fun onStroke(points: List<Offset>) {
        val minSize = with(density) { MinLetterSize.toPx() }
        val letter = recognizeLetter(points.map { StrokePoint(it.x, it.y) }, minSize)
        // En el registro de gestos: el emulador no vibra y así se sabe qué letra se ha leído.
        Log.d(GESTURE_TAG, "letra leída: ${letter ?: "ninguna"}")
        if (letter == null) {
            // Un trazo que no es ninguna letra no hace nada, pero lo dice al dedo.
            AlisioHaptics.limit(view)
            return
        }
        val center = Offset(points.sumOf { it.x.toDouble() }.toFloat() / points.size, points.minOf { it.y })
        val items =
            apps.orEmpty().filterNot { it.hidden }.map(InitialItem::App) + people.map(InitialItem::Contact)
        when (val pick = pickByInitial(letter, items, { it.key }, { it.label }, viewModel.usageNow())) {
            null -> {
                AlisioHaptics.limit(view)
                initialChoice = InitialChoice(letter, emptyList(), center)
            }

            is InitialPick.Open -> {
                AlisioHaptics.done(view)
                openInitial(pick.item)
            }

            is InitialPick.Choose -> {
                AlisioHaptics.step(view)
                initialChoice = InitialChoice(letter, pick.items, center)
            }
        }
    }

    // Lo que hace cada gesto al ganar. El detector vive lo que vive la pantalla: lee siempre la orden
    // de ahora.
    val fire by rememberUpdatedState<(Candidate, List<Offset>) -> Unit> { candidate, stroke ->
        when (candidate) {
            Candidate.SEARCH -> onOpenSearch()
            Candidate.NOTIFICATIONS -> showNotifications?.invoke()
            Candidate.LETTER -> onStroke(stroke)
            Candidate.SETTINGS -> onOpenSettings()
            Candidate.LOCK -> lockScreen?.invoke()
            Candidate.ZONES -> onOpenZones()
        }
    }
    val gestureOptions =
        GestureOptions(
            drawInitial = prefs.drawInitial,
            notifications = onShowNotifications != null,
            lock = canLock,
            pinch = prefs.zonesPinch
        )
    val viewConfiguration = LocalViewConfiguration.current
    val gestureThresholds =
        GestureThresholds.of(
            density = density.density,
            touchSlop = viewConfiguration.touchSlop,
            longPressMillis = viewConfiguration.longPressTimeoutMillis,
            doubleTapMillis = viewConfiguration.doubleTapTimeoutMillis
        )
    val arbiter =
        remember {
            HomeGestureArbiter(gestureOptions, gestureThresholds) { points ->
                val minSize = with(density) { MinLetterSize.toPx() }
                recognizeLetter(points.map { StrokePoint(it.x, it.y) }, minSize) != null
            }
        }
    // Las opciones se leen al bajar el dedo: cambiarlas aquí no corta un gesto en curso.
    SideEffect {
        arbiter.options = gestureOptions
        arbiter.thresholds = gestureThresholds
    }
    val gestureScope = rememberCoroutineScope()
    val feedback = remember { GestureFeedback(gestureScope) }
    val fingers = remember { HomeFingers() }
    val systemGestures = WindowInsets.systemGestures
    // La franja de arriba es del sistema también con la barra oculta: deslizar ahí la hace asomar.
    val statusBars = WindowInsets.statusBarsIgnoringVisibility

    Box(
        modifier
            .fillMaxSize()
            .wallpaperVeil(prefs.wallpaperDim.factor, lightWallpaper)
            .onSizeChanged { homeSize = it }
            // Buscar, notificaciones, la inicial, mantener, dos toques y pellizcar comparten el
            // centro: un solo árbitro decide por intención cuál gana (docs/PLAN.md §11, «Gestos sin
            // pisarse»). Trazo, el dock y el reproductor se quedan antes sus propios gestos, salvo el
            // pellizco: con dos dedos el Inicio se queda el evento antes que ellos. Las opciones del
            // árbitro ya son las de ahora (SideEffect), y el detector guarda la primera lambda.
            .homeFingers(fingers) { arbiter.options.pinch && !searchOpenNow }
            .homeGestures(
                arbiter = arbiter,
                feedback = feedback,
                fingers = fingers,
                trail = trail,
                // Lee estados, no valores de esta composición: el detector guarda la primera lambda.
                paused = { searchOpenNow || tracing || initialChoice != null },
                areaOf = { at ->
                    val top = statusBars.getTop(density)
                    val bottom = homeSize.height - systemGestures.getBottom(density)
                    val left = systemGestures.getLeft(density, LayoutDirection.Ltr) + with(density) { 8.dp.toPx() }
                    val footerTop = homeSize.height - with(density) { footerHeight.toPx() }
                    val railStart = homeSize.width - with(density) { (AlisioTokens.Edge + RailClearance * 2).toPx() }
                    when {
                        at.y < top || at.y > bottom -> StartArea.SYSTEM

                        at.x < left -> StartArea.LEFT_EDGE

                        // La inicial solo en el hueco libre entre la hora y el pie, lejos del índice.
                        at.y > clockBottom && at.y < footerTop && at.x < railStart -> StartArea.DRAW

                        else -> StartArea.FREE
                    }
                },
                onHaptic = {
                    when (it) {
                        Haptic.COMMIT -> AlisioHaptics.threshold(view, true)
                        Haptic.RELEASE -> AlisioHaptics.threshold(view, false)
                        Haptic.NONE -> Unit
                    }
                },
                onFire = { candidate, stroke -> fire(candidate, stroke) }
            )
    ) {
        val glanceData by viewModel.glance.collectAsStateWithLifecycle()
        val musicApp by viewModel.musicApp.collectAsStateWithLifecycle()
        val musicPieceShown by viewModel.musicPieceShown.collectAsStateWithLifecycle()
        // Recordada: una pieza nueva en cada recomposición impediría saltarse ClockAndDate.
        val musicGlance =
            remember(musicApp) { musicApp?.let { app -> MusicGlance(app.label) { playMusic(app) } } }
        ClockAndDate(
            prefs = prefs,
            glance = glanceData,
            music = musicGlance?.takeIf { musicPieceShown },
            notes = if (prefs.homeNotes) notes else emptyList(),
            onStrikeNote = { note ->
                undo.show(resources.getString(R.string.undo_note_struck), viewModel.removeNote(note))
            },
            onOpenLink = { if (!context.openNoteLink(it)) launchFailed(it) },
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .onGloballyPositioned { clockBottom = it.boundsInParent().bottom }
                    .graphicsLayer {
                        alpha = contentAlpha * feedback.homeAlpha()
                        // La hora baja un poco con la persiana: soltar ya baja las notificaciones.
                        translationY = feedback.of(Candidate.NOTIFICATIONS) * AlisioMotion.Rise.toPx()
                    }
                    .padding(
                        top = bars.calculateTopPadding() + 40.dp,
                        start = AlisioTokens.Edge,
                        // La línea bajo la hora no llega al índice de Trazo: sus toques serían de él.
                        end = AlisioTokens.Edge + RailClearance
                    )
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = contentAlpha * feedback.homeAlpha() }
                .onSizeChanged { footerHeight = with(density) { it.height.toDp() } }
                .padding(bottom = bars.calculateBottomPadding() + 16.dp)
        ) {
            // Lo que está en curso va antes que la sugerencia: es lo que importa en este momento.
            val liveApp = live?.let { current ->
                apps.orEmpty().firstOrNull {
                    it.component.packageName ==
                        current.packageName
                }
            }
            if (live != null) {
                val label = liveApp?.label ?: live.packageName
                LiveCard(
                    live = live,
                    appLabel = label,
                    icon = liveApp?.icon,
                    onOpen = {
                        val opened =
                            viewModel.openLive(live, label, liveApp?.icon) {
                                context.sendLive(live) || (liveApp != null && viewModel.launch(liveApp))
                            }
                        if (!opened) launchFailed(label)
                    },
                    modifier = Modifier.padding(horizontal = AlisioTokens.Outset)
                )
                Spacer(Modifier.height(AlisioTokens.PlateGap))
            } else {
                now?.let { (suggestion, app) ->
                    val musicArt by viewModel.musicArt.collectAsStateWithLifecycle()
                    val resumesMusic = suggestion.rule == NowRule.MUSIC
                    NowCard(
                        suggestion = suggestion,
                        app = app,
                        // Solo la carátula de esa app: la de otra diría que se reanuda otra cosa.
                        art = musicArt[app.component.packageName]?.takeIf { resumesMusic },
                        // «Reanudar» pone la música sin abrir la app; si no suena, la abre.
                        onOpen = { if (resumesMusic) playMusic(app) else launch(app) },
                        onDismiss = {
                            val dismissal = viewModel.dismissNow(suggestion)
                            val message =
                                resources.getString(
                                    if (dismissal.silenced) R.string.undo_now_silenced else R.string.undo_now_hidden
                                )
                            undo.show(message, dismissal.undo)
                        },
                        onExplain = if (suggestion.rule.learned) ({ explaining = suggestion to app }) else null,
                        modifier = Modifier.padding(horizontal = AlisioTokens.Outset)
                    )
                    Spacer(Modifier.height(AlisioTokens.PlateGap))
                }
            }
            // DISENO §2: el reproductor entra subiendo 12 dp y sale desvaneciéndose. Mientras sale se
            // sigue pintando el último que hubo.
            var lastPlayer by remember { mutableStateOf(player) }
            // Apartada con el dedo, la placa ya se ha ido con él: desvanecerla la haría volver al
            // centro (PlayerBar devuelve su desplazamiento a 0) antes de irse.
            var swipedAway by remember { mutableStateOf(false) }
            if (player != null) {
                lastPlayer = player
                swipedAway = false
            }
            AnimatedVisibility(
                visible = player != null,
                enter = fadeIn(AlisioMotion.standard()) +
                    slideInVertically(AlisioMotion.standard()) { with(density) { AlisioMotion.Rise.roundToPx() } },
                exit = if (swipedAway) ExitTransition.None else fadeOut(AlisioMotion.exit())
            ) {
                val playing = lastPlayer ?: return@AnimatedVisibility
                val playingApp = apps.orEmpty().firstOrNull { it.component.packageName == playing.packageName }
                val leaving = player == null
                // Mientras se desvanece, la placa ya no es de ninguna sesión: no atiende toques.
                Column(
                    if (leaving) {
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach {
                                        it.consume()
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                ) {
                    PlayerBar(
                        player = playing,
                        appIcon = playingApp?.icon,
                        // Por la lista de apps siempre que se pueda: así la apertura cuenta en el historial.
                        onOpen = { if (playingApp != null) launch(playingApp) else viewModel.openPlayerApp() },
                        onToggle = viewModel::togglePlayback,
                        onNext = viewModel::skipToNext,
                        onHide = {
                            swipedAway = true
                            viewModel.hidePlayer()
                        },
                        modifier = Modifier.padding(horizontal = AlisioTokens.Outset)
                    )
                    Spacer(Modifier.height(AlisioTokens.PlateGap))
                }
            }
            // Con más huecos los iconos encogen para caber de filo a filo; sin huecos no hay dock.
            val iconSize = rememberDockIconSize(dock.size)
            if (dock.isNotEmpty()) {
                DockRow {
                    dock.forEachIndexed { position, slot ->
                        val itemModifier = Modifier.width(iconSize + DOCK_ITEM_SPARE_DP.dp)
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
                                    iconSize = iconSize,
                                    modifier = itemModifier
                                ) { VectorTileIcon(R.drawable.ic_add, size = iconSize) }

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
                                    iconSize = iconSize,
                                    modifier = itemModifier
                                ) {
                                    if (prefs.liveIcons) {
                                        LiveAppIcon(slot.app, iconSize, liveApps, liveLook, nowPlaying, timers)
                                    } else {
                                        AppIcon(slot.app.icon, iconSize)
                                    }
                                }
                            }

                            is DockSlot.Stack ->
                                DockItem(
                                    label = slot.name,
                                    description = null,
                                    onClick = { if (slot.isEmpty) editing = position else openStack = position },
                                    onLongClick = { editing = position },
                                    showLabel = showLabel,
                                    unread = slot.apps.any { it.component.packageName in dotted },
                                    iconSize = iconSize,
                                    modifier = itemModifier
                                ) { StackSlotIcon(slot, iconSize) }
                        }
                    }
                }
                Spacer(Modifier.height(AlisioTokens.PlateGap))
            }
            // También depende de las apps instaladas: instalar o quitar el asistente cambia el texto.
            val assistant = remember(prefs.assistantPackage, apps) { context.appLabel(prefs.assistantPackage) }
            SearchPill(
                searchHint(assistant),
                onOpenSearch,
                Modifier
                    .padding(horizontal = AlisioTokens.Outset)
                    // La píldora sube con el dedo hasta el umbral: soltar ya abre la barra.
                    .graphicsLayer { translationY = -feedback.of(Candidate.SEARCH) * AlisioMotion.Rise.toPx() }
            )
        }

        // Una app oculta no sale en Trazo: solo se llega a ella buscando su nombre.
        val sections = remember(apps) { apps.orEmpty().filterNot { it.hidden }.toSections { it.label } }
        // Al abrir la barra el índice se desvanece con el resto del Inicio en vez de quitarse de golpe,
        // y sale de la composición al acabar. Mientras tanto los toques son de la barra, que va encima.
        val traceAlpha by animateFloatAsState(
            targetValue = if (searchOpen) 0f else 1f,
            animationSpec = AlisioMotion.standard(),
            label = "trace-rail"
        )
        if (!searchOpen || traceAlpha > 0f) {
            Box(Modifier.matchParentSize().graphicsLayer { alpha = traceAlpha }) {
                Trace(
                    sections = sections,
                    mode = prefs.traceRail,
                    railTop = bars.calculateTopPadding() + 16.dp,
                    // El índice en reposo acaba donde empieza el pie; durante el trazo baja sobre él.
                    railBottom = footerHeight + AlisioTokens.PlateGap,
                    onLaunch = ::launch,
                    onOpenZones = onOpenZones,
                    pressedFingers = { fingers.pressed },
                    onTracing = {
                        if (it) TouchTrace.mark("trazo")
                        tracing = it
                    },
                    leaving = searchOpen
                )
            }
        }
        // La persiana propia: la de verdad es del sistema y no se puede enseñar a medias.
        NotificationsShade(progress = { feedback.of(Candidate.NOTIFICATIONS) })
        // El rastro se ve según lo probable que sea letra: un deslizar recto no pinta nada.
        DrawTrail(trail, Modifier.graphicsLayer { alpha = feedback.trail })
        InitialChooser(
            choice = initialChoice,
            photos = photos,
            onPick = {
                initialChoice = null
                AlisioHaptics.done(view)
                openInitial(it)
            },
            onDismiss = { initialChoice = null }
        )
    }

    channelsOf?.let { person ->
        FavoriteChannelsSheet(
            person = person,
            channels = remember(person) { viewModel.channelsOf(person) },
            onContact = { channel ->
                channelsOf = null
                if (context.contact(
                        person,
                        channel
                    )
                ) {
                    viewModel.recordContact(person, channel)
                } else {
                    launchFailed(person.name)
                }
            },
            onOpenContact = {
                channelsOf = null
                if (!context.openContact(person)) launchFailed(person.name)
            },
            onDismiss = { channelsOf = null }
        )
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
                onLaunchShortcut = {
                    openStack = null
                    if (!viewModel.launch(it)) launchFailed(it.label)
                },
                shortcutIcon = viewModel::shortcutIcon,
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

    explaining?.let { (suggestion, app) ->
        NowWhySheet(
            suggestion = suggestion,
            app = app,
            onForget = {
                explaining = null
                undo.show(resources.getString(R.string.undo_now_forgotten, app.label), viewModel.forgetNow(suggestion))
            },
            onDismiss = { explaining = null }
        )
    }

    val defaultStackName = stringResource(R.string.stack_default_name)
    val look = rememberAppLook(viewModel)
    val pickImage = LocalPickImage.current
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
                look = look,
                onOpenShortcut = { shortcut ->
                    appMenu = null
                    launch(SwipeTarget.Shortcut(shortcut))
                },
                // La hoja se cierra: abierta taparía la barra de deshacer, que va en la ventana de abajo.
                onUnpin = { shortcut ->
                    appMenu = null
                    val restore = viewModel.unpinShortcut(shortcut)
                    undo.show(resources.getString(R.string.undo_unpinned, shortcut.label), restore)
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
            // La actividad pone la imagen al volver del selector, con la hoja de la pila aún abierta.
            onPickStackImage = { pickImage(ImageTarget.Stack(position)) {} },
            onReorderStack = { viewModel.reorderStack(position, it) },
            onRemoveShortcut = { viewModel.removeFromStack(position, it) },
            onClear = {
                clearSlot(position)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

/**
 * Arriba del Inicio: la hora con el formato y el tamaño elegidos, la fecha y, debajo, la línea con
 * lo que tenga algo que decir ([glancePieces]). Las tres partes abren algo al tocarlas.
 */
@Composable
private fun ClockAndDate(
    prefs: Prefs,
    glance: GlanceData,
    music: MusicGlance?,
    notes: List<HomeNote>,
    onStrikeNote: (HomeNote) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val now = rememberNow()
    val time = formatClock(now, prefs.clockFormat)
    val date = formatHomeDate(now)
    val noRipple = remember { MutableInteractionSource() }
    val clock = AlisioTokens.Clock.scaled(AlisioTokens.clockScale(prefs.clockSize)).onWallpaper()
    val pieces = glancePieces(prefs, glance, now.toInstant().toEpochMilli(), music = music)
    Column(modifier) {
        ClockText(
            text = time,
            style = clock,
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
        val openCalendar = stringResource(R.string.home_open_calendar)
        val dateText: @Composable (Modifier) -> Unit = { layout ->
            Text(
                text = date,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium).onWallpaper(),
                modifier =
                    Modifier
                        .clickable(
                            interactionSource = noRipple,
                            indication = null,
                            onClickLabel = openCalendar,
                            role = Role.Button
                        ) {
                            context.open(
                                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
                            )
                        }
                        // Renglón junto, por debajo del toque mínimo a elección del dueño (AlisioTokens.InfoRow).
                        .heightIn(min = AlisioTokens.InfoRow)
                        .wrapContentHeight()
                        .then(layout)
            )
        }
        // Sin aire delante: su tinta cae en el filo, como la de la hora.
        dateText(Modifier.padding(top = 4.dp, bottom = if (pieces.isEmpty()) 8.dp else 0.dp, end = 12.dp))
        // Las piezas van en su renglón bajo la fecha, más pequeñas: datos de un vistazo, no una lista.
        if (pieces.isNotEmpty()) GlanceLine(pieces, Modifier.padding(bottom = 4.dp))
        // Las notas siguen a la línea, con su misma letra: son lo que el dueño se ha dejado dicho.
        if (notes.isNotEmpty()) NotesLine(notes, onStrikeNote, onOpenLink)
    }
}

/** La fecha del Inicio («Lunes, 16 de noviembre»), igual en su miniatura. */
@Composable
internal fun formatHomeDate(now: ZonedDateTime): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(now.toLocalDate(), locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        now.format(DateTimeFormatter.ofPattern(pattern, locale)).replaceFirstChar { it.titlecase(locale) }
    }
}

/** La hora a una fracción de su tamaño: el interlineado va con ella para no dejar aire de más. */
internal fun TextStyle.scaled(scale: Float): TextStyle =
    if (scale == 1f) this else copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)

/**
 * Abre la app de Reloj. `ACTION_SHOW_ALARMS` exige el permiso `SET_ALARM` (en One UI falla sin él),
 * así que solo se usa para saber qué app es el reloj y se abre por su icono de inicio.
 */
internal fun Context.openClockApp() {
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

/**
 * El lado de los iconos del dock con [count] huecos ([dockIconSize]). Se mide con el ancho real de
 * la pantalla, no con el de quien dibuja: la miniatura de los ajustes reduce la densidad y debe
 * salir con el mismo tamaño relativo que el Inicio.
 */
@Composable
internal fun rememberDockIconSize(count: Int): Dp {
    // Las métricas de la pantalla, no `LocalDensity`: la miniatura la cambia y el ancho saldría mayor.
    val metrics = LocalResources.current.displayMetrics
    val width = metrics.widthPixels / metrics.density
    return remember(count, width) { dockIconSize(count, width).dp }
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
    iconSize: Dp = DockIconSize,
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
        size = iconSize,
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
                        // el árbitro del Inicio lo da por perdido. Solo se lo queda hacia arriba: hacia
                        // abajo, o de lado, sigue siendo del Inicio (bajar las notificaciones).
                        Modifier.pointerInput(Unit) {
                            val openAt = -SwipeOpenDrag.toPx()
                            val maxLift = SwipeMaxLift.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var claimed = false
                                // Hasta salir de la holgura no se decide; si no sale hacia arriba, no es suyo.
                                while (!claimed) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed || change.isConsumed) return@awaitEachGesture
                                    val moved = change.position - down.position
                                    if (moved.getDistance() <= viewConfiguration.touchSlop) continue
                                    if (moved.y >= 0f || abs(moved.x) > abs(moved.y)) return@awaitEachGesture
                                    claimed = true
                                    change.consume()
                                }
                                var dragged = 0f
                                var armed = false
                                var released = false
                                val start = down.position.y
                                try {
                                    while (true) {
                                        val change =
                                            awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                        // Llega ya consumido si el sistema cancela el gesto o si un segundo
                                        // dedo lo ha vuelto pellizco (homeFingers): no se abre nada.
                                        if (change.isConsumed) break
                                        if (!change.pressed) {
                                            released = true
                                            change.consume()
                                            break
                                        }
                                        change.consume()
                                        dragged = change.position.y - start
                                        // Una sola vibración por sentido: al cruzar el umbral (soltar ya
                                        // abre el destino) y al volver atrás (soltar ya no hace nada).
                                        val nowArmed = dragged <= openAt
                                        if (nowArmed != armed) AlisioHaptics.threshold(view, nowArmed)
                                        armed = nowArmed
                                        // El icono acompaña al dedo a medio paso y con tope: se nota que
                                        // responde sin que parezca que se puede arrastrar a otro sitio.
                                        val target = (dragged * SWIPE_LIFT_RATIO).coerceIn(-maxLift, 0f)
                                        scope.launch { lift.snapTo(target) }
                                    }
                                } finally {
                                    scope.launch { lift.animateTo(0f, AlisioMotion.follow()) }
                                }
                                // Ya vibró al armarse: abrir no vuelve a vibrar.
                                if (released && armed) currentSwipe?.open?.invoke()
                            }
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
        stack.icon == StackIcon.IMAGE && stack.image != null -> AppIcon(stack.image, size)

        // La imagen elegida ya no está en el disco: el dibujo de imagen dice qué falta por elegir.
        stack.icon == StackIcon.IMAGE -> VectorTileIcon(R.drawable.ic_image, size = size)

        stack.icon == StackIcon.SYMBOL && stack.glyph.isNotBlank() -> SymbolTileIcon(stack.glyph, size = size)

        stack.icon == StackIcon.SYMBOL || stack.isEmpty -> VectorTileIcon(R.drawable.ic_stacks, size = size)

        // Un acceso fijado se ve con el icono de su app, como en la barra.
        stack.icon == StackIcon.FIRST_APP -> AppIcon(
            (stack.apps.firstOrNull() ?: stack.shortcuts.first().app).icon,
            size
        )

        else -> FolderTileIcon(stack.apps.map { it.icon } + stack.shortcuts.map { it.app.icon }, size = size)
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
            .background(LocalHomePlate.current.fill, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = LocalHomePlate.current.secondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalHomePlate.current.secondary
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
    onLaunchShortcut: (ShortcutEntry) -> Unit,
    shortcutIcon: suspend (ShortcutEntry) -> Bitmap?,
    onMenu: (AppEntry) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    val menuLabel = stringResource(R.string.dock_app_menu)
    // Las apps y después los accesos fijados desde otras apps, en la misma rejilla.
    val cells: List<Any> = stack.apps + stack.shortcuts
    AlisioSheet(onDismiss = onDismiss, expanded = false) {
        SheetTitle(stack.name)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            cells.chunked(STACK_COLUMNS).forEach { row ->
                // De filo a filo, como el dock: el primer icono cae bajo el título.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AlisioTokens.Edge - (StackCellWidth - DockIconSize) / 2),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    row.forEach { cell ->
                        val app = cell as? AppEntry
                        val shortcut = cell as? ShortcutEntry
                        Column(
                            Modifier
                                .width(StackCellWidth)
                                .clip(AlisioTokens.RowShape)
                                .combinedClickable(
                                    role = Role.Button,
                                    onLongClickLabel = if (app != null) menuLabel else null,
                                    onLongClick = app?.let { { onMenu(it) } },
                                    onClick = {
                                        if (app !=
                                            null
                                        ) {
                                            onLaunch(app)
                                        } else if (shortcut != null) {
                                            onLaunchShortcut(shortcut)
                                        }
                                    }
                                )
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (app != null) {
                                AppIcon(app.icon, DockIconSize)
                                Spacer(Modifier.height(6.dp))
                                IconLabel(app.label)
                            } else if (shortcut != null) {
                                // Su icono propio, si se deja leer; mientras tanto, el de su app.
                                val icon by produceState(shortcut.app.icon, shortcut.key) {
                                    shortcutIcon(shortcut)?.let { value = it }
                                }
                                AppIcon(icon, DockIconSize)
                                Spacer(Modifier.height(6.dp))
                                IconLabel(shortcut.label)
                            }
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
    onPickStackImage: () -> Unit,
    onReorderStack: (List<AppEntry>) -> Unit,
    onRemoveShortcut: (ShortcutEntry) -> Unit,
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
            StackEditor(
                stack,
                apps,
                onToggleInStack,
                onRenameStack,
                onSetStackIcon,
                onPickStackImage,
                onReorderStack,
                onRemoveShortcut,
                onClear
            )
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
    onPickImage: () -> Unit,
    onReorderStack: (List<AppEntry>) -> Unit,
    onRemoveShortcut: (ShortcutEntry) -> Unit,
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
                            StackIcon.IMAGE -> R.string.icon_image
                        }
                    ),
                selected = stack.icon == mode,
                onClick = {
                    when {
                        // Sin imagen, o ya elegida (para cambiarla), se abre el selector de fotos; si la pila ya
                        // tuvo una, vuelve sin pedirla otra vez.
                        mode == StackIcon.IMAGE && (stack.image == null || stack.icon == StackIcon.IMAGE) ->
                            onPickImage()

                        else -> onSetStackIcon(mode, glyph)
                    }
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
                    StackSlotIcon(DockSlot.Stack(stack.name, stack.apps, mode, glyph, stack.image, stack.shortcuts))
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
        // Los accesos fijados desde otras apps: se quitan de la pila, pero siguen saliendo en la barra.
        if (query.isBlank() && stack.shortcuts.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.stack_pinned_title)) }
            items(stack.shortcuts, key = { it.key }) { shortcut ->
                ItemRow(
                    title = AnnotatedString(shortcut.label),
                    icon = shortcut.app.icon,
                    subtitle = shortcut.app.label,
                    // Como en «Añadir», tocar la fila la cambia de lado: aquí, la quita.
                    onClick = { onRemoveShortcut(shortcut) },
                    actions = {
                        RoundIconButton(
                            R.drawable.ic_close,
                            stringResource(R.string.stack_remove_app, shortcut.label),
                            AlisioTokens.TextSecondary,
                            onClick = { onRemoveShortcut(shortcut) }
                        )
                    }
                )
            }
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
    val view = LocalView.current

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
                                            AlisioHaptics.dragStart(view)
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
                                            if (step != 0 && move(app, step)) {
                                                offset -= step * rowHeightPx
                                                AlisioHaptics.step(view)
                                            }
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
    modifier: Modifier = Modifier,
    onExplain: (() -> Unit)? = null,
    art: Bitmap? = null
) {
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

            NowRule.MUSIC -> stringResource(R.string.now_reason_music)

            NowRule.HABIT -> stringResource(R.string.now_reason_habit)
        }
    NowPlate(
        // La carátula de lo último que sonó, como en el reproductor: se reconoce qué se va a reanudar.
        icon = { AppIcon(art ?: app.icon, AlisioTokens.IconSlot) },
        title = if (suggestion.rule ==
            NowRule.MUSIC
        ) {
            stringResource(R.string.now_music_title, app.label)
        } else {
            app.label
        },
        reason = reason,
        onOpen = onOpen,
        onLongPress = onExplain,
        modifier = modifier
    ) {
        RoundIconButton(
            R.drawable.ic_close,
            stringResource(R.string.now_dismiss),
            LocalHomePlate.current.secondary,
            onDismiss,
            iconSize = 20.dp
        )
    }
}

/**
 * Ahora en vivo (docs/IDEAS.md §2.7): lo que está en curso en otra app, con su título y su motivo
 * («En curso · Maps»). La cuenta atrás avanza con el minuto compartido, sin un bucle propio.
 */
@Composable
internal fun LiveCard(
    live: LiveNow,
    appLabel: String,
    icon: Bitmap?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nowMillis = rememberNow().toInstant().toEpochMilli()
    val detail =
        live.countdownTo?.let { until ->
            val minutes = ((until - nowMillis + 59_999L) / 60_000L).coerceAtLeast(0).toInt()
            pluralStringResource(R.plurals.now_live_left, minutes, minutes)
        } ?: live.text
    val reason = listOfNotNull(stringResource(R.string.now_live_reason, appLabel), detail)
        .joinToString(stringResource(R.string.list_separator))
    NowPlate(
        icon = {
            if (icon !=
                null
            ) {
                AppIcon(icon, AlisioTokens.IconSlot)
            } else {
                VectorTileIcon(R.drawable.ic_schedule, size = AlisioTokens.IconSlot)
            }
        },
        title = live.title,
        reason = reason,
        onOpen = onOpen,
        modifier = modifier
    )
}

/**
 * La placa de Ahora: icono, título y motivo, y al final lo que haga falta (descartar). Mantenerla
 * explica por qué sale, si se puede ([onLongPress]); para TalkBack es una acción con nombre.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlate(
    icon: @Composable () -> Unit,
    title: String,
    reason: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    val shape = AlisioTokens.CardShape
    val spoken = stringResource(R.string.now_description, title, reason)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .clip(shape)
                .background(LocalHomePlate.current.fill, shape)
                .combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = stringResource(R.string.now_why),
                    onLongClick = onLongPress,
                    onClick = onOpen
                )
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(AlisioTokens.IconGap))
        Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = spoken }) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = AlisioTokens.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalHomePlate.current.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing()
    }
}

/**
 * Abre lo que está en curso por la pantalla que la propia notificación abre. Alisio cede su permiso
 * de abrir actividades, como con la alarma (`pendingIntentOptions`); si el sistema no lo deja,
 * devuelve `false` y quien llama abre la app por su icono.
 */
private fun Context.sendLive(live: LiveNow): Boolean {
    val open = live.open ?: return false
    return try {
        open.send(this, 0, null, null, null, null, pendingIntentOptions())
        true
    } catch (e: PendingIntent.CanceledException) {
        Log.w("HomeScreen", "Lo que estaba en curso ya no abre nada", e)
        false
    }
}

/**
 * Por qué sale una sugerencia aprendida y cómo olvidarla en el sitio (docs/IDEAS.md §2.6). Olvidar
 * no borra el historial: la costumbre deja de contar y se vuelve a aprender si se repite.
 */
@Composable
private fun NowWhySheet(suggestion: NowSuggestion, app: AppEntry, onForget: () -> Unit, onDismiss: () -> Unit) {
    AlisioSheet(onDismiss = onDismiss, expanded = false) {
        SheetTitle(app.label)
        // Las filas llevan su margen, como en las demás hojas: así su contenido cae en el filo.
        Column(Modifier.padding(horizontal = AlisioTokens.Outset)) {
            Note(
                when (suggestion.rule) {
                    NowRule.BLUETOOTH ->
                        pluralStringResource(
                            R.plurals.now_why_bluetooth,
                            suggestion.evidence,
                            suggestion.device.orEmpty(),
                            suggestion.evidence
                        )

                    NowRule.MUSIC -> stringResource(R.string.now_why_music, suggestion.evidence, HABIT_DAYS)

                    else -> stringResource(R.string.now_why_habit, suggestion.evidence, HABIT_DAYS)
                }
            )
            Spacer(Modifier.height(8.dp))
            ItemRow(
                title = AnnotatedString(
                    stringResource(
                        if (suggestion.rule == NowRule.BLUETOOTH) {
                            R.string.now_forget_bluetooth
                        } else {
                            R.string.now_forget_habit
                        }
                    )
                ),
                leadingIcon = R.drawable.ic_restart,
                subtitle = stringResource(R.string.now_forget_hint),
                onClick = onForget
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
