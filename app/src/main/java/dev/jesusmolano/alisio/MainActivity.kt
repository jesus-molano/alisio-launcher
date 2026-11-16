package dev.jesusmolano.alisio

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.Choreographer
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.jesusmolano.alisio.core.icons.CustomIcon
import dev.jesusmolano.alisio.core.settings.SettingsDestination
import dev.jesusmolano.alisio.core.zones.ZoneId
import dev.jesusmolano.alisio.data.pause.PendingLaunch
import dev.jesusmolano.alisio.data.settings.Prefs
import dev.jesusmolano.alisio.data.usage.AppToday
import dev.jesusmolano.alisio.feature.home.HomeScreen
import dev.jesusmolano.alisio.feature.home.HomeViewModel
import dev.jesusmolano.alisio.feature.home.SystemCancel
import dev.jesusmolano.alisio.feature.home.dockApps
import dev.jesusmolano.alisio.feature.onboarding.OnboardingScreen
import dev.jesusmolano.alisio.feature.pause.PauseScreen
import dev.jesusmolano.alisio.feature.search.SearchScreen
import dev.jesusmolano.alisio.feature.search.SearchViewModel
import dev.jesusmolano.alisio.feature.settings.AccessibilityNotice
import dev.jesusmolano.alisio.feature.settings.BackupActions
import dev.jesusmolano.alisio.feature.settings.BackupMissingSheet
import dev.jesusmolano.alisio.feature.settings.BackupResult
import dev.jesusmolano.alisio.feature.settings.SettingsAccess
import dev.jesusmolano.alisio.feature.settings.SettingsScreen
import dev.jesusmolano.alisio.feature.settings.WallpaperActions
import dev.jesusmolano.alisio.feature.settings.ZoneSettings
import dev.jesusmolano.alisio.feature.zones.ZonesScreen
import dev.jesusmolano.alisio.feature.zones.ZonesViewModel
import dev.jesusmolano.alisio.ui.components.BackFollowLayer
import dev.jesusmolano.alisio.ui.components.FollowingBackHandler
import dev.jesusmolano.alisio.ui.components.ImageTarget
import dev.jesusmolano.alisio.ui.components.LocalPickImage
import dev.jesusmolano.alisio.ui.components.LocalUndo
import dev.jesusmolano.alisio.ui.components.UndoBar
import dev.jesusmolano.alisio.ui.components.rememberBackFollow
import dev.jesusmolano.alisio.ui.components.rememberNow
import dev.jesusmolano.alisio.ui.components.rememberUndoState
import dev.jesusmolano.alisio.ui.theme.AlisioHaptics
import dev.jesusmolano.alisio.ui.theme.AlisioMotion
import dev.jesusmolano.alisio.ui.theme.AlisioTheme
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import dev.jesusmolano.alisio.ui.theme.LocalHomePlate
import dev.jesusmolano.alisio.ui.theme.accentColor
import dev.jesusmolano.alisio.ui.theme.dayAccentColor
import kotlinx.coroutines.launch

// Menos que lo que tarda nadie en responder a un diálogo de permisos.
private const val NO_DIALOG_MILLIS = 350L

private const val BACKUP_MIME = "application/json"
private const val BACKUP_FILE_NAME = "alisio.json"
private const val STATE_SETTINGS_OPEN = "settings_open"
private const val STATE_SETTINGS_PAGE = "settings_page"
private const val STATE_IMAGE_TARGET = "image_target"

