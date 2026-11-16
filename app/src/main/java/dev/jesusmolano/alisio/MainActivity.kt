package dev.jesusmolano.alisio

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesusmolano.alisio.core.settings.SettingsDestination
import dev.jesusmolano.alisio.core.zones.ZoneId
import dev.jesusmolano.alisio.data.pause.PendingLaunch
import dev.jesusmolano.alisio.data.settings.Prefs
import dev.jesusmolano.alisio.feature.home.HomeScreen
import dev.jesusmolano.alisio.feature.home.HomeViewModel
import dev.jesusmolano.alisio.feature.home.dockApps
import dev.jesusmolano.alisio.feature.onboarding.OnboardingScreen
import dev.jesusmolano.alisio.feature.pause.PauseScreen
import dev.jesusmolano.alisio.feature.search.SearchScreen
import dev.jesusmolano.alisio.feature.search.SearchViewModel
import dev.jesusmolano.alisio.feature.settings.AccessibilityNotice
import dev.jesusmolano.alisio.feature.settings.SettingsAccess
import dev.jesusmolano.alisio.feature.settings.SettingsScreen
import dev.jesusmolano.alisio.feature.settings.ZoneSettings
import dev.jesusmolano.alisio.feature.zones.ZonesScreen
import dev.jesusmolano.alisio.feature.zones.ZonesViewModel
import dev.jesusmolano.alisio.ui.components.LocalUndo
import dev.jesusmolano.alisio.ui.components.UndoBar
import dev.jesusmolano.alisio.ui.components.rememberUndoState
import dev.jesusmolano.alisio.ui.theme.AlisioMotion
import dev.jesusmolano.alisio.ui.theme.AlisioTheme

// Menos que lo que tarda nadie en responder a un diálogo de permisos.
private const val NO_DIALOG_MILLIS = 350L

