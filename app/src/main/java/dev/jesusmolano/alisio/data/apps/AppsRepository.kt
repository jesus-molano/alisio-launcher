package dev.jesusmolano.alisio.data.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Apps con icono de inicio de todos los perfiles, al día con instalaciones y desinstalaciones. */
class AppsRepository(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val iconSizePx = (ICON_SIZE_DP * appContext.resources.displayMetrics.density).toInt()

    /** `null` mientras se carga la primera vez. */
    private val _apps = MutableStateFlow<List<AppEntry>?>(null)
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()

    private var reloadJob: Job? = null

    private val callback =
        object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) = reload()

            override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()

            override fun onPackageChanged(packageName: String, user: UserHandle) = reload()

            override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
                reload()

            override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
                reload()
        }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    /** Vuelve a leer la lista entera; una recarga nueva cancela la que esté en curso. */
    fun reload() {
        reloadJob?.cancel()
        reloadJob =
            scope.launch {
                _apps.value = withContext(Dispatchers.IO) { load() }
            }
    }

    /** Abre la app. Devuelve `false` si ya no existe o el sistema no deja abrirla. */
    fun launch(app: AppEntry, sourceBounds: Rect?, options: Bundle?): Boolean = try {
        launcherApps.startMainActivity(app.component, app.user, sourceBounds, options)
        true
    } catch (e: RuntimeException) {
        // SecurityException o ActivityNotFoundException: la app ha cambiado bajo nuestros pies.
        Log.w(TAG, "No se ha podido abrir ${app.key}", e)
        reload()
        false
    }

    private fun load(): List<AppEntry> = launcherApps.profiles
        .flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user).map { info ->
                AppEntry(
                    key = AppKey(info.componentName.packageName, info.componentName.className, serial),
                    label = info.label.toString(),
                    component = info.componentName,
                    user = user,
                    icon = renderIcon(info.getIcon(0), iconSizePx, ICON_TINT, ICON_SURFACE)
                )
            }
        }
        .filterNot { it.component.packageName == appContext.packageName }
        .sortedAlphabetically(label = { it.label }, key = { it.key })

    private companion object {
        const val TAG = "AppsRepository"
        const val ICON_SIZE_DP = 48

        // Tokens «Texto» y «Superficie elevada» de docs/PLAN.md §6.
        const val ICON_TINT = 0xFFF2F3F5.toInt()
        const val ICON_SURFACE = 0xFF1B1E22.toInt()
    }
}
