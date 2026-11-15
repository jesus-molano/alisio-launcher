package dev.jesusmolano.alisio

import android.app.Application
import android.content.Context
import dev.jesusmolano.alisio.data.apps.AppsRepository
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

    val appsRepository = AppsRepository(context, appScope)
}
