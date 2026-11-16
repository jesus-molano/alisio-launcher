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

    // Iconos y Gestos salen de Aspecto y de Inicio, que con la fase 7 ya no cabían en una página.
    // Los iconos se ven en el dock de la miniatura; los gestos no tienen nada que enseñar en ella.
    ICONS("icons", hasPreview = true),
    HOME("home", hasPreview = true),
    GESTURES("gestures", hasPreview = false),
    SEARCH("search", hasPreview = false),
    ZONES("zones", hasPreview = false),
    PAUSE("pause", hasPreview = false),
    PERMISSIONS("permissions", hasPreview = false)
}

/** Lo que Alisio necesita que el sistema le conceda, sea un permiso, un rol o un servicio. */
enum class Access {
    HOME_ROLE,
    CONTACTS,
    CALENDAR,
    LOCATION,
    BLUETOOTH,
    NOTIFICATIONS,
    USAGE,
    ACCESSIBILITY,

    /** Cambiar ajustes del sistema: el brillo y la rotación desde la barra. */
    SYSTEM_SETTINGS,

    /** La política de notificaciones: «no molestar» desde la barra. */
    DO_NOT_DISTURB
}

/**
 * Las opciones encendidas que no hacen nada sin un acceso.
 *
 * @param player el reproductor del Inicio; [unreadDots] los puntos de no leído.
 * @param lockDoubleTap y [notificationsSwipe] los dos gestos que ejecuta el servicio de accesibilidad.
 * @param contactPhotos las fotos de la agenda en los avatares.
 * @param nextEvent el próximo evento en la línea bajo la hora.
 * @param deviceLocation el tiempo o la salida del sol sin una ciudad fijada: van por la ubicación.
 * @param screenTime el uso del teléfono de hoy en la línea bajo la hora.
 * @param favoritePeople hay caras favoritas elegidas: sin la agenda no se pueden pintar ni abrir.
 */
data class AccessUse(
    val player: Boolean,
    val unreadDots: Boolean,
    val lockDoubleTap: Boolean,
    val notificationsSwipe: Boolean,
    val contactPhotos: Boolean,
    val nextEvent: Boolean = false,
    val deviceLocation: Boolean = false,
    val screenTime: Boolean = false,
    val favoritePeople: Boolean = false
)

/**
 * Los accesos que faltan y que algo encendido necesita: mientras falten, esa opción está encendida
 * y no hace nada. Ser la app de inicio cuenta siempre, porque sin serlo no hay accesos directos.
 * El audio Bluetooth no cuenta: solo añade un dato a algo que ya funciona. El acceso a uso, lo
 * mismo, salvo para el uso del teléfono bajo la hora, que sin él no tiene nada que enseñar. Cambiar
 * ajustes del sistema y «No molestar» tampoco: sin ellos, las órdenes de la barra abren el ajuste.
 */
fun blockingAccess(granted: Set<Access>, use: AccessUse): Set<Access> {
    val needed = HashSet<Access>()
    needed += Access.HOME_ROLE
    if (use.player || use.unreadDots) needed += Access.NOTIFICATIONS
    if (use.lockDoubleTap || use.notificationsSwipe) needed += Access.ACCESSIBILITY
    if (use.contactPhotos || use.favoritePeople) needed += Access.CONTACTS
    if (use.nextEvent) needed += Access.CALENDAR
    if (use.deviceLocation) needed += Access.LOCATION
    if (use.screenTime) needed += Access.USAGE
    return needed - granted
}

/** Los accesos en el orden en que se enseñan: primero los que faltan, cada grupo en su orden de siempre. */
fun accessOrder(granted: Set<Access>): List<Access> = Access.entries.sortedBy { it in granted }
