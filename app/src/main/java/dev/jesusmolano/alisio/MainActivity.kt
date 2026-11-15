package dev.jesusmolano.alisio

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesusmolano.alisio.data.settings.Prefs
import dev.jesusmolano.alisio.feature.home.HomeScreen
import dev.jesusmolano.alisio.feature.home.HomeViewModel
import dev.jesusmolano.alisio.feature.home.dockApps
import dev.jesusmolano.alisio.feature.onboarding.OnboardingScreen
import dev.jesusmolano.alisio.feature.search.SearchScreen
import dev.jesusmolano.alisio.feature.search.SearchViewModel
import dev.jesusmolano.alisio.feature.settings.SettingsScreen
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

    private var isDefaultHome by mutableStateOf(false)
    private var onboardingSkipped by mutableStateOf(false)
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

    // Se sale a una pantalla del sistema a conceder algo: al volver, los ajustes deben seguir abiertos
    // para ver el resultado en la fila que se tocó.
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

        setContent {
            val settings = (application as AlisioApp).container.settings
            val loadedPrefs by settings.prefs.collectAsStateWithLifecycle()
            // Hasta leer las preferencias no se pinta nada: si no, saldrían un instante el acento y el
            // velo por defecto antes que los elegidos.
            val prefs = loadedPrefs ?: return@setContent
            AlisioTheme(accent = prefs.accent) {
                // Atrás cierra lo que haya encima del Inicio; en el Inicio no hace nada.
                BackHandler {
                    if (settingsOpen) settingsOpen = false else searchOpen = false
                }
                if (isDefaultHome || onboardingSkipped) {
                    Box(Modifier.fillMaxSize()) {
                        HomeScreen(
                            viewModel = homeViewModel,
                            homePresses = homePresses,
                            // Con algo encima, Trazo se retira.
                            searchOpen = searchOpen || settingsOpen,
                            prefs = prefs,
                            onOpenSettings = { settingsOpen = true },
                            onGrantUsage = ::openUsageAccess,
                            onOpenSearch = {
                                searchSession++
                                searchOpen = true
                            }
                        )
                        // One UI no anima el gesto atrás en apps de terceros (§9): la barra trae su animación.
                        AnimatedVisibility(
                            visible = searchOpen,
                            enter = fadeIn() + slideInVertically { it / 8 },
                            exit = fadeOut() + slideOutVertically { it / 8 }
                        ) {
                            key(searchSession) {
                                SearchScreen(
                                    viewModel = searchViewModel,
                                    onAskContacts = ::askContacts,
                                    onClose = { searchOpen = false },
                                    prefs = prefs,
                                    onOpenSettings = {
                                        searchOpen = false
                                        settingsOpen = true
                                    }
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = settingsOpen,
                            enter = fadeIn() + slideInVertically { it / 8 },
                            exit = fadeOut() + slideOutVertically { it / 8 }
                        ) {
                            val apps by homeViewModel.apps.collectAsStateWithLifecycle()
                            val contacts = (application as AlisioApp).container.contacts
                            val contactsGranted by contacts.granted.collectAsStateWithLifecycle()
                            val bluetooth = (application as AlisioApp).container.bluetoothAudio
                            val bluetoothGranted by bluetooth.granted.collectAsStateWithLifecycle()
                            val bluetoothConnected by bluetooth.connected.collectAsStateWithLifecycle()
                            val notificationAccess = (application as AlisioApp).container.notificationAccess
                            val notificationsGranted by notificationAccess.granted.collectAsStateWithLifecycle()
                            val dock by homeViewModel.dock.collectAsStateWithLifecycle()
                            val usageGranted by homeViewModel.usageGranted.collectAsStateWithLifecycle()
                            SettingsScreen(
                                prefs = prefs,
                                settings = settings,
                                apps = apps.orEmpty(),
                                contactsGranted = contactsGranted,
                                onContactsClick = { if (contactsGranted) openAppDetails() else askContacts() },
                                bluetoothGranted = bluetoothGranted,
                                bluetoothDevice = bluetoothConnected?.name,
                                onBluetoothClick = { if (bluetoothGranted) openAppDetails() else askBluetooth() },
                                notificationsGranted = notificationsGranted,
                                onNotificationsClick = ::openNotificationAccess,
                                dockApps = dock.orEmpty().dockApps(),
                                usageGranted = usageGranted,
                                onUsageClick = ::openUsageAccess
                            )
                        }
                    }
                } else {
                    // El onboarding va sobre fondo opaco: la ventana deja ver el fondo de pantalla.
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        OnboardingScreen(
                            onSetDefault = ::requestHomeRole,
                            onSkip = { onboardingSkipped = true }
                        )
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
        val holdsRole = holdsHomeRole()
        if (holdsRole && !isDefaultHome) homeViewModel.onHomeRoleGranted()
        isDefaultHome = holdsRole
    }

    override fun onStop() {
        super.onStop()
        // Al volver de otra app el Inicio aparece limpio, sin la barra ni los ajustes a medias.
        searchOpen = false
        if (!leavingForSystemSettings) settingsOpen = false
        // Vale para una sola salida: si después se va a otra app, el Inicio vuelve limpio.
        leavingForSystemSettings = false
    }

    /** Pulsar «inicio» estando ya en Alisio llega aquí: se cierran la barra y las hojas. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            searchOpen = false
            settingsOpen = false
            homePresses++
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

    private fun holdsHomeRole(): Boolean = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

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
