package dev.jesusmolano.alisio.core.settings

/**
 * Las páginas de los ajustes de Alisio: la portada y una subpágina por sección. La barra abre los
 * ajustes directamente en una de ellas.
 *
 * @param id lo que distingue a cada página en el historial de la barra; no cambia aunque cambie
 *   el nombre del valor.
 * @param hasPreview la página enseña arriba el Inicio en miniatura, porque sus opciones lo cambian.
 */
enum class SettingsDestination(val id: String, val hasPreview: Boolean) {
    COVER("cover", hasPreview = true),
    LOOK("look", hasPreview = true),
    HOME("home", hasPreview = true),
    SEARCH("search", hasPreview = false),
    ZONES("zones", hasPreview = false),
    PAUSE("pause", hasPreview = false),
    PERMISSIONS("permissions", hasPreview = false)
}

/** Lo que Alisio necesita que el sistema le conceda, sea un permiso, un rol o un servicio. */
enum class Access {
    HOME_ROLE,
    CONTACTS,
    BLUETOOTH,
    NOTIFICATIONS,
    USAGE,
    ACCESSIBILITY
}

/**
 * Las opciones encendidas que no hacen nada sin un acceso.
 *
 * @param player el reproductor del Inicio; [unreadDots] los puntos de no leído.
 * @param lockDoubleTap y [notificationsSwipe] los dos gestos que ejecuta el servicio de accesibilidad.
 * @param contactPhotos las fotos de la agenda en los avatares.
 */
data class AccessUse(
    val player: Boolean,
    val unreadDots: Boolean,
    val lockDoubleTap: Boolean,
    val notificationsSwipe: Boolean,
    val contactPhotos: Boolean
)

/**
 * Los accesos que faltan y que algo encendido necesita: mientras falten, esa opción está encendida
 * y no hace nada. Ser la app de inicio cuenta siempre, porque sin serlo no hay accesos directos.
 * El audio Bluetooth y el acceso a uso no cuentan: solo añaden un dato a algo que ya funciona.
 */
fun blockingAccess(granted: Set<Access>, use: AccessUse): Set<Access> {
    val needed = HashSet<Access>()
    needed += Access.HOME_ROLE
    if (use.player || use.unreadDots) needed += Access.NOTIFICATIONS
    if (use.lockDoubleTap || use.notificationsSwipe) needed += Access.ACCESSIBILITY
    if (use.contactPhotos) needed += Access.CONTACTS
    return needed - granted
}

/** Los accesos en el orden en que se enseñan: primero los que faltan, cada grupo en su orden de siempre. */
fun accessOrder(granted: Set<Access>): List<Access> = Access.entries.sortedBy { it in granted }
