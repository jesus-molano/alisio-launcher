package dev.jesusmolano.alisio.core.glance

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/** Salida o puesta del sol. */
enum class SunEventKind { SUNRISE, SUNSET }

class SunEvent(val kind: SunEventKind, val at: Long)

private const val J2000 = 2451545.0
private const val UNIX_EPOCH_JULIAN = 2440587.5
private const val DAY_MILLIS = 86_400_000.0
private const val FULL_TURN = 360.0

// El sol se da por salido con el borde superior en el horizonte, contando la refracción.
private const val HORIZON_DEGREES = -0.833
private const val AXIAL_TILT = 23.4397

/**
 * Salida y puesta del sol en [date] para un sitio, con la ecuación de la salida del sol (la de
 * NOAA simplificada): da el minuto con un error de uno o dos. Sin red: solo hacen falta la fecha y
 * las coordenadas. Devuelve `null` en la noche o el día polar, cuando el sol no cruza el horizonte.
 *
 * @return la salida y la puesta, en milisegundos desde 1970.
 */
fun sunTimes(date: LocalDate, latitude: Double, longitude: Double): Pair<Long, Long>? {
    // Mediodía UTC de la fecha, en días julianos desde J2000.
    val n = date.toEpochDay() + UNIX_EPOCH_JULIAN + 0.5 - J2000 + 0.0008
    val meanSolarTime = n - longitude / FULL_TURN
    val anomaly = Math.toRadians((357.5291 + 0.98560028 * meanSolarTime).mod(FULL_TURN))
    val center = 1.9148 * sin(anomaly) + 0.0200 * sin(2 * anomaly) + 0.0003 * sin(3 * anomaly)
    val eclipticLongitude =
        Math.toRadians((Math.toDegrees(anomaly) + center + 180.0 + 102.9372).mod(FULL_TURN))
    val transit = J2000 + meanSolarTime + 0.0053 * sin(anomaly) - 0.0069 * sin(2 * eclipticLongitude)
    val declination = asin(sin(eclipticLongitude) * sin(Math.toRadians(AXIAL_TILT)))
    val phi = Math.toRadians(latitude)
    val cosHourAngle =
        (sin(Math.toRadians(HORIZON_DEGREES)) - sin(phi) * sin(declination)) / (cos(phi) * cos(declination))
    if (cosHourAngle !in -1.0..1.0) return null
    val hourAngle = Math.toDegrees(acos(cosHourAngle)) / FULL_TURN
    return julianToMillis(transit - hourAngle) to julianToMillis(transit + hourAngle)
}

private fun julianToMillis(julian: Double): Long = ((julian - UNIX_EPOCH_JULIAN) * DAY_MILLIS).toLong()

/**
 * Lo próximo que hará el sol después de [now]: la puesta de hoy, o la salida de mañana si ya se ha
 * puesto. `null` si en los dos días siguientes no sale ni se pone (latitudes polares).
 */
fun nextSunEvent(now: Long, latitude: Double, longitude: Double, zone: ZoneId): SunEvent? {
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return (0L..2L).asSequence()
        .mapNotNull { sunTimes(today.plusDays(it), latitude, longitude) }
        .flatMap { (rise, set) -> sequenceOf(SunEvent(SunEventKind.SUNRISE, rise), SunEvent(SunEventKind.SUNSET, set)) }
        .firstOrNull { it.at > now }
}
