package dev.jesusmolano.alisio

import android.app.Application
import android.content.Context
import androidx.room.Room
import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.data.apps.AppsRepository
import dev.jesusmolano.alisio.data.contacts.ContactsRepository
import dev.jesusmolano.alisio.data.db.AlisioDatabase
import dev.jesusmolano.alisio.data.db.MIGRATION_2_3
import dev.jesusmolano.alisio.data.history.LaunchHistory
import dev.jesusmolano.alisio.data.home.HomeConfigRepository
import dev.jesusmolano.alisio.data.settings.AlisioSettings
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
            .addMigrations(MIGRATION_2_3)
            // Si falta una migración se empieza de cero: es de uso personal y aún en desarrollo.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    val clock = Clock.System
    val settings = AlisioSettings(context, appScope)
    val appsRepository = AppsRepository(context, appScope, settings.iconStyle)
    val launchHistory = LaunchHistory(database.launchEvents(), clock, appScope)
    val homeConfig = HomeConfigRepository(database.homeConfig(), appScope)
    val contacts = ContactsRepository(context, appScope)
}
