package dev.jesusmolano.alisio.core.theme

import dev.jesusmolano.alisio.core.glance.sunTimes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Minutos de un día. */
const val DAY_MINUTES = 24 * 60

// El cambio de tono dura hora y media, centrada en la salida y en la puesta: como la luz de fuera.
private const val TRANSITION_MINUTES = 90

// Sin salida ni puesta (noche o día polar, o sin sitio conocido): un día de siete a siete.
private const val FALLBACK_SUNRISE = 7 * 60
private const val FALLBACK_SUNSET = 19 * 60

// Sin ubicación se calcula para la longitud de la zona horaria y una latitud media de Europa.
private const val FALLBACK_LATITUDE = 40.0
private const val DEGREES_PER_HOUR = 15.0
private const val SECONDS_PER_HOUR = 3600.0

/** Salida y puesta del sol en minutos del día local. */
data class SunMinutes(val sunrise: Int, val sunset: Int)

/**
 * La salida y la puesta del día [date] en [zone], en minutos del día. Sin coordenadas se toma la
 * longitud de la zona horaria; si el sol no sale o no se pone, un día de siete a siete.
 */
fun sunMinutes(date: LocalDate, zone: ZoneId, latitude: Double?, longitude: Double?): SunMinutes {
    val offsetHours = zone.rules.getOffset(date.atStartOfDay(zone).toInstant()).totalSeconds / SECONDS_PER_HOUR
    val lat = latitude ?: FALLBACK_LATITUDE
    val lon = longitude ?: (offsetHours * DEGREES_PER_HOUR)
    val times = sunTimes(date, lat, lon) ?: return SunMinutes(FALLBACK_SUNRISE, FALLBACK_SUNSET)
    fun minuteOf(millis: Long): Int = Instant.ofEpochMilli(millis).atZone(zone).let { it.hour * 60 + it.minute }
    val rise = minuteOf(times.first)
    val set = minuteOf(times.second)
    return if (rise < set) SunMinutes(rise, set) else SunMinutes(FALLBACK_SUNRISE, FALLBACK_SUNSET)
}

/**
 * Cuánto es de día a [minute] (0 de noche, 1 de día), con una subida y una bajada suaves de
 * [TRANSITION_MINUTES] centradas en la salida y la puesta.
 */
fun daylight(minute: Int, sun: SunMinutes): Float {
    val half = TRANSITION_MINUTES / 2f
    val rising = smoothstep((minute - (sun.sunrise - half)) / TRANSITION_MINUTES)
    val setting = 1f - smoothstep((minute - (sun.sunset - half)) / TRANSITION_MINUTES)
    return minOf(rising, setting).coerceIn(0f, 1f)
}

/**
 * El acento a una hora: [night] de noche, [day] de día y, entre medias, la mezcla de los dos canal a
 * canal. No se gira el tono: de ámbar a turquesa pasaría por un amarillo y un verde que no son de
 * ninguno de los dos. Siempre legible con [text] encima ([legibleAccent]).
 */
fun dayAccent(day: Int, night: Int, daylight: Float, text: Int): Int {
    val t = daylight.coerceIn(0f, 1f)
    fun channel(shift: Int): Int {
        val from = (night shr shift) and CHANNEL
        val to = (day shr shift) and CHANNEL
        return (from + (to - from) * t).roundToInt().coerceIn(0, CHANNEL) shl shift
    }
    val mixed = OPAQUE or channel(RED) or channel(GREEN) or channel(BLUE)
    return legibleAccent(mixed, text)
}

/** El acento de cada tramo de [step] minutos de un día entero: la franja de la vista previa. */
fun dayStrip(day: Int, night: Int, sun: SunMinutes, text: Int, step: Int = 30): List<Int> =
    (0 until DAY_MINUTES step step).map { minute -> dayAccent(day, night, daylight(minute + step / 2, sun), text) }

/** Si dos acentos se distinguen a la vista: elegir dos iguales no tendría nada que cambiar. */
fun accentsDiffer(a: Int, b: Int): Boolean {
    val x = hsvOf(a)
    val y = hsvOf(b)
    val hue = abs(x.hue - y.hue).let { minOf(it, FULL_TURN - it) }
    return hue > MIN_HUE_DIFFERENCE || abs(x.value - y.value) > MIN_VALUE_DIFFERENCE
}

private fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

private const val FULL_TURN = 360f
private const val CHANNEL = 0xFF
private const val RED = 16
private const val GREEN = 8
private const val BLUE = 0
private const val OPAQUE = 0xFF shl 24
private const val MIN_HUE_DIFFERENCE = 8f
private const val MIN_VALUE_DIFFERENCE = 0.1f