class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModel.factory((application as AlisioApp).container)
    }
    private val searchViewModel: SearchViewModel by viewModels {
        SearchViewModel.factory((application as AlisioApp).container, applicationContext)
    }
    private val zonesViewModel: ZonesViewModel by viewModels {
        ZonesViewModel.factory((application as AlisioApp).container)
    }

    private var isDefaultHome by mutableStateOf(false)
    private var searchOpen by mutableStateOf(false)
    private var homePresses by mutableIntStateOf(0)

    // Sube en cada apertura de la barra: así empieza siempre de cero, aunque se reabra antes de que
    // termine su animación de salida y su contenido siga montado.
    private var searchSession by mutableIntStateOf(0)
    private var contactsAskedAt = 0L
    private var contactsRequestPending = false
    private var bluetoothAskedAt = 0L
    private var bluetoothRequestPending = false
    private var settingsOpen by mutableStateOf(false)

    // La página de los ajustes que se enseña. Vive aquí para que el gesto atrás vuelva de una
    // subpágina a la portada y para que la barra abra una sección concreta.
    private var settingsPage by mutableStateOf(SettingsDestination.COVER)
    private var zonesOpen by mutableStateOf(false)
    private var accessibilityNoticeOpen by mutableStateOf(false)

    // Se sale a una pantalla del sistema a conceder algo: al volver, los ajustes (o Zonas, si se salió
    // desde el Interior de una app) deben seguir abiertos para ver el resultado donde se tocó.
    private var leavingForSystemSettings = false

    // El resultado no importa: onResume vuelve a comprobar el rol.
    private val requestHomeRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    private val requestContacts =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            contactsRequestPending = false
            val contacts = (application as AlisioApp).container.contacts
            contacts.onPermissionChanged()
            // Denegado para siempre, el sistema responde al instante y sin diálogo: el único camino
            // que queda es la ficha de la app, y se abre para que el botón no parezca roto.
            val answeredWithoutDialog = SystemClock.uptimeMillis() - contactsAskedAt < NO_DIALOG_MILLIS
            if (!contacts.granted.value && answeredWithoutDialog) openAppDetails()
        }

    private val requestBluetooth =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            bluetoothRequestPending = false
            val bluetooth = (application as AlisioApp).container.bluetoothAudio
            bluetooth.onPermissionChanged()
            // Como en los contactos: denegado para siempre no hay diálogo, y se abre la ficha de la app.
            val answeredWithoutDialog = SystemClock.uptimeMillis() - bluetoothAskedAt < NO_DIALOG_MILLIS
            if (!bluetooth.granted.value && answeredWithoutDialog) openAppDetails()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val transparent = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = transparent, navigationBarStyle = transparent)
        super.onCreate(savedInstanceState)
        isDefaultHome = holdsHomeRole()
        StartupTrace.mark("actividad creada")

        setContent {
            val settings = (application as AlisioApp).container.settings
            val loadedPrefs by settings.prefs.collectAsStateWithLifecycle()
            // Hasta leer las preferencias no se pinta nada: si no, saldrían un instante el acento y el
            // velo por defecto antes que los elegidos.
            val prefs = loadedPrefs ?: return@setContent
            // Primer fotograma con contenido: los anteriores van vacíos a la espera de las preferencias.
            LaunchedEffect(Unit) {
                withFrameNanos {}
                StartupTrace.mark("primer fotograma")
                reportFullyDrawn()
            }
            AlisioTheme(accent = prefs.accent, font = prefs.font) {
                // Una sola barra de deshacer para toda la actividad: la usan el Inicio, Zonas y los ajustes.
                val undo = rememberUndoState()
                CompositionLocalProvider(LocalUndo provides undo) {
                    // Atrás cierra lo que haya encima del Inicio; en el Inicio no hace nada.
                    val container = (application as AlisioApp).container
                    val pendingPause by container.launchPause.pending.collectAsStateWithLifecycle()
                    val accessibilityEnabled by container.globalActions.enabled.collectAsStateWithLifecycle()
                    BackHandler {
                        // De arriba abajo: la pausa, el aviso de accesibilidad, los ajustes, la barra y,
                        // debajo de todos, Zonas.
                        when {
                            pendingPause != null -> container.launchPause.cancel()

                            accessibilityNoticeOpen -> accessibilityNoticeOpen = false

                            // De una subpágina de los ajustes se vuelve a su portada; de la portada, se sale.
                            settingsOpen && settingsPage != SettingsDestination.COVER ->
                                settingsPage = SettingsDestination.COVER

                            settingsOpen -> settingsOpen = false

                            searchOpen -> searchOpen = false

                            else -> zonesOpen = false
                        }
                    }
                    // App cuyo Interior piden la barra o Zonas; lo abre el Inicio, que es quien tiene esa hoja.
                    var menuRequest by remember { mutableStateOf<String?>(null) }
                    if (isDefaultHome || prefs.onboardingSkipped) {
                        Box(Modifier.fillMaxSize()) {
                            HomeScreen(
                                menuRequest = menuRequest,
                                onMenuRequestTaken = { menuRequest = null },
                                viewModel = homeViewModel,
                                zones = zonesViewModel,
                                onOpenZones = { zonesOpen = true },
                                homePresses = homePresses,
                                // Con algo encima, Trazo se retira.
                                searchOpen = searchOpen || settingsOpen || zonesOpen || pendingPause != null,
                                prefs = prefs,
                                onOpenSettings = { openSettings() },
                                onGrantUsage = ::openUsageAccess,
                                onGrantHomeRole = if (isDefaultHome) null else ({ requestHomeRole() }),
                                // Sin el servicio activado no hay orden que dar: el gesto ni se escucha.
                                onLockScreen =
                                    if (accessibilityEnabled && prefs.lockDoubleTap) {
                                        { if (!container.globalActions.lockScreen()) onGestureIgnored() }
                                    } else {
                                        null
                                    },
                                onShowNotifications =
                                    if (accessibilityEnabled && prefs.notificationsSwipe) {
                                        { if (!container.globalActions.showNotifications()) onGestureIgnored() }
                                    } else {
                                        null
                                    },
                                onOpenSearch = {
                                    searchSession++
                                    searchOpen = true
                                }
                            )
                            // Zonas va debajo de la barra y de los ajustes, que pueden abrirse el uno al otro.
                            AnimatedVisibility(
                                visible = zonesOpen,
                                enter = AlisioMotion.screenEnter(),
                                exit = AlisioMotion.screenExit()
                            ) {
                                ZonesScreen(
                                    viewModel = zonesViewModel,
                                    prefs = prefs,
                                    onClose = { zonesOpen = false },
                                    // Zonas sigue debajo: atrás desde los ajustes vuelve a ella.
                                    onOpenSettings = { openSettings() },
                                    onAppMenu = { menuRequest = it.key.toString() }
                                )
                            }
                            // One UI no anima el gesto atrás en apps de terceros (§9): la barra trae su animación.
                            AnimatedVisibility(
                                visible = searchOpen,
                                enter = AlisioMotion.screenEnter(),
                                exit = AlisioMotion.screenExit()
                            ) {
                                key(searchSession) {
                                    SearchScreen(
                                        viewModel = searchViewModel,
                                        onAskContacts = ::askContacts,
                                        onClose = { searchOpen = false },
                                        onAppMenu = { menuRequest = it.key.toString() },
                                        prefs = prefs,
                                        onOpenSettings = { page ->
                                            searchOpen = false
                                            openSettings(page)
                                        },
                                        onOpenZones = {
                                            searchOpen = false
                                            zonesOpen = true
                                        }
                                    )
                                }
                            }
                            AnimatedVisibility(
                                visible = settingsOpen,
                                enter = AlisioMotion.screenEnter(),
                                exit = AlisioMotion.screenExit()
                            ) {
                                val contacts = (application as AlisioApp).container.contacts
                                val contactsGranted by contacts.granted.collectAsStateWithLifecycle()
                                val bluetooth = (application as AlisioApp).container.bluetoothAudio
                                val bluetoothGranted by bluetooth.granted.collectAsStateWithLifecycle()
                                val bluetoothConnected by bluetooth.connected.collectAsStateWithLifecycle()
                                val notificationAccess = (application as AlisioApp).container.notificationAccess
                                val notificationsGranted by notificationAccess.granted.collectAsStateWithLifecycle()
                                val usageGranted by homeViewModel.usageGranted.collectAsStateWithLifecycle()
                                val zonesState by zonesViewModel.state.collectAsStateWithLifecycle()
                                SettingsScreen(
                                    page = settingsPage,
                                    onNavigate = { settingsPage = it },
                                    onClose = { settingsOpen = false },
                                    home = homeViewModel,
                                    zones =
                                        ZoneSettings(
                                            islands = zonesState?.zones.orEmpty().filter { it.id is ZoneId.Custom },
                                            onOpen = {
                                                settingsOpen = false
                                                zonesOpen = true
                                            },
                                            editor = zonesViewModel
                                        ),
                                    prefs = prefs,
                                    settings = settings,
                                    access =
                                        SettingsAccess(
                                            homeRole = isDefaultHome,
                                            onHomeRole = {
                                                if (isDefaultHome) openHomeSettings() else requestHomeRole()
                                            },
                                            contacts = contactsGranted,
                                            onContacts = { if (contactsGranted) openAppDetails() else askContacts() },
                                            bluetooth = bluetoothGranted,
                                            bluetoothDevice = bluetoothConnected?.name,
                                            onBluetooth = {
                                                if (bluetoothGranted) openAppDetails() else askBluetooth()
                                            },
                                            notifications = notificationsGranted,
                                            onNotifications = ::openNotificationAccess,
                                            usage = usageGranted,
                                            onUsage = ::openUsageAccess,
                                            accessibility = accessibilityEnabled,
                                            // Activado, el aviso ya se leyó: se va directo a donde se desactiva.
                                            onAccessibility = {
                                                if (accessibilityEnabled) {
                                                    openAccessibilitySettings()
                                                } else {
                                                    accessibilityNoticeOpen = true
                                                }
                                            }
                                        ),
                                    contacts = contacts,
                                    onWallpaper = ::openWallpaperPicker,
                                    onPreviewPause = { container.launchPause.preview(it, prefs.pauseSeconds) }
                                )
                            }
                            AnimatedVisibility(
                                visible = accessibilityNoticeOpen,
                                enter = AlisioMotion.screenEnter(),
                                exit = AlisioMotion.screenExit()
                            ) {
                                AccessibilityNotice(
                                    onContinue = ::openAccessibilitySettings,
                                    onDismiss = { accessibilityNoticeOpen = false }
                                )
                            }
                            // La pausa va encima de todo: una app se abre desde el Inicio, la barra o Zonas.
                            // Se guarda la última para que su salida animada siga teniendo qué pintar.
                            var shownPause by remember { mutableStateOf<PendingLaunch?>(null) }
                            LaunchedEffect(pendingPause) { pendingPause?.let { shownPause = it } }
                            AnimatedVisibility(
                                visible = pendingPause != null,
                                enter = fadeIn(AlisioMotion.standard()),
                                exit = AlisioMotion.screenExit()
                            ) {
                                (pendingPause ?: shownPause)?.let { pause ->
                                    PauseScreen(
                                        launch = pause,
                                        onOpen = {
                                            if (!container.launchPause.confirm()) {
                                                val message = getString(R.string.app_launch_failed, pause.label)
                                                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onCancel = container.launchPause::cancel
                                    )
                                }
                            }
                            // Encima de todo y en el sitio de la píldora: junto al pulgar, sin tapar el dock.
                            UndoBar(
                                undo,
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .imePadding()
                                    .padding(bottom = 16.dp)
                            )
                        }
                    } else {
                        // El onboarding va sobre fondo opaco: la ventana deja ver el fondo de pantalla.
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            OnboardingScreen(
                                onSetDefault = ::requestHomeRole,
                                // Se guarda: si no, la bienvenida volvería cada vez que el sistema
                                // recrea la actividad.
                                onSkip = { settings.skipOnboarding() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        homeViewModel.refreshNow()
        // El permiso de contactos también se puede cambiar desde los ajustes del sistema.
        (application as AlisioApp).container.contacts.onPermissionChanged()
        (application as AlisioApp).container.bluetoothAudio.onPermissionChanged()
        // El acceso a notificaciones se concede en los ajustes del sistema: al volver ya puede estar.
        (application as AlisioApp).container.notificationAccess.refresh()
        (application as AlisioApp).container.mediaSessions.refresh()
        // Lo mismo con el acceso a uso.
        (application as AlisioApp).container.usageAccess.refresh()
        // Y con el servicio de accesibilidad, que se activa y se desactiva en los ajustes del sistema.
        (application as AlisioApp).container.globalActions.refresh()
        val holdsRole = holdsHomeRole()
        if (holdsRole && !isDefaultHome) homeViewModel.onHomeRoleGranted()
        isDefaultHome = holdsRole
    }

    override fun onStop() {
        super.onStop()
        // Al volver de otra app el Inicio aparece limpio, sin la barra, Zonas ni los ajustes a medias.
        searchOpen = false
        accessibilityNoticeOpen = false
        // Una pausa a medias no espera a la vuelta: abrir la app más tarde sería una sorpresa.
        (application as AlisioApp).container.launchPause.cancel()
        if (!leavingForSystemSettings) {
            settingsOpen = false
            zonesOpen = false
        }
        // Vale para una sola salida: si después se va a otra app, el Inicio vuelve limpio.
        leavingForSystemSettings = false
    }

    /** Pulsar «inicio» estando ya en Alisio llega aquí: se cierran la barra y las hojas. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            searchOpen = false
            settingsOpen = false
            zonesOpen = false
            accessibilityNoticeOpen = false
            (application as AlisioApp).container.launchPause.cancel()
            homePresses++
        }
    }

    /** Abre los ajustes de Alisio en su portada o, desde un resultado de la barra, en una sección. */
    private fun openSettings(page: SettingsDestination = SettingsDestination.COVER) {
        settingsPage = page
        settingsOpen = true
    }

    /**
     * El selector de fondo de pantalla del sistema. No pide permisos: el fondo lo pone la app que el
     * usuario elija ahí. Al volver, los ajustes siguen abiertos y la miniatura ya enseña el fondo nuevo.
     */
    private fun openWallpaperPicker() {
        leavingForSystemSettings = true
        // El selector existe siempre: si ninguna app pone fondos, es él quien lo dice.
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), null))
    }

    /** Leer la agenda y, si se concede, llamar sin pasar por el marcador. Se piden juntos. */
    private fun askContacts() {
        // Un segundo toque con la petición en curso la cancelaría con un resultado vacío al instante,
        // que se confundiría con un permiso denegado para siempre.
        if (contactsRequestPending) return
        contactsRequestPending = true
        contactsAskedAt = SystemClock.uptimeMillis()
        requestContacts.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
    }

    /** Dispositivos cercanos: solo para saber qué audio Bluetooth está conectado (regla de Ahora). */
    private fun askBluetooth() {
        // Igual que en [askContacts]: un segundo toque no debe parecer un «denegado para siempre».
        if (bluetoothRequestPending) return
        bluetoothRequestPending = true
        bluetoothAskedAt = SystemClock.uptimeMillis()
        requestBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }

    /** La ficha de Alisio en los ajustes del sistema: ahí se retiran los permisos. */
    private fun openAppDetails() {
        leavingForSystemSettings = true
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()))
    }

    /**
     * El acceso a notificaciones no se pide con un diálogo: se da en una pantalla del sistema. Se abre
     * directamente la de Alisio y, si este sistema no la tiene, la lista de todas las apps.
     */
    private fun openNotificationAccess() {
        leavingForSystemSettings = true
        val component = (application as AlisioApp).container.notificationAccess.component
        val detail =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        try {
            startActivity(detail)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                openAppDetails()
            }
        }
    }

    /**
     * El acceso a uso tampoco se pide con un diálogo. Se intenta abrir la pantalla de Alisio dentro
     * de «Acceso a datos de uso»; el sistema que no admite ir a una app concreta abre la lista.
     */
    private fun openUsageAccess() {
        leavingForSystemSettings = true
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, "package:$packageName".toUri()))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                openAppDetails()
            }
        }
    }

    /**
     * El servicio de accesibilidad solo se activa en los ajustes del sistema, y siempre después del
     * aviso propio ([AccessibilityNotice]). No hay pantalla de una app concreta: se abre la lista.
     */
    private fun openAccessibilitySettings() {
        leavingForSystemSettings = true
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            openAppDetails()
        }
    }

    /**
     * El servicio puede constar como activado sin que el sistema lo tenga enlazado (justo al recrear
     * el proceso, por ejemplo): `GlobalActions` devuelve entonces `false`, y un gesto que no hace nada
     * sin decir por qué parecería roto.
     */
    private fun onGestureIgnored() {
        Toast.makeText(this, R.string.gesture_service_silent, Toast.LENGTH_SHORT).show()
    }

    private fun holdsHomeRole(): Boolean = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

    /** Donde se cambia de app de inicio una vez que Alisio ya lo es. */
    private fun openHomeSettings() {
        leavingForSystemSettings = true
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            openAppDetails()
        }
    }

    private fun requestHomeRole() {
        val roleManager = getSystemService(RoleManager::class.java)
        try {
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                requestHomeRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
            } else {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
