package dev.jesusmolano.alisio.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.settings.Access
import dev.jesusmolano.alisio.core.settings.SettingsDestination
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.contacts.ContactsRepository
import dev.jesusmolano.alisio.data.settings.AlisioSettings
import dev.jesusmolano.alisio.data.settings.Prefs
import dev.jesusmolano.alisio.feature.home.DockSlot
import dev.jesusmolano.alisio.feature.home.HomePreview
import dev.jesusmolano.alisio.feature.home.HomeViewModel
import dev.jesusmolano.alisio.feature.home.dockApps
import dev.jesusmolano.alisio.feature.home.favoritePeople
import dev.jesusmolano.alisio.feature.zones.ZoneView
import dev.jesusmolano.alisio.feature.zones.ZonesViewModel
import dev.jesusmolano.alisio.ui.components.BackFollow
import dev.jesusmolano.alisio.ui.components.BackFollowLayer
import dev.jesusmolano.alisio.ui.components.RoundIconButton
import dev.jesusmolano.alisio.ui.theme.AlisioMotion
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

// Alto de la ventana al Inicio en miniatura, sin contar la barra de estado, hasta que la miniatura
// mide el suyo: con esto caben la hora, Ahora, el reproductor, el dock y la píldora.
private val PreviewHeight = 292.dp

// El botón de volver se centra en la columna de los dibujos de las filas, y el título empieza
// donde empieza el texto de las filas (el segundo filo).
private val HeaderStart = AlisioTokens.Edge + (AlisioTokens.IconSlot - AlisioTokens.MinTouch) / 2
private val HeaderGap = AlisioTokens.IconGap + (AlisioTokens.IconSlot - AlisioTokens.MinTouch) / 2

// Hasta dónde se encoge el título si no cabe en una línea (letra del sistema grande), y en qué pasos.
private const val TITLE_MIN_SCALE = 0.7f
private const val TITLE_STEP_SCALE = 0.05f

// Aire al final de cada página: la barra de deshacer sale ahí y no debe tapar la última fila.
private val PageBottom = 88.dp

// Lo que queda de la miniatura bajo la barra de estado con el cuerpo subido del todo: la hora y la
// primera línea bajo ella.
private val PreviewMinVisible = 96.dp

/**
 * El cuerpo de los ajustes sube sobre la miniatura antes de desplazar la página y baja cuando la
 * página ya está arriba del todo (lo que Material llama «salir hasta plegarse»).
 *
 * @param collapse cuánto ha subido el cuerpo, en píxeles; lo lee el marco para colocarlo.
 * @param limit hasta dónde puede subir ahora: cambia con el alto de la miniatura.
 */
private class SheetCollapse(private val collapse: MutableFloatState, private val limit: () -> Float) :
    NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Solo el dedo que sube (la página avanza) pliega antes de desplazar.
        if (available.y >= 0f) return Offset.Zero
        return Offset(0f, -move(-available.y))
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // Lo que sobra al bajar con la página ya arriba del todo despliega la miniatura.
        if (available.y <= 0f) return Offset.Zero
        return Offset(0f, -move(-available.y))
    }

    /** Sube (positivo) o baja el cuerpo dentro de su límite y devuelve lo que se ha movido. */
    private fun move(delta: Float): Float {
        val before = collapse.floatValue.coerceIn(0f, limit())
        val after = (before + delta).coerceIn(0f, limit())
        collapse.floatValue = after
        return after - before
    }
}

/**
 * Lo que la sección Zonas enseña y hace.
 *
 * @param islands las islas propias, con sus apps, en su orden.
 * @param onOpen cierra los ajustes y abre Zonas, para ver cómo queda.
 * @param editor quien crea, renombra, llena y borra las islas: lo usa la hoja de una isla.
 */
class ZoneSettings(val islands: List<ZoneView>, val onOpen: () -> Unit, val editor: ZonesViewModel)

/**
 * Lo que el sistema le ha concedido a Alisio y cómo pedir cada cosa. Cada `on…` pide el acceso si
 * falta y, si ya está, abre la pantalla del sistema donde se retira.
 *
 * @param bluetoothDevice nombre del dispositivo de audio Bluetooth conectado ahora, si lo hay.
 * @param onAccessibility sin el servicio, enseña el aviso previo; con él, abre los ajustes de
 *   accesibilidad del sistema.
 */
