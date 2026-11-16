package dev.jesusmolano.alisio

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.room.Room
import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.data.accessibility.GlobalActions
import dev.jesusmolano.alisio.data.apps.AppCustomRepository
import dev.jesusmolano.alisio.data.apps.AppsRepository
import dev.jesusmolano.alisio.data.apps.CustomImages
import dev.jesusmolano.alisio.data.apps.IconPackCatalog
import dev.jesusmolano.alisio.data.apps.SystemApps
import dev.jesusmolano.alisio.data.backup.BackupRepository
import dev.jesusmolano.alisio.data.bluetooth.BluetoothAudio
import dev.jesusmolano.alisio.data.contacts.ContactsRepository
import dev.jesusmolano.alisio.data.db.AlisioDatabase
import dev.jesusmolano.alisio.data.db.MIGRATION_2_3
import dev.jesusmolano.alisio.data.db.MIGRATION_3_4
import dev.jesusmolano.alisio.data.db.MIGRATION_4_5
import dev.jesusmolano.alisio.data.db.MIGRATION_5_6
import dev.jesusmolano.alisio.data.db.MIGRATION_6_7
import dev.jesusmolano.alisio.data.db.MIGRATION_7_8
import dev.jesusmolano.alisio.data.db.MIGRATION_8_9
import dev.jesusmolano.alisio.data.glance.GlanceRepository
import dev.jesusmolano.alisio.data.history.LaunchHistory
import dev.jesusmolano.alisio.data.home.HomeConfigRepository
import dev.jesusmolano.alisio.data.home.IconGestureRepository
import dev.jesusmolano.alisio.data.media.MediaSessions
import dev.jesusmolano.alisio.data.media.NotificationAccess
import dev.jesusmolano.alisio.data.notes.NotesRepository
import dev.jesusmolano.alisio.data.now.ForgottenRepository
import dev.jesusmolano.alisio.data.now.NowFeedbackRepository
import dev.jesusmolano.alisio.data.pause.LaunchPause
import dev.jesusmolano.alisio.data.settings.AlisioSettings
import dev.jesusmolano.alisio.data.system.PhoneControls
import dev.jesusmolano.alisio.data.usage.UsageAccess
import dev.jesusmolano.alisio.data.wallpaper.WallpaperRotation
import dev.jesusmolano.alisio.data.zones.ZonesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AlisioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        StartupTrace.mark("proceso listo")
        container = AppContainer(this)
        StartupTrace.mark("contenedor creado")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        container.onConfigurationChanged(newConfig)
    }
}

/** Inyección de dependencias manual: todo lo que vive lo que vive el proceso. */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val database =
        Room.databaseBuilder(context, AlisioDatabase::class.java, "alisio.db")
            .addMigrations(
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9
            )
            // Si falta una migración se empieza de cero: es de uso personal y aún en desarrollo.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    private val _locales = MutableStateFlow(context.resources.configuration.locales.toLanguageTags())

    /**
     * Idiomas del sistema, por orden de preferencia. El proceso sobrevive a un cambio de idioma, así
     * que quien guarde textos ya resueltos (los nombres de los ajustes en el índice de la barra) los
     * vuelve a pedir cuando esto cambia.
     */
    val locales: StateFlow<String> = _locales.asStateFlow()

    val clock = Clock.System
    val settings = AlisioSettings(context, appScope)
    val customImages = CustomImages(context)
    val iconPacks = IconPackCatalog(context)
    val appCustom = AppCustomRepository(database.appCustom(), customImages, appScope)
    val appsRepository =
        AppsRepository(context, appScope, settings.iconStyle, appCustom.all, customImages, iconPacks)
    val bluetoothAudio = BluetoothAudio(context, clock)
    val launchHistory = LaunchHistory(database.launchEvents(), clock, appScope) { bluetoothAudio.connected.value }
    val homeConfig = HomeConfigRepository(database.homeConfig(), appScope, settings.dockSlots)
    val iconGestures = IconGestureRepository(database.iconGestures(), appScope)
    val usageAccess = UsageAccess(context, clock)
    val glance = GlanceRepository(context, settings.prefs, usageAccess, clock, appScope)
    val contacts = ContactsRepository(context, appScope)
    val nowFeedback = NowFeedbackRepository(database.suggestionFeedback(), clock, appScope)
    val forgotten = ForgottenRepository(database.forgotten(), clock, appScope)
    val backup = BackupRepository(context, database.backup(), settings, customImages, appsRepository)
    val notificationAccess = NotificationAccess(context)
    val mediaSessions = MediaSessions(context, notificationAccess, settings.playerEnabled, appScope)
    val zones = ZonesRepository(database.zones(), appScope)
    val systemApps = SystemApps(context)
    val launchPause = LaunchPause(settings.prefs)
    val globalActions = GlobalActions(context)
    val notes = NotesRepository(database.homeNotes(), clock, appScope)
    val phoneControls = PhoneControls(context)
    val wallpaperRotation = WallpaperRotation(context)

    init {
        // Con las notas apagadas, Alisio no sale en la hoja de compartir de las demás apps: no estorba.
        val share = ComponentName(context, ShareNoteActivity::class.java)
        appScope.launch {
            settings.prefs.filterNotNull().map { it.homeNotes }.distinctUntilChanged().collect { on ->
                context.packageManager.setComponentEnabledSetting(
                    share,
                    if (on) {
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    },
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }

    /**
     * Para lo que no debe cortarse aunque la actividad se pare o se rehaga: poner un fondo, aplicar
     * una copia o deshacerla. El ámbito de la actividad lo cancelaría a medias.
     */
    fun launchDetached(block: suspend CoroutineScope.() -> Unit) = appScope.launch(block = block)

    /**
     * Las apps guardan su nombre tal como se leyó; tras un cambio de idioma hay que releerlas. Los
     * demás cambios de configuración (giro, tamaño de letra) no tocan ningún texto guardado.
     */
    fun onConfigurationChanged(config: Configuration) {
        val locales = config.locales.toLanguageTags()
        if (locales == _locales.value) return
        _locales.value = locales
        appsRepository.reload()
    }
}
