package dev.jesusmolano.alisio

import android.app.Application
import android.content.Context
import androidx.room.Room
import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.data.apps.AppsRepository
import dev.jesusmolano.alisio.data.bluetooth.BluetoothAudio
import dev.jesusmolano.alisio.data.contacts.ContactsRepository
import dev.jesusmolano.alisio.data.db.AlisioDatabase
import dev.jesusmolano.alisio.data.db.MIGRATION_2_3
import dev.jesusmolano.alisio.data.db.MIGRATION_3_4
import dev.jesusmolano.alisio.data.db.MIGRATION_4_5
import dev.jesusmolano.alisio.data.history.LaunchHistory
import dev.jesusmolano.alisio.data.home.HomeConfigRepository
import dev.jesusmolano.alisio.data.home.IconGestureRepository
import dev.jesusmolano.alisio.data.media.MediaSessions
import dev.jesusmolano.alisio.data.media.NotificationAccess
import dev.jesusmolano.alisio.data.now.NowFeedbackRepository
import dev.jesusmolano.alisio.data.settings.AlisioSettings
import dev.jesusmolano.alisio.data.usage.UsageAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AlisioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Inyección de dependencias manual: todo lo que vive lo que vive el proceso. */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val database =
        Room.databaseBuilder(context, AlisioDatabase::class.java, "alisio.db")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            // Si falta una migración se empieza de cero: es de uso personal y aún en desarrollo.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    val clock = Clock.System
    val settings = AlisioSettings(context, appScope)
    val appsRepository = AppsRepository(context, appScope, settings.iconStyle)
    val bluetoothAudio = BluetoothAudio(context, clock)
    val launchHistory = LaunchHistory(database.launchEvents(), clock, appScope) { bluetoothAudio.connected.value }
    val homeConfig = HomeConfigRepository(database.homeConfig(), appScope)
    val iconGestures = IconGestureRepository(database.iconGestures(), appScope)
    val usageAccess = UsageAccess(context, clock)
    val contacts = ContactsRepository(context, appScope)
    val nowFeedback = NowFeedbackRepository(database.suggestionFeedback(), clock, appScope)
    val notificationAccess = NotificationAccess(context)
    val mediaSessions = MediaSessions(context, notificationAccess, settings.playerEnabled, appScope)
}