class SettingsAccess(
    val homeRole: Boolean,
    val onHomeRole: () -> Unit,
    val contacts: Boolean,
    val onContacts: () -> Unit,
    val calendar: Boolean,
    val onCalendar: () -> Unit,
    val location: Boolean,
    val onLocation: () -> Unit,
    val bluetooth: Boolean,
    val bluetoothDevice: String?,
    val onBluetooth: () -> Unit,
    val notifications: Boolean,
    val onNotifications: () -> Unit,
    val usage: Boolean,
    val onUsage: () -> Unit,
    val accessibility: Boolean,
    val onAccessibility: () -> Unit,
    val writeSettings: Boolean = false,
    val onWriteSettings: () -> Unit = {},
    val doNotDisturb: Boolean = false,
    val onDoNotDisturb: () -> Unit = {}
) {
    val granted: Set<Access> =
        buildSet {
            if (homeRole) add(Access.HOME_ROLE)
            if (contacts) add(Access.CONTACTS)
            if (calendar) add(Access.CALENDAR)
            if (location) add(Access.LOCATION)
            if (bluetooth) add(Access.BLUETOOTH)
            if (notifications) add(Access.NOTIFICATIONS)
            if (usage) add(Access.USAGE)
            if (accessibility) add(Access.ACCESSIBILITY)
            if (writeSettings) add(Access.SYSTEM_SETTINGS)
            if (doNotDisturb) add(Access.DO_NOT_DISTURB)
        }
}

/** Lo que comparten todas las páginas de los ajustes. */
internal class SettingsContext(
    val prefs: Prefs,
    val settings: AlisioSettings,
    val apps: List<AppEntry>,
    val dock: List<DockSlot>,
    val access: SettingsAccess
)

/**
 * Ajustes de Alisio (docs/DISENO.md §6.9): una portada con sus secciones y una subpágina por
 * sección, nunca una lista única. La portada, Aspecto e Inicio llevan arriba el Inicio en miniatura,
 * que cambia con cada opción; el resto ocupa toda la pantalla. Cada opción se aplica al tocarla.
 *
 * @param page la página que se enseña; la lleva quien llama para que el gesto atrás vuelva de una
 *   subpágina a la portada y para que la barra pueda abrir una sección concreta.
 * @param onClose cierra los ajustes: es el botón de volver de la portada.
 * @param home de dónde salen las apps, el dock y los destinos al deslizar que se enseñan y se cambian.
 * @param contacts la agenda, para enseñar una foto real al elegir entre fotos e iniciales.
 * @param onWallpaper abre el selector de fondo de pantalla del sistema.
 * @param onPreviewPause enseña la pausa de esa app tal como saldrá, sin abrirla.
 * @param backup guarda la configuración en un archivo o la carga de uno.
 * @param wallpaper elige la carpeta de los fondos que rotan y pasa al siguiente.
 */
