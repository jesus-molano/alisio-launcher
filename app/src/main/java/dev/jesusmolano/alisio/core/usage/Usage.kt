package dev.jesusmolano.alisio.core.usage

import java.time.Instant
import java.time.ZoneId

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MINUTES = 60L

/** Lo que el sistema apunta de una actividad de la app: pasa a primer plano o lo deja. */
enum class ForegroundChange { RESUMED, PAUSED, STOPPED }

/** @param activity nombre de la actividad; una app puede tener varias a la vez en pantalla dividida. */
class ForegroundEvent(val at: Long, val activity: String, val change: ForegroundChange)

/** Primer instante del día local al que pertenece [now]: el uso «de hoy» se cuenta desde ahí. */
fun startOfDay(now: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

/**
 * Tiempo que una app ha estado en primer plano entre [from] y [until], a partir de sus eventos de
 * actividad en ese tramo. La app cuenta como visible mientras tenga alguna actividad reanudada.
 *
 * Dos bordes: si lo primero que se ve es una pausa, la app ya estaba abierta al empezar el tramo
 * (venía de antes de medianoche) y cuenta desde [from]; si al final sigue reanudada, cuenta hasta
 * [until]. Una parada (`STOPPED`) cierra su actividad, pero no abre el tramo desde [from]: llega
 * también después de una pausa anterior a medianoche.
 */
fun foregroundMillis(events: List<ForegroundEvent>, from: Long, until: Long): Long {
    val resumed = HashSet<String>()
    var openedAt = 0L
    var total = 0L
    var sawActivityChange = false
    for (event in events.sortedBy { it.at }) {
        val at = event.at.coerceIn(from, until)
        when (event.change) {
            ForegroundChange.RESUMED -> {
                if (resumed.isEmpty()) openedAt = at
                resumed += event.activity
                sawActivityChange = true
            }

            ForegroundChange.PAUSED -> {
                if (!sawActivityChange) {
                    total += at - from
                } else if (resumed.remove(event.activity) && resumed.isEmpty()) {
                    total += at - openedAt
                }
                sawActivityChange = true
            }

            ForegroundChange.STOPPED ->
                if (resumed.remove(event.activity) && resumed.isEmpty()) total += at - openedAt
        }
    }
    if (resumed.isNotEmpty()) total += until - openedAt
    return total.coerceAtLeast(0L)
}

/**
 * Un tiempo de uso partido en lo que se enseña: horas y minutos enteros. El texto lo pone la
 * interfaz con sus recursos.
 *
 * @param underMinute hubo uso real, pero menos de un minuto: no es lo mismo que no haber abierto la app.
 */
data class UsageDuration(val hours: Int, val minutes: Int, val underMinute: Boolean)

fun usageDuration(millis: Long): UsageDuration {
    val totalMinutes = millis.coerceAtLeast(0L) / MINUTE_MILLIS
    return UsageDuration(
        hours = (totalMinutes / HOUR_MINUTES).toInt(),
        minutes = (totalMinutes % HOUR_MINUTES).toInt(),
        underMinute = millis > 0L && totalMinutes == 0L
    )
}
