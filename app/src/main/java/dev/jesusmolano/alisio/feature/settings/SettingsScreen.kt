package dev.jesusmolano.alisio.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import dev.jesusmolano.alisio.feature.zones.ZoneView
import dev.jesusmolano.alisio.feature.zones.ZonesViewModel
import dev.jesusmolano.alisio.ui.components.RoundIconButton
import dev.jesusmolano.alisio.ui.theme.AlisioMotion
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

// Alto de la ventana al Inicio en miniatura, sin contar la barra de estado. La guía pedía 220 dp;
// con menos de esto no caben a la vez la hora, Ahora, el reproductor, el dock y la píldora.
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
    val bluetooth: Boolean,
    val bluetoothDevice: String?,
    val onBluetooth: () -> Unit,
    val notifications: Boolean,
    val onNotifications: () -> Unit,
    val usage: Boolean,
    val onUsage: () -> Unit,
    val accessibility: Boolean,
    val onAccessibility: () -> Unit
) {
    val granted: Set<Access> =
        buildSet {
            if (homeRole) add(Access.HOME_ROLE)
            if (contacts) add(Access.CONTACTS)
            if (bluetooth) add(Access.BLUETOOTH)
            if (notifications) add(Access.NOTIFICATIONS)
            if (usage) add(Access.USAGE)
            if (accessibility) add(Access.ACCESSIBILITY)
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
 */
@Composable
fun SettingsScreen(
    page: SettingsDestination,
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
    modifier: Modifier = Modifier
) {
    val loadedApps by home.apps.collectAsStateWithLifecycle()
    val loadedDock by home.dock.collectAsStateWithLifecycle()
    val swipeTargets by home.swipeTargets.collectAsStateWithLifecycle()
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
    // La ventana incluye la barra de estado: el fondo de pantalla llega hasta arriba, como en el Inicio.
    val window by animateDpAsState(
        targetValue = if (page.hasPreview) statusBar + PreviewHeight else 0.dp,
        animationSpec = AlisioMotion.standard(),
        label = "settings-window"
    )
    val rise = with(LocalDensity.current) { AlisioMotion.Rise.roundToPx() }

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
                modifier = Modifier.fillMaxWidth().height(window)
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = window)
                .clip(if (window > 0.dp) AlisioTokens.SheetShape else RectangleShape)
                .background(AlisioTokens.Background)
                .navigationBarsPadding()
                // Con el teclado abierto al escribir, el final de la página sigue siendo alcanzable.
                .imePadding()
        ) {
            // Sin ventana, el cuerpo llega arriba del todo y deja libre la barra de estado.
            Spacer(Modifier.height((statusBar - window).coerceAtLeast(0.dp)))
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    (fadeIn(AlisioMotion.standard()) + slideInVertically(AlisioMotion.standard()) { rise }) togetherWith
                        fadeOut(AlisioMotion.exit())
                },
                label = "settings-page"
            ) { shown ->
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
                            SettingsDestination.COVER -> CoverPage(context, zones, onNavigate)
                            SettingsDestination.LOOK -> LookPage(context, home, onWallpaper)
                            SettingsDestination.HOME -> HomePage(context, home)
                            SettingsDestination.SEARCH -> SearchPage(context, contacts)
                            SettingsDestination.ZONES -> ZonesPage(zones)
                            SettingsDestination.PAUSE -> PausePage(context, onPreviewPause)
                            SettingsDestination.PERMISSIONS -> PermissionsPage(access)
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
    SettingsDestination.HOME -> R.string.settings_home
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