@Composable
fun SettingsScreen(
    page: SettingsDestination,
    back: BackFollow,
    onNavigate: (SettingsDestination) -> Unit,
    onClose: () -> Unit,
    home: HomeViewModel,
    zones: ZoneSettings,
    prefs: Prefs,
    settings: AlisioSettings,
    access: SettingsAccess,
    contacts: ContactsRepository,
    onWallpaper: () -> Unit,
    onPreviewPause: (AppEntry) -> Unit,
    backup: BackupActions,
    wallpaper: WallpaperActions,
    modifier: Modifier = Modifier
) {
    val loadedApps by home.apps.collectAsStateWithLifecycle()
    val loadedDock by home.dock.collectAsStateWithLifecycle()
    val swipeTargets by home.swipeTargets.collectAsStateWithLifecycle()
    val glance by home.glance.collectAsStateWithLifecycle()
    val notes by home.notes.collectAsStateWithLifecycle()
    val apps = loadedApps.orEmpty()
    val dock = loadedDock.orEmpty()
    val context = remember(prefs, settings, apps, dock, access) { SettingsContext(prefs, settings, apps, dock, access) }
    // La sugerencia de muestra es una app que no está ya en el dock: así no sale dos veces en la miniatura.
    val sampleApp =
        remember(apps, dock) {
            val inDock = dock.dockApps().mapTo(HashSet()) { it.key }
            apps.firstOrNull { it.key !in inDock } ?: apps.firstOrNull()
        }
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Lo que mide la miniatura por dentro (HomePreview lo mide: la línea bajo la hora, las notas, Ahora,
    // las caras y el dock cambian con cada opción, con la letra y con el tamaño de la hora). Hasta la
    // primera medida vale el alto de siempre.
    var measured by remember { mutableStateOf<Dp?>(null) }
    // La ventana incluye la barra de estado: el fondo de pantalla llega hasta arriba, como en el Inicio.
    val window by animateDpAsState(
        targetValue = if (page.hasPreview) statusBar + (measured ?: PreviewHeight) else 0.dp,
        animationSpec = AlisioMotion.standard(),
        label = "settings-window"
    )
    val density = LocalDensity.current
    val rise = with(density) { AlisioMotion.Rise.roundToPx() }
    // Con la línea, las notas y las caras la miniatura llega a pasar de media pantalla: al desplazar la
    // página, el cuerpo sube sobre ella como una hoja (hasta dejar la hora a la vista) y baja al volver
    // arriba. Así las opciones tienen sitio sin quitar la vista previa.
    val maxCollapse =
        with(density) { (window - statusBar - PreviewMinVisible).coerceAtLeast(0.dp).toPx() }
    val collapse = remember { mutableFloatStateOf(0f) }
    val limit by rememberUpdatedState(maxCollapse)
    LaunchedEffect(page) { collapse.floatValue = 0f }
    val bodyScroll = remember { SheetCollapse(collapse) { limit } }
    val shown = with(density) { collapse.floatValue.coerceAtMost(maxCollapse).toDp() }

    Box(
        modifier
            .fillMaxSize()
            // La pantalla se queda los toques: no llegan al Inicio de debajo.
            .pointerInput(Unit) {}
    ) {
        if (window > 0.dp) {
            HomePreview(
                prefs = prefs,
                dock = dock,
                swipeOwners = swipeTargets.keys,
                sampleApp = sampleApp,
                notificationsGranted = access.notifications,
                glance = glance,
                modifier = Modifier.fillMaxWidth().height(window),
                notes = notes,
                onContentHeight = { measured = it }
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = window - shown)
                .clip(if (window > 0.dp) AlisioTokens.SheetShape else RectangleShape)
                .background(AlisioTokens.Background)
                .navigationBarsPadding()
                // Con el teclado abierto al escribir, el final de la página sigue siendo alcanzable.
                .imePadding()
                .nestedScroll(bodyScroll)
        ) {
            // Sin ventana, el cuerpo llega arriba del todo y deja libre la barra de estado.
            Spacer(Modifier.height((statusBar - window).coerceAtLeast(0.dp)))
            AnimatedContent(
                targetState = page,
                transitionSpec = { AlisioMotion.pageChange(rise) },
                label = "settings-page"
            ) { shown ->
                // Atrás desde una subpágina la baja con el dedo y deja quieta la miniatura de arriba.
                BackFollowLayer(back, shown) {
                    Column(Modifier.fillMaxSize()) {
                        val cover = shown == SettingsDestination.COVER
                        Header(
                            title = stringResource(shown.title()),
                            backLabel = stringResource(if (cover) R.string.settings_close else R.string.appmenu_back),
                            onBack = { if (cover) onClose() else onNavigate(SettingsDestination.COVER) }
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = AlisioTokens.Outset)
                                .padding(bottom = PageBottom)
                        ) {
                            when (shown) {
                                SettingsDestination.COVER -> CoverPage(context, zones, backup, onNavigate)
                                SettingsDestination.LOOK -> LookPage(context, home, onWallpaper, wallpaper)
                                SettingsDestination.ICONS -> IconsPage(context, home)
                                SettingsDestination.HOME -> HomePage(context, home)
                                SettingsDestination.GESTURES -> GesturesPage(context, home)
                                SettingsDestination.SEARCH -> SearchPage(context, contacts)
                                SettingsDestination.ZONES -> ZonesPage(context, zones, home)
                                SettingsDestination.PAUSE -> PausePage(context, onPreviewPause)
                                SettingsDestination.PERMISSIONS -> PermissionsPage(access)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** El nombre de cada página: su título y, en la portada, el de su fila. */
internal fun SettingsDestination.title(): Int = when (this) {
    SettingsDestination.COVER -> R.string.settings_title
    SettingsDestination.LOOK -> R.string.settings_appearance
    SettingsDestination.ICONS -> R.string.settings_icons
    SettingsDestination.HOME -> R.string.settings_home
    SettingsDestination.GESTURES -> R.string.settings_gestures
    SettingsDestination.SEARCH -> R.string.settings_search
    SettingsDestination.ZONES -> R.string.settings_zones
    SettingsDestination.PAUSE -> R.string.settings_pause
    SettingsDestination.PERMISSIONS -> R.string.settings_permissions
}

/** Cabecera fija de una página: volver y su título, que no se van al desplazar. */
@Composable
private fun Header(title: String, backLabel: String, onBack: () -> Unit) {
    val style = MaterialTheme.typography.headlineLarge
    Row(
        Modifier.fillMaxWidth().padding(start = HeaderStart, end = AlisioTokens.Edge, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(R.drawable.ic_arrow_back, backLabel, AlisioTokens.Text, onBack)
        Spacer(Modifier.width(HeaderGap))
        Text(
            text = title,
            style = style,
            color = AlisioTokens.Text,
            maxLines = 1,
            autoSize =
                TextAutoSize.StepBased(
                    minFontSize = style.fontSize * TITLE_MIN_SCALE,
                    maxFontSize = style.fontSize,
                    stepSize = style.fontSize * TITLE_STEP_SCALE
                ),
            modifier = Modifier.semantics { heading() }
        )
    }
}
