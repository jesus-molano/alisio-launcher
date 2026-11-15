package dev.jesusmolano.alisio.core.now

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** Las reglas del hueco Ahora, de mayor a menor prioridad (§5.5). */
enum class NowRule {
    /** App instalada o actualizada hace menos de 24 h y sin abrir desde entonces. */
    NEW_VERSION,

    /** La app que más se abre al poco de conectar el dispositivo de audio que está conectado ahora. */
    BLUETOOTH,

    /** App que se suele abrir a esta hora en días como hoy y que hoy aún no se ha abierto. */
    HABIT
}

/**
 * La sugerencia de Ahora: una sola, siempre con su motivo.
 *
 * @param since para [NowRule.NEW_VERSION], cuándo se instaló o actualizó; el motivo dice hace cuánto.
 * @param device para [NowRule.BLUETOOTH], el nombre del dispositivo conectado.
 */
data class NowSuggestion(val rule: NowRule, val appKey: String, val since: Long = 0, val device: String? = null)

/** App con icono de inicio y la hora de su última instalación o actualización. */
class NowApp(val key: String, val updatedAt: Long)

/** Descartes de una sugerencia concreta (regla y app). */
class NowFeedback(val dismissals: Int, val lastDismissedAt: Long)

/**
 * Todo lo que el motor necesita, ya leído: no toca Android ni el reloj.
 *
 * @param openings horas de apertura por clave de app.
 * @param dockKeys apps del dock (también dentro de sus pilas): ya están a un toque y la regla de
 *   costumbre no las sugiere.
 * @param bluetoothDevice dispositivo de audio conectado ahora, si lo hay.
 * @param bluetoothOpenings por dispositivo, cuántas veces se abrió cada app al poco de conectarlo.
 */
class NowInput(
    val now: Long,
    val zone: ZoneId,
    val apps: List<NowApp>,
    val openings: Map<String, List<Long>>,
    val dockKeys: Set<String>,
    val bluetoothDevice: String? = null,
    val bluetoothOpenings: Map<String, Map<String, Int>> = emptyMap(),
    val feedback: Map<Pair<NowRule, String>, NowFeedback> = emptyMap()
)

private const val HOUR_MILLIS = 60L * 60 * 1000
const val NEW_VERSION_WINDOW_MILLIS = 24 * HOUR_MILLIS
const val DISMISS_HIDE_MILLIS = 6 * HOUR_MILLIS
const val DISMISSALS_TO_SILENCE = 3
private const val BLUETOOTH_MIN_CASES = 3
private const val RECENT_USE_MILLIS = 6 * HOUR_MILLIS
private const val HABIT_WINDOW_MINUTES = 30
private const val HABIT_DAYS = 5
private const val HABIT_MIN_DAYS = 3
private const val MINUTES_PER_DAY = 24 * 60

/** La primera regla que tenga algo que proponer; `null` si ninguna (el hueco se queda vacío). */
fun suggest(input: NowInput): NowSuggestion? = newVersion(input) ?: bluetooth(input) ?: habit(input)

private fun newVersion(input: NowInput): NowSuggestion? = input.apps
    .filter { app ->
        val age = input.now - app.updatedAt
        age in 0 until NEW_VERSION_WINDOW_MILLIS &&
            // Una vez abierta la versión nueva, ya no hay nada que avisar.
            input.openings[app.key].orEmpty().none { it >= app.updatedAt } &&
            !input.suppressed(NowRule.NEW_VERSION, app.key)
    }
    .maxByOrNull { it.updatedAt }
    ?.let { NowSuggestion(NowRule.NEW_VERSION, it.key, since = it.updatedAt) }

private fun bluetooth(input: NowInput): NowSuggestion? {
    val device = input.bluetoothDevice ?: return null
    val installed = input.apps.mapTo(HashSet()) { it.key }
    return input.bluetoothOpenings[device].orEmpty()
        .filter { (key, cases) ->
            cases >= BLUETOOTH_MIN_CASES && key in installed && !input.suppressed(NowRule.BLUETOOTH, key)
        }
        .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key })
        ?.let { NowSuggestion(NowRule.BLUETOOTH, it.key, device = device) }
}

private fun habit(input: NowInput): NowSuggestion? {
    val today = dateOf(input.now, input.zone)
    val days = previousDaysLike(today)
    val nowMinute = minuteOfDay(input.now, input.zone)
    val installed = input.apps.mapTo(HashSet()) { it.key }
    return input.openings
        .filter { (key, _) -> key in installed && key !in input.dockKeys && !input.suppressed(NowRule.HABIT, key) }
        .mapNotNull { (key, openings) ->
            // «Hoy» incluye las últimas horas: a las 00:10 no se sugiere lo que se abrió a las 23:50.
            if (openings.any { dateOf(it, input.zone) == today || input.now - it in 0 until RECENT_USE_MILLIS }) {
                return@mapNotNull null
            }
            val matchingDays =
                openings
                    .filter { nearMinute(minuteOfDay(it, input.zone), nowMinute) }
                    .mapTo(HashSet()) { dateOf(it, input.zone) }
                    .count { it in days }
            if (matchingDays >= HABIT_MIN_DAYS) key to matchingDays else null
        }
        // En empate, la clave: el resultado no debe bailar entre dos apps igual de habituales.
        .maxWithOrNull(compareBy<Pair<String, Int>> { it.second }.thenByDescending { it.first })
        ?.let { NowSuggestion(NowRule.HABIT, it.first) }
}

/** Descartada hace menos de 6 h, o tres veces: en ese caso se silencia para siempre. */
private fun NowInput.suppressed(rule: NowRule, key: String): Boolean {
    val given = feedback[rule to key] ?: return false
    return given.dismissals >= DISMISSALS_TO_SILENCE || now - given.lastDismissedAt < DISMISS_HIDE_MILLIS
}

/** Los cinco días anteriores del mismo tipo que [today]: laborables si lo es, fin de semana si no. */
private fun previousDaysLike(today: LocalDate): Set<LocalDate> {
    val weekend = today.isWeekend()
    return generateSequence(today.minusDays(1)) { it.minusDays(1) }
        .filter { it.isWeekend() == weekend }
        .take(HABIT_DAYS)
        .toSet()
}

private fun LocalDate.isWeekend() = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

private fun dateOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

private fun minuteOfDay(millis: Long, zone: ZoneId): Int {
    val time = Instant.ofEpochMilli(millis).atZone(zone)
    return time.hour * 60 + time.minute
}

/** A ±30 minutos, contando que las 23:50 y las 00:10 están a 20 minutos. */
private fun nearMinute(a: Int, b: Int): Boolean {
    val distance = abs(a - b)
    return minOf(distance, MINUTES_PER_DAY - distance) <= HABIT_WINDOW_MINUTES
}
