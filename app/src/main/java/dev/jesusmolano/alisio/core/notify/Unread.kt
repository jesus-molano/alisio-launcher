package dev.jesusmolano.alisio.core.notify

/**
 * Lo único que Alisio mira de una notificación para los puntos de no leído (§5.5): de quién es y de
 * qué clase. Nunca su título ni su texto.
 *
 * @param media es el aviso de un reproductor (lleva una sesión multimedia o es de categoría transporte).
 */
data class NotificationFacts(
    val packageName: String,
    val ongoing: Boolean = false,
    val foregroundService: Boolean = false,
    val groupSummary: Boolean = false,
    val media: Boolean = false
)

/**
 * Si la notificación cuenta como algo sin leer. No cuentan las permanentes ni las de servicio en
 * primer plano (una descarga, una ruta: no hay nada que leer), ni el resumen de un grupo, que
 * duplicaría a sus hijas. Tampoco el aviso de un reproductor: en pausa deja de ser permanente y
 * dejaría un punto fijo en la app de música.
 */
fun NotificationFacts.countsAsUnread(): Boolean = !ongoing && !foregroundService && !groupSummary && !media

/** Cuántas notificaciones sin leer tiene cada paquete; los que no tienen ninguna no aparecen. */
fun unreadByPackage(notifications: Collection<NotificationFacts>): Map<String, Int> =
    notifications.filter { it.countsAsUnread() }.groupingBy { it.packageName }.eachCount()

/** Los paquetes que enseñan punto: tienen algo sin leer y su punto no está apagado en los ajustes. */
fun packagesWithDot(unread: Map<String, Int>, muted: Set<String>): Set<String> =
    unread.filterTo(HashMap()) { (packageName, count) -> count > 0 && packageName !in muted }.keys