/** Las capas que tapan el Inicio y se cierran con atrás; una subpágina de los ajustes es la suya. */
private enum class Layer { PAUSE, NOTICE, SETTINGS, SEARCH, ZONES }

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

    // El sistema le ha quitado a Alisio el papel de inicio mientras estaba fuera: se avisa una vez.
    private var homeRoleLost by mutableStateOf(false)
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

    // Calendario y ubicación aproximada, para la línea bajo la hora. Como en los contactos: denegado
    // para siempre no hay diálogo, y se abre la ficha de la app para que el botón no parezca roto.
    private var glanceAskedAt = 0L
    private var glanceRequestPending = false
    private val requestGlancePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            glanceRequestPending = false
            (application as AlisioApp).container.glance.refresh()
            val answeredWithoutDialog = SystemClock.uptimeMillis() - glanceAskedAt < NO_DIALOG_MILLIS
            if (!granted && answeredWithoutDialog) openAppDetails()
        }

    // A quién va la imagen pedida al selector de fotos. Se guarda con el estado de la actividad: si se
    // rehace con el selector abierto, el resultado llega a la instancia nueva y esta sabe qué hacer.
    private var imageTarget: ImageTarget? = null

    // Lo que quien la pidió hace después (cerrar su hoja); solo existe en la instancia que la pidió.
    private var onImagePicked: (() -> Unit)? = null
    private val pickImageRequest =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            // Si el selector salió a media pantalla no hubo onStop que gastara la marca: se gasta aquí.
            leavingForSystemSettings = false
            val target = imageTarget
            val then = onImagePicked
            imageTarget = null
            onImagePicked = null
            if (uri != null && target != null) lifecycleScope.launch { onImageChosen(target, uri, then) }
        }

    /**
     * Lo que la actividad enseña en su barra de deshacer cuando la acción no la hizo una pantalla
     * (al volver de un selector del sistema): la barra vive en la composición, no aquí.
     */
    private var pendingUndo by mutableStateOf<Pair<String, () -> Unit>?>(null)

    // Las apps que faltan tras cargar una copia, con cómo deshacerla.
    private var backupMissing by mutableStateOf<BackupResult?>(null)

    // Los dos permisos de las órdenes de la barra se dan en pantallas del sistema: se miran al volver.
    private var writeSettingsGranted by mutableStateOf(false)
    private var dndGranted by mutableStateOf(false)

    /**
     * La carpeta de los fondos que rotan, con el selector de carpetas del sistema. Se guarda su
     * permiso para siempre y se pone la primera imagen en el acto, para ver el resultado al volver.
     */
    private val pickWallpaperFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            leavingForSystemSettings = false
            val container = (application as AlisioApp).container
            if (uri == null || !container.wallpaperRotation.keepAccess(uri)) return@registerForActivityResult
            container.settings.setWallpaperFolder(uri.toString())
            val chosen = container.settings.prefs.value?.copy(
                wallpaperFolder = uri.toString(),
                wallpaperRotation = true
            )
            // Fuera del ámbito de la actividad: poner el fondo la rehace, y eso no debe cortarlo.
            if (chosen !=
                null
            ) {
                container.launchDetached { container.wallpaperRotation.rotateIfDue(chosen, force = true) }
            }
        }

    private val wallpaperActions by lazy {
        WallpaperActions(
            rotation = (application as AlisioApp).container.wallpaperRotation,
            pickFolder = {
                leavingForSystemSettings = true
                try {
                    pickWallpaperFolder.launch(null)
                } catch (_: ActivityNotFoundException) {
                    leavingForSystemSettings = false
                    Toast.makeText(this, R.string.backup_no_picker, Toast.LENGTH_SHORT).show()
                }
            },
            next = {
                val container = (application as AlisioApp).container
                container.settings.prefs.value?.let { prefs ->
                    container.launchDetached { container.wallpaperRotation.rotateIfDue(prefs, force = true) }
                }
            }
        )
    }

    // Cada selector de la copia atiende su resultado aquí mismo, sin depender de quien lo abrió: si
    // la actividad se rehace con el selector abierto, el archivo elegido llega a la instancia nueva.
    private val createBackupFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME)) { uri ->
            // Como con el selector de fotos: si no hubo onStop que gastara la marca, se gasta aquí.
            leavingForSystemSettings = false
            if (uri != null) lifecycleScope.launch { saveBackup(uri) }
        }
    private val openBackupFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            leavingForSystemSettings = false
            if (uri != null) loadBackup(uri)
        }

    /**
     * Guardar y cargar la configuración con el selector de documentos del sistema: sin permisos, el
     * sistema da acceso al archivo elegido y a nada más. Los ajustes siguen abiertos al volver.
     */
    private val backupActions =
        BackupActions(
            save = { pickBackupFile { createBackupFile.launch(BACKUP_FILE_NAME) } },
            load = {
                pickBackupFile {
                    openBackupFile.launch(arrayOf(BACKUP_MIME, "text/plain", "application/octet-stream"))
                }
            }
        )

    /** Lanza el selector de documentos con [launch]. */
    private fun pickBackupFile(launch: () -> Unit) {
        leavingForSystemSettings = true
        try {
            launch()
        } catch (_: ActivityNotFoundException) {
            leavingForSystemSettings = false
            Toast.makeText(this, R.string.backup_no_picker, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun saveBackup(uri: Uri) {
        val saved = (application as AlisioApp).container.backup.export(uri)
        val message = if (saved) R.string.backup_saved else R.string.backup_save_failed
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Carga la copia en el ámbito de la aplicación: rehacer la actividad a mitad dejaría la
     * configuración a medias. Si faltan apps, su hoja sale antes que la barra de deshacer, que solo
     * aparece al cerrarla: abierta, la hoja la taparía y sus segundos correrían sin verse.
     */
    private fun loadBackup(uri: Uri) {
        val container = (application as AlisioApp).container
        container.launchDetached {
            val loaded = container.backup.import(uri)
            if (loaded == null) {
                Toast.makeText(applicationContext, R.string.backup_load_failed, Toast.LENGTH_SHORT).show()
                return@launchDetached
            }
            val result = BackupResult(loaded.missing) { container.launchDetached { loaded.undo() } }
            if (result.missing.isEmpty()) {
                pendingUndo = getString(R.string.backup_loaded) to result.undo
            } else {
                backupMissing = result
            }
        }
    }

    /**
     * Guarda la imagen elegida en el selector de fotos y la pone en [target], con su «Deshacer».
     * [then] es lo que hace después quien la pidió, si sigue ahí.
     */
    private suspend fun onImageChosen(target: ImageTarget, uri: Uri, then: (() -> Unit)?) {
        val container = (application as AlisioApp).container
        val image = container.customImages.import(uri)
        if (image == null) {
            Toast.makeText(this, R.string.icon_image_failed, Toast.LENGTH_SHORT).show()
            return
        }
        pendingUndo =
            when (target) {
                is ImageTarget.App -> {
                    val label =
                        container.appsRepository.apps.value
                            ?.firstOrNull { it.key.toString() == target.key }
                            ?.label
                            .orEmpty()
                    getString(R.string.undo_icon_changed, label) to
                        container.appCustom.setIcon(target.key, CustomIcon.Image(image))
                }

                is ImageTarget.Stack ->
                    getString(R.string.undo_stack_image) to container.homeConfig.setStackImage(target.position, image)
            }
        then?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val transparent = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = transparent, navigationBarStyle = transparent)
        super.onCreate(savedInstanceState)
        // Con las preferencias ya leídas, se oculta antes del primer fotograma: sin esto la barra se
        // ve un instante en cada arranque y cada vez que Android rehace la actividad.
        if ((application as AlisioApp).container.settings.prefs.value?.hideStatusBar == true) applyStatusBar(true)
        // Poner un fondo nuevo cambia los colores del sistema y Android rehace la actividad: los
        // ajustes abiertos (desde «Pasar al siguiente» o al elegir la carpeta) siguen donde estaban.
        savedInstanceState?.let { saved ->
            settingsOpen = saved.getBoolean(STATE_SETTINGS_OPEN)
            settingsPage =
                SettingsDestination.entries.firstOrNull { it.id == saved.getString(STATE_SETTINGS_PAGE) }
                    ?: SettingsDestination.COVER
            imageTarget = ImageTarget.decode(saved.getString(STATE_IMAGE_TARGET))
        }
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
            // Mientras se elige un color propio, el tema pinta el del dedo, aún sin guardar.
            val accentDraft by settings.accentDraft.collectAsStateWithLifecycle()
            val chosenAccent = accentDraft?.let { Color(it) } ?: accentColor(prefs.accent, prefs.customAccent)
            // El color del día cambia cada minuto con el sol; mientras se elige uno a mano, manda el dedo.
            val glanceData by (application as AlisioApp).container.glance.data.collectAsStateWithLifecycle()
            val minute = rememberNow()
            val accent =
                if (prefs.dayAccent && accentDraft == null) {
                    val place = prefs.weatherPlace
                    dayAccentColor(
                        day = chosenAccent,
                        nightIndex = prefs.nightAccent,
                        now = minute,
                        latitude = place?.latitude ?: glanceData.location?.latitude,
                        longitude = place?.longitude ?: glanceData.location?.longitude
                    )
                } else {
                    chosenAccent
                }
            // En todo Alisio, no solo en el Inicio: si volviera al abrir la barra, Zonas o los ajustes,
            // aparecería y se iría arriba en cada apertura. Las pantallas miden su margen con
            // *IgnoringVisibility, así que oculta no suben hasta la cámara. Sale un momento al
            // deslizar desde arriba.
            val hideBar = prefs.hideStatusBar
            LaunchedEffect(hideBar) { applyStatusBar(hideBar) }
            AlisioTheme(accent = accent, font = prefs.font) {
                // Una sola barra de deshacer para toda la actividad: la usan el Inicio, Zonas y los ajustes.
                val undo = rememberUndoState()
                // Las placas del Inicio y de su miniatura en los ajustes, con la opacidad elegida.
                CompositionLocalProvider(
                    LocalUndo provides undo,
                    LocalPickImage provides ::pickImage,
                    LocalHomePlate provides AlisioTokens.homePlate(prefs.plateOpacity)
                ) {
                    // Atrás cierra lo que haya encima del Inicio; en el Inicio no hace nada.
                    val container = (application as AlisioApp).container
                    val pendingPause by container.launchPause.pending.collectAsStateWithLifecycle()
                    val accessibilityEnabled by container.globalActions.enabled.collectAsStateWithLifecycle()
                    // El gesto mueve con el dedo la capa que va a cerrar (docs/IDEAS.md §2.2).
                    val back = rememberBackFollow()
                    FollowingBackHandler(
                        back,
                        // De arriba abajo: la pausa, el aviso de accesibilidad, los ajustes, la barra y,
                        // debajo de todos, Zonas. De una subpágina de los ajustes se vuelve a su
                        // portada: la capa es la propia subpágina.
                        layer = {
                            when {
                                pendingPause != null -> Layer.PAUSE
                                accessibilityNoticeOpen -> Layer.NOTICE
                                settingsOpen && settingsPage != SettingsDestination.COVER -> settingsPage
                                settingsOpen -> Layer.SETTINGS
                                searchOpen -> Layer.SEARCH
                                zonesOpen -> Layer.ZONES
                                else -> null
                            }
                        },
                        onBack = { layer ->
                            when (layer) {
                                Layer.PAUSE -> container.launchPause.cancel()
                                Layer.NOTICE -> accessibilityNoticeOpen = false
                                is SettingsDestination -> settingsPage = SettingsDestination.COVER
                                Layer.SETTINGS -> settingsOpen = false
                                Layer.SEARCH -> searchOpen = false
                                Layer.ZONES -> zonesOpen = false
                            }
                        }
                    )
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
                            // Con los ajustes encima se retira (sin cerrarse: atrás vuelve a ella), porque
                            // la miniatura de los ajustes es transparente y dejaba ver las islas a través.
                            AnimatedVisibility(
                                visible = zonesOpen && !settingsOpen,
                                enter = AlisioMotion.screenEnter(),
                                exit = AlisioMotion.screenExit()
                            ) {
                                BackFollowLayer(back, Layer.ZONES) {
                                    ZonesScreen(
                                        viewModel = zonesViewModel,
                                        prefs = prefs,
                                        onClose = { zonesOpen = false },
                                        // Zonas sigue debajo: atrás desde los ajustes vuelve a ella.
                                        onOpenSettings = { openSettings() },
                                        onAppMenu = { menuRequest = it.key.toString() }
                                    )
                                }
                            }
                            // One UI no anima el gesto atrás en apps de terceros (§9): la barra trae su animación.
                            AnimatedVisibility(
                                visible = searchOpen,
                                enter = AlisioMotion.screenEnter(),
                                exit = AlisioMotion.screenExit()
                            ) {
                                key(searchSession) {
                                    BackFollowLayer(back, Layer.SEARCH) {
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
                                val calendarGranted by container.glance.calendarGranted.collectAsStateWithLifecycle()
                                val locationGranted by container.glance.locationGranted.collectAsStateWithLifecycle()
                                val zonesState by zonesViewModel.state.collectAsStateWithLifecycle()
                                BackFollowLayer(back, Layer.SETTINGS) {
                                    SettingsScreen(
                                        page = settingsPage,
                                        back = back,
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
                                                onContacts = {
                                                    if (contactsGranted) openAppDetails() else askContacts()
                                                },
                                                calendar = calendarGranted,
                                                onCalendar = {
                                                    if (calendarGranted) {
                                                        openAppDetails()
                                                    } else {
                                                        askGlancePermission(Manifest.permission.READ_CALENDAR)
                                                    }
                                                },
                                                location = locationGranted,
                                                onLocation = {
                                                    if (locationGranted) {
                                                        openAppDetails()
                                                    } else {
                                                        askGlancePermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                                                    }
                                                },
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
                                                },
                                                writeSettings = writeSettingsGranted,
                                                onWriteSettings = {
                                                    openSystemScreen(container.phoneControls.writeSettingsIntent())
                                                },
                                                doNotDisturb = dndGranted,
                                                onDoNotDisturb = {
                                                    openSystemScreen(container.phoneControls.dndAccessIntent())
                                                }
                                            ),
                                        wallpaper = wallpaperActions,
                                        contacts = contacts,
                                        onWallpaper = ::openWallpaperPicker,
                                        onPreviewPause = { container.launchPause.preview(it, prefs.pauseSeconds) },
                                        backup = backupActions
                                    )
                                }
                            }
                            AnimatedVisibility(
                                visible = accessibilityNoticeOpen,
                                enter = AlisioMotion.screenEnter(),
                                exit = AlisioMotion.screenExit()
                            ) {
                                BackFollowLayer(back, Layer.NOTICE) {
                                    AccessibilityNotice(
                                        onContinue = ::openAccessibilitySettings,
                                        onDismiss = { accessibilityNoticeOpen = false }
                                    )
                                }
                            }
                            // La pausa va encima de todo: una app se abre desde el Inicio, la barra o Zonas.
                            // Se guarda la última para que su salida animada siga teniendo qué pintar.
                            var shownPause by remember { mutableStateOf<PendingLaunch?>(null) }
                            LaunchedEffect(pendingPause) { pendingPause?.let { shownPause = it } }
                            AnimatedVisibility(
                                visible = pendingPause != null,
                                // Entra como las demás pantallas que tapan el Inicio: subiendo 12 dp.
                                enter = AlisioMotion.screenEnter(),
                                exit = AlisioMotion.screenExit()
                            ) {
                                (pendingPause ?: shownPause)?.let { pause ->
                                    // Lo de hoy según el sistema; sin acceso a uso, o apagado, no hay línea.
                                    val today by produceState<AppToday?>(null, pause, prefs.pauseStats) {
                                        value =
                                            if (prefs.pauseStats) {
                                                container.usageAccess.todayVisits(
                                                    pause.packageName
                                                )
                                            } else {
                                                null
                                            }
                                    }
                                    BackFollowLayer(back, Layer.PAUSE) {
                                        PauseScreen(
                                            launch = pause,
                                            today = today,
                                            onOpen = {
                                                if (!container.launchPause.confirm()) {
                                                    val message = getString(R.string.app_launch_failed, pause.label)
                                                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT)
                                                        .show()
                                                }
                                            },
                                            onCancel = container.launchPause::cancel
                                        )
                                    }
                                }
                            }
                            val roleLostMessage = stringResource(R.string.home_role_lost)
                            val roleBack = stringResource(R.string.home_role_back)
                            LaunchedEffect(homeRoleLost) {
                                if (homeRoleLost) {
                                    homeRoleLost = false
                                    undo.show(roleLostMessage, roleBack) { requestHomeRole() }
                                }
                            }
                            LaunchedEffect(pendingUndo) {
                                pendingUndo?.let { (message, action) ->
                                    pendingUndo = null
                                    undo.show(message, action)
                                }
                            }
                            backupMissing?.let { result ->
                                val loadedMessage = stringResource(R.string.backup_loaded)
                                BackupMissingSheet(
                                    missing = result.missing,
                                    onUndo = {
                                        backupMissing = null
                                        result.undo()
                                    },
                                    // Con la hoja cerrada, la barra ya se ve: sus segundos empiezan ahora.
                                    onDismiss = {
                                        backupMissing = null
                                        pendingUndo = loadedMessage to result.undo
                                    }
                                )
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
        TouchTrace.returned("onResume")
        val workStart = SystemClock.uptimeMillis()
        homeViewModel.refreshNow()
        // El permiso de contactos también se puede cambiar desde los ajustes del sistema.
        (application as AlisioApp).container.contacts.onPermissionChanged()
        (application as AlisioApp).container.bluetoothAudio.onPermissionChanged()
        // El acceso a notificaciones se concede en los ajustes del sistema: al volver ya puede estar.
        (application as AlisioApp).container.notificationAccess.refresh()
        (application as AlisioApp).container.mediaSessions.refresh()
        // Lo que está en curso, por si «En curso» se encendió o algo empezó mientras Alisio no se veía.
        (application as AlisioApp).container.notificationAccess.refreshLive()
        // Lo mismo con el acceso a uso.
        (application as AlisioApp).container.usageAccess.refresh()
        // Y con el servicio de accesibilidad, que se activa y se desactiva en los ajustes del sistema.
        (application as AlisioApp).container.globalActions.refresh()
        // La línea bajo la hora: calendario, uso de hoy, alarma y, si ha caducado, el tiempo.
        (application as AlisioApp).container.glance.refresh()
        val container = (application as AlisioApp).container
        writeSettingsGranted = container.phoneControls.canWriteSettings()
        dndGranted = container.phoneControls.dndGranted()
        val holdsRole = holdsHomeRole()
        if (holdsRole && !isDefaultHome) homeViewModel.onHomeRoleGranted()
        // One UI puede dar el papel de inicio a otra app (al instalar un launcher, tras una
        // actualización): se ofrece recuperarlo en el sitio, sin ir a buscarlo a los ajustes.
        if (!holdsRole && isDefaultHome) homeRoleLost = true
        isDefaultHome = holdsRole
        TouchTrace.mark("trabajo de onResume", took = SystemClock.uptimeMillis() - workStart)
        // El primer fotograma tras volver: hasta él, un toque no tiene qué tocar.
        Choreographer.getInstance().postFrameCallback { TouchTrace.mark("primer fotograma tras volver") }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) TouchTrace.touched()
        // Compose entrega el cancelar como un soltar: el Inicio necesita saber que fue el sistema.
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) SystemCancel.mark(event.eventTime)
        return super.dispatchTouchEvent(event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SETTINGS_OPEN, settingsOpen)
        outState.putString(STATE_SETTINGS_PAGE, settingsPage.id)
        outState.putString(STATE_IMAGE_TARGET, imageTarget?.encode())
    }

    override fun onStart() {
        super.onStart()
        // La batería, la alarma y el calendario solo se escuchan con el Inicio a la vista (§5).
        (application as AlisioApp).container.glance.start()
    }

    override fun onStop() {
        super.onStop()
        (application as AlisioApp).container.glance.stop()
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
        // Los fondos que rotan cambian una vez al día al salir del Inicio, sin trabajo de fondo. Al
        // volver no: poner un fondo cambia los colores del sistema y Android rehace la actividad, y
        // eso cerraría la barra o un Trazo recién empezados. En el ámbito de la aplicación, para que
        // la parada no corte el fondo a medio poner.
        val container = (application as AlisioApp).container
        container.settings.prefs.value?.let { prefs ->
            if (prefs.wallpaperRotation) container.launchDetached { container.wallpaperRotation.rotateIfDue(prefs) }
        }
    }

    /** Pulsar «inicio» estando ya en Alisio llega aquí: se cierran la barra y las hojas. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Desde otra app llega también onResume, que es quien marca la vuelta.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) TouchTrace.returned("inicio")
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

    /**
     * El selector de fotos del sistema, solo imágenes. No pide permisos: el sistema da acceso a la
     * elegida y nada más. Al volver siguen abiertos los ajustes o Zonas, como al volver de los ajustes
     * del sistema.
     */
    private fun pickImage(target: ImageTarget, onPicked: () -> Unit) {
        imageTarget = target
        onImagePicked = onPicked
        leavingForSystemSettings = true
        try {
            pickImageRequest.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: ActivityNotFoundException) {
            leavingForSystemSettings = false
            imageTarget = null
            onImagePicked = null
            Toast.makeText(this, R.string.icon_image_failed, Toast.LENGTH_SHORT).show()
        }
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

    /** Calendario o ubicación aproximada, uno cada vez: cada uno lo pide su pieza de la línea. */
    private fun askGlancePermission(permission: String) {
        // Igual que en [askContacts]: un segundo toque no debe parecer un «denegado para siempre».
        if (glanceRequestPending) return
        glanceRequestPending = true
        glanceAskedAt = SystemClock.uptimeMillis()
        requestGlancePermission.launch(permission)
    }

    /** Una pantalla del sistema donde se concede algo; si este sistema no la tiene, la ficha de Alisio. */
    private fun openSystemScreen(intent: Intent) {
        leavingForSystemSettings = true
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            openAppDetails()
        }
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
        AlisioHaptics.limit(window.decorView)
        Toast.makeText(this, R.string.gesture_service_silent, Toast.LENGTH_SHORT).show()
    }

    private fun applyStatusBar(hidden: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hidden) hide(WindowInsetsCompat.Type.statusBars()) else show(WindowInsetsCompat.Type.statusBars())
        }
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
