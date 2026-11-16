package dev.jesusmolano.alisio.data.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.jesusmolano.alisio.StartupTrace
import dev.jesusmolano.alisio.core.icons.CustomIcon
import dev.jesusmolano.alisio.core.icons.IconStyle
import dev.jesusmolano.alisio.data.db.AppCustom
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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

/** Clave de un acceso directo en el índice, en el historial y en el destino secundario de un icono. */
fun shortcutKey(packageName: String, id: String, userSerial: Long): String = "shortcut:$packageName/$id#$userSerial"

/**
 * Apps con icono de inicio de todos los perfiles y sus accesos directos, al día con instalaciones,
 * desinstalaciones y cambios de accesos directos.
 */
class AppsRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val iconStyle: StateFlow<IconStyle?>,
    private val custom: StateFlow<Map<String, AppCustom>?>,
    private val images: CustomImages,
    private val packs: IconPackCatalog
) {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val iconSizePx = (ICON_SIZE_DP * appContext.resources.displayMetrics.density).toInt()

    /** `null` mientras se carga la primera vez. Con los nombres e iconos propios ya puestos. */
    private val _apps = MutableStateFlow<List<AppEntry>?>(null)
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()

    private val _shortcuts = MutableStateFlow<List<ShortcutEntry>>(emptyList())
    val shortcuts: StateFlow<List<ShortcutEntry>> = _shortcuts.asStateFlow()

    // Lo leído del sistema, tal cual (con el estilo de iconos elegido). Lo propio de cada app se pone
    // encima en [applyCustom]: cambiar un nombre o un icono no vuelve a leer ni a dibujar todas las apps.
    private val read = MutableStateFlow<Pair<List<AppEntry>, List<ShortcutEntry>>?>(null)

    // Lo último publicado: si solo cambian los accesos directos, las apps siguen siendo la misma lista
    // y nada que dependa de ella (un Trazo en curso) se reinicia.
    private var applied: Triple<List<AppEntry>, Map<String, AppCustom>, List<AppEntry>>? = null

    // Iconos de las vistas previas de estilo, por estilo y app. Se leen y se escriben desde varios hilos.
    private val previewIcons = ConcurrentHashMap<String, Bitmap>()

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

            // Bienestar digital pone y quita la pausa de una app: su icono cambia de aspecto.
            override fun onPackagesSuspended(packageNames: Array<String>, user: UserHandle) = reload()

            override fun onPackagesUnsuspended(packageNames: Array<String>, user: UserHandle) = reload()
        }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        // La primera carga espera al estilo guardado; cada cambio de estilo vuelve a dibujar los iconos.
        scope.launch { iconStyle.filterNotNull().collect { reload() } }
        // Lo leído y lo propio de cada app se juntan aquí; un cambio nuevo cancela el que se estaba
        // aplicando. La primera publicación espera también a lo propio: si no, saldrían un instante
        // los nombres y los iconos sin cambiar.
        scope.launch {
            combine(read.filterNotNull(), custom.filterNotNull(), ::Pair).collectLatest { (loaded, own) ->
                val (apps, shortcuts) = withContext(Dispatchers.IO) { applyCustom(loaded.first, loaded.second, own) }
                if (_apps.value == null) StartupTrace.mark("apps e iconos listos (${apps.size})")
                _apps.value = apps
                _shortcuts.value = shortcuts
            }
        }
    }

    /** Vuelve a leer apps y accesos directos; una recarga nueva cancela la que esté en curso. */
    fun reload() {
        shortcutsJob?.cancel()
        reloadJob?.cancel()
        reloadJob =
            scope.launch {
                val loaded =
                    withContext(Dispatchers.IO) {
                        // Un paquete de iconos instalado o quitado cambia lo que hay que ofrecer.
                        packs.forget()
                        val apps = loadApps()
                        apps to loadShortcuts(apps)
                    }
                // Una app actualizada puede traer otro icono: las vistas previas se vuelven a dibujar.
                previewIcons.clear()
                read.value = loaded
            }
    }

    /**
     * Solo los accesos directos: cambian a menudo (cada mensaje de una app de chat) y releer las
     * apps volvería a dibujar todos los iconos y cortaría un Trazo en curso.
     */
    private fun reloadShortcuts() {
        // Una recarga completa en curso ya traerá los accesos directos, y con los iconos nuevos.
        if (reloadJob?.isActive == true) return
        val apps = read.value?.first ?: return
        shortcutsJob?.cancel()
        shortcutsJob = scope.launch { read.value = apps to withContext(Dispatchers.IO) { loadShortcuts(apps) } }
    }

    /**
     * Pone a cada app su nombre e icono propios y su ocultación, y la lista vuelve a ir por orden
     * alfabético del nombre que se ve. Los accesos directos pasan a apuntar a esas apps, para que su
     * secundario diga el nombre propio.
     */
    private fun applyCustom(
        base: List<AppEntry>,
        shortcuts: List<ShortcutEntry>,
        own: Map<String, AppCustom>
    ): Pair<List<AppEntry>, List<ShortcutEntry>> {
        val previous = applied
        val apps =
            if (previous != null && previous.first === base && previous.second == own) {
                previous.third
            } else if (own.isEmpty()) {
                base
            } else {
                base.map { app -> own[app.key.toString()]?.let { app.customized(it) } ?: app }
                    .sortedAlphabetically(label = { it.label }, key = { it.key })
            }
        applied = Triple(base, own, apps)
        if (apps === base) return apps to shortcuts
        val byKey = apps.associateBy { it.key }
        return apps to shortcuts.map { shortcut ->
            val app = byKey[shortcut.app.key]
            if (app == null || app === shortcut.app) {
                shortcut
            } else {
                ShortcutEntry(shortcut.key, shortcut.label, app, shortcut.info, shortcut.isConversation)
            }
        }
    }

    private fun AppEntry.customized(own: AppCustom): AppEntry {
        val chosen = CustomIcon.parse(own.icon)
        // Un icono elegido que ya no está (paquete desinstalado, imagen borrada) deja el de la app.
        val drawn =
            when (chosen) {
                null -> null
                is CustomIcon.Pack -> packs.icon(chosen.packageName, chosen.drawable)
                is CustomIcon.Image -> images.load(chosen.file)
            }
        return AppEntry(
            key = key,
            label = own.label ?: label,
            component = component,
            user = user,
            icon = drawn?.let { if (suspended) dimmed(it) else it } ?: icon,
            updatedAt = updatedAt,
            originalLabel = label,
            originalIcon = icon,
            hidden = own.hidden,
            customIcon = chosen.takeIf { drawn != null },
            suspended = suspended,
            hideFromSuggestions = hideFromSuggestions
        )
    }

    /**
     * El icono de una app en pausa, a α 0,38 como todo lo que no se puede usar (docs/DISENO.md §2.6).
     * Va en el propio dibujo: así se ve igual en el dock, Zonas, Trazo, la barra y el Interior.
     */
    private fun dimmed(icon: Bitmap): Bitmap {
        val out = createBitmap(icon.width, icon.height)
        val paint = Paint().apply { alpha = (AlisioTokens.DISABLED_ALPHA * OPAQUE).roundToInt() }
        Canvas(out).drawBitmap(icon, 0f, 0f, paint)
        return out
    }

    // Cada app pregunta una vez por recarga: el sistema responde por paquete y usuario.
    private fun hiddenFromSuggestions(packageName: String, user: UserHandle): Boolean = try {
        launcherApps.shouldHideFromSuggestions(packageName, user)
    } catch (e: RuntimeException) {
        Log.w(TAG, "No se ha podido saber si $packageName se puede sugerir", e)
        false
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
        // Solo el paquete: el id de un acceso directo de conversación puede llevar datos del contacto.
        Log.w(TAG, "No se ha podido abrir un acceso directo de ${shortcut.info.`package`}", e)
        reload()
        false
    }

    /**
     * Acepta el acceso directo que otra app pide fijar («Añadir a inicio», docs/IDEAS.md §2.3) y
     * devuelve su clave ([shortcutKey]), o `null` si el sistema ya no lo admite. Una vez fijado,
     * sale en la barra como cualquier otro: `loadShortcuts` lee también los fijados.
     */
    fun acceptPin(request: LauncherApps.PinItemRequest): String? {
        val info = request.shortcutInfo ?: return null
        return try {
            if (!request.isValid || !request.accept()) return null
            shortcutKey(info.`package`, info.id, userManager.getSerialNumberForUser(info.userHandle))
        } catch (e: RuntimeException) {
            // Petición caducada o rol de inicio perdido entre medias.
            Log.w(TAG, "No se ha podido fijar un acceso directo de ${info.`package`}", e)
            null
        }
    }

    /** Los accesos directos fijados a Alisio, para la copia de la configuración. */
    fun pinnedShortcuts(): List<Triple<String, String, Long>> = _shortcuts.value
        .filter { it.info.isPinned }
        .map { Triple(it.info.`package`, it.info.id, it.app.key.userSerial) }

    /**
     * Vuelve a fijar accesos directos al cargar una copia. El sistema no los copia, y `pinShortcuts`
     * sustituye los fijados de cada paquete: se suman a los que ya hubiera. El de una app que falta o
     * que ya no lo publica se queda fuera sin más.
     */
    fun repin(pins: List<Triple<String, String, Long>>) {
        if (!launcherApps.hasShortcutHostPermission()) return
        pins.groupBy { it.first to it.third }.forEach { (owner, group) ->
            val (packageName, serial) = owner
            val user = userManager.getUserForSerialNumber(serial) ?: android.os.Process.myUserHandle()
            try {
                val query = ShortcutQuery().setPackage(packageName).setQueryFlags(ShortcutQuery.FLAG_MATCH_PINNED)
                val current = launcherApps.getShortcuts(query, user).orEmpty().map { it.id }
                launcherApps.pinShortcuts(packageName, (current + group.map { it.second }).distinct(), user)
            } catch (e: RuntimeException) {
                Log.w(TAG, "No se han podido volver a fijar los accesos directos de $packageName", e)
            }
        }
        reloadShortcuts()
    }

    /**
     * Deja fijados justo [pins], para deshacer la carga de una copia: [repin] solo suma, y los que
     * trajo la copia se quedarían en la barra. Cada paquete que tiene fijados ahora o en [pins] pasa
     * a tener exactamente los suyos; una lista vacía los desfija todos.
     */
    fun setPinned(pins: List<Triple<String, String, Long>>) {
        if (!launcherApps.hasShortcutHostPermission()) return
        val wanted = pins.groupBy({ it.first to it.third }, { it.second })
        val owners = wanted.keys + pinnedShortcuts().map { it.first to it.third }
        owners.forEach { owner -> pinExactly(owner.first, owner.second, wanted[owner].orEmpty()) }
        reloadShortcuts()
    }

    /**
     * Quita un acceso fijado a Alisio («Añadir a inicio»): fuera de las pilas no hay otra forma de
     * quitarlo, y `pinShortcuts` sustituye los fijados del paquete, así que se pasan los demás.
     */
    fun unpin(shortcut: ShortcutEntry) {
        if (!launcherApps.hasShortcutHostPermission()) return
        val packageName = shortcut.info.`package`
        val serial = shortcut.app.key.userSerial
        val others =
            pinnedShortcuts().filter { it.first == packageName && it.third == serial && it.second != shortcut.info.id }
        pinExactly(packageName, serial, others.map { it.second })
        reloadShortcuts()
    }

    private fun pinExactly(packageName: String, serial: Long, ids: List<String>) {
        val user = userManager.getUserForSerialNumber(serial) ?: android.os.Process.myUserHandle()
        try {
            launcherApps.pinShortcuts(packageName, ids.distinct(), user)
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se han podido cambiar los accesos fijados de $packageName", e)
        }
    }

    /** Abre la ficha de la app en los ajustes del sistema. Devuelve `false` si no se ha podido. */
    fun openDetails(app: AppEntry): Boolean = try {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "No se ha podido abrir la ficha de ${app.key}", e)
        false
    }

    /**
     * El icono propio de un acceso directo, o `null` si no tiene o no se deja leer. Se pide solo
     * para los pocos que enseña el Interior de app; al indexar, todos llevan el icono de su app.
     */
    suspend fun shortcutIcon(shortcut: ShortcutEntry): Bitmap? = withContext(Dispatchers.IO) {
        try {
            launcherApps
                .getShortcutIconDrawable(shortcut.info, appContext.resources.displayMetrics.densityDpi)
                ?.toBitmap(iconSizePx, iconSizePx)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Icono de acceso directo imposible de leer", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Icono de acceso directo demasiado grande", e)
            null
        }
    }

    /**
     * Los iconos de [apps] dibujados con [style], sin cambiar el estilo elegido: es lo que enseña cada
     * opción de estilo en los ajustes. Son pocos iconos y se guardan en memoria hasta la próxima
     * recarga de apps; una app que ya no está se queda con el icono que tenía.
     */
    suspend fun previewIcons(style: IconStyle, apps: List<AppEntry>): List<Bitmap> = withContext(Dispatchers.IO) {
        // El paquete solo se lee si algún icono no está ya dibujado.
        val pack by lazy { (style as? IconStyle.Pack)?.let { IconPack.load(appContext, it.packageName) } }
        apps.map { app ->
            previewIcons.getOrPut(style.id + "|" + app.key) {
                val info =
                    try {
                        launcherApps
                            .getActivityList(app.component.packageName, app.user)
                            .firstOrNull { it.componentName == app.component }
                    } catch (e: RuntimeException) {
                        // Perfil bloqueado o app desinstalada mientras se pedía.
                        Log.w(TAG, "No se ha podido leer ${app.key} para su vista previa", e)
                        null
                    }
                if (info == null) app.icon else safeIcon(info.getIcon(0), pack?.iconFor(app.component), style)
            }
        }
    }

    private fun loadApps(): List<AppEntry> {
        val style = iconStyle.value ?: IconStyle.Color
        val pack = (style as? IconStyle.Pack)?.let { IconPack.load(appContext, it.packageName) }
        return loadApps(style, pack)
    }

    // De uno en uno a propósito: repartir los iconos entre 2, 4 u 8 hilos tardó lo mismo en el S26
    // (docs/PLAN.md §11, fase 5). Casi todo el tiempo se va la primera vez que el proceso lee cada
    // app: una segunda lectura completa tarda la quinta parte.
    private fun loadApps(style: IconStyle, pack: IconPack?): List<AppEntry> = launcherApps.profiles
        .flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            val unsuggested = HashMap<String, Boolean>()
            launcherApps.getActivityList(null, user).map { info ->
                val packageName = info.componentName.packageName
                val suspended = info.applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED != 0
                val icon = safeIcon(info.getIcon(0), pack?.iconFor(info.componentName), style)
                AppEntry(
                    key = AppKey(packageName, info.componentName.className, serial),
                    label = info.label.toString(),
                    component = info.componentName,
                    user = user,
                    icon = if (suspended) dimmed(icon) else icon,
                    updatedAt = updatedAt(packageName),
                    suspended = suspended,
                    hideFromSuggestions = unsuggested.getOrPut(packageName) { hiddenFromSuggestions(packageName, user) }
                )
            }
        }
        .filterNot { it.component.packageName == appContext.packageName }
        .sortedAlphabetically(label = { it.label }, key = { it.key })

    /** Hora de la última instalación o actualización: la regla «versión nueva» de Ahora sale de aquí. */
    private fun updatedAt(packageName: String): Long = try {
        appContext.packageManager.getPackageInfo(packageName, 0).lastUpdateTime
    } catch (_: PackageManager.NameNotFoundException) {
        // Desinstalada mientras se leía la lista, o de un perfil que este usuario no ve.
        0L
    }

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
                    key = shortcutKey(info.`package`, info.id, serial),
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
        const val OPAQUE = 255
        const val CONVERSATION_CATEGORY = "android.shortcut.conversation"
        const val SHARE_TARGET_MARK = "SHARE_TARGET"

        // Tokens «Texto» y «Superficie elevada» de docs/PLAN.md §6.
        const val ICON_TINT = 0xFFF2F3F5.toInt()
        const val ICON_SURFACE = 0xFF1B1E22.toInt()
    }
}
