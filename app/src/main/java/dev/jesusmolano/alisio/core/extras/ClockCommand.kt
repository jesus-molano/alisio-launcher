package dev.jesusmolano.alisio.core.extras

import dev.jesusmolano.alisio.core.text.normalize

/** Lo que la barra le pide al reloj. */
sealed interface ClockCommand {
    /** Alarma a una hora del día, en formato de 24 horas. */
    data class Alarm(val hour: Int, val minute: Int) : ClockCommand

    /** Cuenta atrás de [seconds] segundos. */
    data class Timer(val seconds: Int) : ClockCommand
}

private val ALARM_WORDS = listOf("alarma", "alarm", "despertador")
private val TIMER_WORDS = listOf("temporizador", "timer")

// «a las 7:30», «para las 7», «at 7», «de 10 minutos», «for 10 minutes».
private val FILLER = Regex("""^(?:a las|a la|para las|para la|para|at|for|de|en|in)\s+""")

// El reloj no acepta temporizadores de más de un día.
private const val MAX_TIMER_SECONDS = 86_400

/**
 * Lee «alarma 7:30» o «temporizador 10» (y «alarm», «despertador», «timer»). Devuelve `null` si no
 * empieza por una de esas palabras o lo que sigue no es una hora o una duración: la palabra sola es
 * una búsqueda normal.
 */
fun parseClockCommand(input: String): ClockCommand? {
    val text = normalize(input)
    val word = text.substringBefore(' ')
    if (' ' !in text) return null
    val rest = text.substringAfter(' ').trim().replace(FILLER, "")
    return when (word) {
        in ALARM_WORDS -> parseAlarmTime(rest)
        in TIMER_WORDS -> parseTimerLength(rest)
        else -> null
    }
}

private val TIME = Regex("""^(\d{1,2})(?:\s*[:.h]\s*(\d{2}))?\s*h?\s*(am|pm|a\.m\.|p\.m\.)?$""")

private fun parseAlarmTime(text: String): ClockCommand.Alarm? {
    val match = TIME.matchEntire(text) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].ifEmpty { "0" }.toInt()
    val half = match.groupValues[3]
    if (minute > 59) return null
    if (half.isEmpty()) return if (hour <= 23) ClockCommand.Alarm(hour, minute) else null
    if (hour !in 1..12) return null
    // Las 12 am son las 0 y las 12 pm, las 12.
    val pm = half.startsWith('p')
    return ClockCommand.Alarm(hour % 12 + if (pm) 12 else 0, minute)
}

private val CLOCK_LENGTH = Regex("""^(?:(\d{1,2}):)?(\d{1,3}):(\d{2})$""")
private val LENGTH_PART = Regex("""(\d+)\s*([a-z]*)""")

private val HOUR_UNITS = setOf("h", "hr", "hora", "horas", "hour", "hours")
private val MINUTE_UNITS = setOf("m", "min", "mins", "minuto", "minutos", "minute", "minutes")
private val SECOND_UNITS = setOf("s", "seg", "segs", "sec", "secs", "segundo", "segundos", "second", "seconds")

private const val HOUR = 3600L
private const val MINUTE = 60L

private fun parseTimerLength(text: String): ClockCommand.Timer? {
    val seconds = clockLength(text) ?: partsLength(text) ?: return null
    return if (seconds in 1..MAX_TIMER_SECONDS) ClockCommand.Timer(seconds.toInt()) else null
}

/** «1:30» es un minuto y medio; «1:05:00», una hora y cinco minutos. */
private fun clockLength(text: String): Long? {
    val match = CLOCK_LENGTH.matchEntire(text) ?: return null
    val (hours, minutes, seconds) = match.destructured
    if (seconds.toInt() > 59) return null
    return hours.ifEmpty { "0" }.toLong() * HOUR + minutes.toLong() * MINUTE + seconds.toLong()
}

/**
 * «10», «10m», «90s», «1h», «1h 30m», «1h30». Un número sin unidad son minutos, salvo detrás de las
 * horas o de los minutos, donde es la unidad siguiente («1h30», «2m30»).
 */
private fun partsLength(text: String): Long? {
    val compact = text.replace(" y ", " ").replace(" and ", " ")
    var total = 0L
    var position = 0
    // Unidad de la parte anterior, en segundos: obliga a ir de mayor a menor y sin repetir.
    var previous = Long.MAX_VALUE
    while (position < compact.length) {
        if (compact[position] == ' ') {
            position++
            continue
        }
        val match = LENGTH_PART.matchAt(compact, position) ?: return null
        val number = match.groupValues[1].toLongOrNull() ?: return null
        val unit =
            when (match.groupValues[2]) {
                "" ->
                    when (previous) {
                        Long.MAX_VALUE, HOUR -> MINUTE
                        MINUTE -> 1L
                        else -> return null
                    }

                in HOUR_UNITS -> HOUR

                in MINUTE_UNITS -> MINUTE

                in SECOND_UNITS -> 1L

                else -> return null
            }
        if (unit >= previous) return null
        // Un número sin unidad solo vale solo o al final («1h30»), nunca delante de otra parte.
        if (match.groupValues[2].isEmpty() && previous == Long.MAX_VALUE && match.range.last + 1 < compact.length) {
            return null
        }
        if (number > MAX_TIMER_SECONDS) return null
        total += number * unit
        previous = unit
        position = match.range.last + 1
    }
    return total.takeIf { previous != Long.MAX_VALUE }
}
