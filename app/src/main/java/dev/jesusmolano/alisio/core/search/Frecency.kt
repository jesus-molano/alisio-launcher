package dev.jesusmolano.alisio.core.search

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** Lo que cuenta para el ranking; lo más antiguo se borra al arrancar. */
const val HISTORY_DAYS = 90L

private const val HALF_LIFE_DAYS = 7.0
private const val HOUR_BONUS = 0.3
private const val HOUR_WINDOW_DAYS = 14L
private const val HOUR_WINDOW_MINUTES = 60
private const val HOUR_MIN_OPENINGS = 3
private const val MINUTES_PER_DAY = 24 * 60

/** `f = Σ 0,5^(días desde cada apertura / 7)` con las aperturas de los últimos 90 días (§5.3). */
fun frecency(openings: List<Long>, now: Long): Double = openings.sumOf { at ->
    val age = now - at
    if (age < 0 || age > HISTORY_DAYS * DAY_MILLIS) 0.0 else 0.5.pow(age.toDouble() / DAY_MILLIS / HALF_LIFE_DAYS)
}

/** Extra horario: 0,3 si hubo 3 aperturas o más a esta hora (±1 h) en los últimos 14 días. */
fun hourBonus(openings: List<Long>, now: Long, zone: ZoneId): Double {
    val nowMinute = minuteOfDay(now, zone)
    val count =
        openings.count { at ->
            val age = now - at
            if (age < 0 || age > HOUR_WINDOW_DAYS * DAY_MILLIS) return@count false
            val distance = abs(minuteOfDay(at, zone) - nowMinute)
            // Las 23:30 y las 00:15 están a 45 minutos, no a 23 horas.
            minOf(distance, MINUTES_PER_DAY - distance) <= HOUR_WINDOW_MINUTES
        }
    return if (count >= HOUR_MIN_OPENINGS) HOUR_BONUS else 0.0
}

/** Puntuación final: `calidad × (1 + ln(1 + f) + h)`. */
fun score(quality: Double, frecency: Double, hourBonus: Double): Double = quality * (1 + ln(1 + frecency) + hourBonus)

private fun minuteOfDay(millis: Long, zone: ZoneId): Int {
    val time = Instant.ofEpochMilli(millis).atZone(zone)
    return time.hour * 60 + time.minute
}
