package dev.jesusmolano.alisio.data.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.core.graphics.createBitmap
import dev.jesusmolano.alisio.core.icons.IconStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Acceso directo de una app (§5.2). Se muestra con el icono de su app.
 *
 * @param isConversation la app lo publica como una conversación con alguien (un chat de WhatsApp),
 *   no como una acción suya: la barra lo trata como una persona.
 */
class ShortcutEntry(
    val key: String,
    val label: String,
    val app: AppEntry,
    val info: ShortcutInfo,
    val isConversation: Boolean
)

/**
 * Apps con icono de inicio de todos los perfiles y sus accesos directos, al día con instalaciones,
 * desinstalaciones y cambios de accesos directos.
 */
class AppsRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val iconStyle: StateFlow<IconStyle?>
) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val iconSizePx = (ICON_SIZE_DP * appContext.resources.displayMetrics.density).toInt()

    /** `null` mientras se carga la primera vez. */
    private val _apps = MutableStateFlow<List<AppEntry>?>(null)
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()

    private val _shortcuts = MutableStateFlow<List<ShortcutEntry>>(emptyList())
    val shortcuts: StateFlow<List<ShortcutEntry>> = _shortcuts.asStateFlow()

    private var reloadJob: Job? = null
    private var shortcutsJob: Job? = null

    private val callback =
        object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) = reload()

            override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()

            override fun onPackageChanged(packageName: String, user: UserHandle) = reload()

            override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
                reload()

            override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) =
                reload()

            override fun onShortcutsChanged(packageName: String, shortcuts: List<ShortcutInfo>, user: UserHandle) =
                reloadShortcuts()
        }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        // La primera carga espera al estilo guardado; cada cambio de estilo vuelve a dibujar los iconos.
        scope.launch { iconStyle.filterNotNull().collect { reload() } }
    }

    /** Vuelve a leer apps y accesos directos; una recarga nueva cancela la que esté en curso. */
    fun reload() {
        shortcutsJob?.cancel()
        reloadJob?.cancel()
        reloadJob =
            scope.launch {
                val (apps, shortcuts) =
                    withContext(Dispatchers.IO) {
                        val apps = loadApps()
                        apps to loadShortcuts(apps)
                    }
                _apps.value = apps
                _shortcuts.value = shortcuts
            }
    }

    /**
     * Solo los accesos directos: cambian a menudo (cada mensaje de una app de chat) y releer las
     * apps volvería a dibujar todos los iconos y cortaría un Trazo en curso.
     */
    private fun reloadShortcuts() {
        // Una recarga completa en curso ya traerá los accesos directos, y con los iconos nuevos.
        if (reloadJob?.isActive == true) return
        val apps = _apps.value ?: return
        shortcutsJob?.cancel()
        shortcutsJob = scope.launch { _shortcuts.value = withContext(Dispatchers.IO) { loadShortcuts(apps) } }
    }

    /** Abre la app. Devuelve `false` si ya no existe o el sistema no deja abrirla. */
    fun launch(app: AppEntry): Boolean = try {
        // Sin opciones de animación: One UI 9 las descarta (docs/PLAN.md §11).
        launcherApps.startMainActivity(app.component, app.user, null, null)
        true
    } catch (e: RuntimeException) {
        // SecurityException o ActivityNotFoundException: la app ha cambiado bajo nuestros pies.
        Log.w(TAG, "No se ha podido abrir ${app.key}", e)
        reload()
        false
    }

    fun launch(shortcut: ShortcutEntry): Boolean = try {
        launcherApps.startShortcut(shortcut.info, null, null)
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "No se ha podido abrir ${shortcut.key}", e)
        reload()
        false
    }

    private fun loadApps(): List<AppEntry> {
        val style = iconStyle.value ?: IconStyle.Color
        val pack = (style as? IconStyle.Pack)?.let { IconPack.load(appContext, it.packageName) }
        return loadApps(style, pack)
    }

    private fun loadApps(style: IconStyle, pack: IconPack?): List<AppEntry> = launcherApps.profiles
        .flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user).map { info ->
                AppEntry(
                    key = AppKey(info.componentName.packageName, info.componentName.className, serial),
                    label = info.label.toString(),
                    component = info.componentName,
                    user = user,
                    icon = safeIcon(info.getIcon(0), pack?.iconFor(info.componentName), style)
                )
            }
        }
        .filterNot { it.component.packageName == appContext.packageName }
        .sortedAlphabetically(label = { it.label }, key = { it.key })

    /**
     * Un dibujo defectuoso de un paquete de iconos (enorme, o que falla al pintarse) no puede tumbar
     * la carga: el estilo está guardado y Alisio se cerraría en cada arranque. Esa app usa su icono a color.
     */
    private fun safeIcon(drawable: Drawable, packIcon: Drawable?, style: IconStyle): Bitmap =
        tryRender(drawable, packIcon, style)
            ?: tryRender(drawable, null, IconStyle.Color)
            // Ni el icono de la propia app se deja dibujar: se queda en blanco antes que cerrar Alisio.
            ?: createBitmap(iconSizePx, iconSizePx)

    private fun tryRender(drawable: Drawable, packIcon: Drawable?, style: IconStyle): Bitmap? = try {
        renderIcon(drawable, packIcon, style, iconSizePx, ICON_TINT, ICON_SURFACE)
    } catch (e: RuntimeException) {
        Log.w(TAG, "Icono imposible de dibujar con $style", e)
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Icono demasiado grande con $style", e)
        null
    }

    /** Solo el launcher activo puede leerlos (`hasShortcutHostPermission`); si no, lista vacía. */
    private fun loadShortcuts(apps: List<AppEntry>): List<ShortcutEntry> {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        // Con varias actividades de inicio en un paquete, el acceso directo se asocia a la primera.
        val appByPackage = apps.groupBy { it.component.packageName to it.key.userSerial }.mapValues { it.value.first() }
        val query =
            ShortcutQuery().setQueryFlags(
                ShortcutQuery.FLAG_MATCH_DYNAMIC or ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_PINNED
            )
        return launcherApps.profiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            val infos =
                try {
                    launcherApps.getShortcuts(query, user).orEmpty()
                } catch (e: RuntimeException) {
                    // Perfil bloqueado o permiso retirado entre la comprobación y la consulta.
                    Log.w(TAG, "No se han podido leer los accesos directos de $user", e)
                    emptyList()
                }
            infos.mapNotNull { info ->
                val app = appByPackage[info.`package` to serial] ?: return@mapNotNull null
                val label = (info.shortLabel ?: info.longLabel)?.toString()?.takeIf { it.isNotBlank() }
                if (!info.isEnabled || label == null) return@mapNotNull null
                ShortcutEntry(
                    key = "shortcut:${info.`package`}/${info.id}#$serial",
                    label = label,
                    app = app,
                    info = info,
                    // Las apps de mensajería publican sus conversaciones con la categoría de conversación o
                    // como destinos para compartir (WhatsApp, Discord y Mensajes usan `…SHARE_TARGET…`).
                    isConversation =
                        info.categories.orEmpty().any {
                            it == CONVERSATION_CATEGORY || it.contains(SHARE_TARGET_MARK, ignoreCase = true)
                        }
                )
            }
        }
    }

    private companion object {
        const val TAG = "AppsRepository"
        const val CONVERSATION_CATEGORY = "android.shortcut.conversation"
        const val SHARE_TARGET_MARK = "SHARE_TARGET"
        const val ICON_SIZE_DP = 52

        // Tokens «Texto» y «Superficie elevada» de docs/PLAN.md §6.
        const val ICON_TINT = 0xFFF2F3F5.toInt()
        const val ICON_SURFACE = 0xFF1B1E22.toInt()
    }
}
