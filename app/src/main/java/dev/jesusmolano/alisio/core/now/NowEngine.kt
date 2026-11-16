package dev.jesusmolano.alisio.core.now

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Las reglas del hueco Ahora, de mayor a menor prioridad (§5.5). [id] es lo que se guarda en
 * `suggestion_feedback.rule`: cambiar el nombre de una entrada no debe perder sus descartes.
 */
enum class NowRule(val id: String) {
    /** App de la isla Dev instalada o actualizada hace menos de 24 h y sin abrir desde entonces. */
    NEW_VERSION("new-version"),

    /** La app que más se abre al poco de conectar el dispositivo de audio que se acaba de conectar. */
    BLUETOOTH("bluetooth"),

    /**
     * Reanudar la app de música por defecto: no suena nada y a esta hora, en días como hoy, se
     * suele poner música (Música siempre a mano).
     */
    MUSIC("music"),

    /** App que se suele abrir a esta hora en días como hoy y que hoy aún no se ha abierto. */
    HABIT("habit");

    /** Lo aprende del historial y, por eso, se puede olvidar en el sitio (docs/IDEAS.md §2.6). */
    val learned: Boolean get() = this != NEW_VERSION

    companion object {
        fun parse(id: String): NowRule? = entries.firstOrNull { it.id == id }
    }
}

/**
 * La sugerencia de Ahora: una sola, siempre con su motivo.
 *
 * @param since para [NowRule.NEW_VERSION], cuándo se instaló o actualizó; el motivo dice hace cuánto.
 * @param device para [NowRule.BLUETOOTH], el nombre del dispositivo conectado.
 * @param evidence en qué se basa una regla aprendida: los días de los últimos cinco del mismo tipo
 *   con apertura a esta hora ([NowRule.HABIT]) o las conexiones con apertura ([NowRule.BLUETOOTH]).
 */
data class NowSuggestion(
    val rule: NowRule,
    val appKey: String,
    val since: Long = 0,
    val device: String? = null,
    val evidence: Int = 0
)

/** App con icono de inicio y la hora de su última instalación o actualización. */
class NowApp(val key: String, val updatedAt: Long)

/**
 * La app de música por defecto, para [NowRule.MUSIC].
 *
 * @param appKey la clave de la app, la misma de las aperturas y los descartes.
 * @param starts cuándo empezó a sonar (desde los auriculares, la notificación o Alisio).
 * @param playing si suena algo ahora, de cualquier app: entonces no hay nada que reanudar.
 * @param lastPlayedAt la última vez que se la vio sonar; con ella, una pausa de hace un momento no
 *   se toma por «aún no ha sonado».
 * @param watching si se escuchan las sesiones. Sin ello [playing] y [lastPlayedAt] no dicen nada:
 *   la regla callaría la costumbre de días anteriores y propondría reanudar lo que ya suena.
 */
class NowMusic(
    val appKey: String,
    val starts: List<Long>,
    val playing: Boolean,
    val lastPlayedAt: Long? = null,
    val watching: Boolean = true
)

/** Descartes de una sugerencia concreta (regla y app). */
class NowFeedback(val dismissals: Int, val lastDismissedAt: Long)

/**
 * Todo lo que el motor necesita, ya leído: no toca Android ni el reloj.
 *
 * @param openings horas de apertura por clave de app.
 * @param dockKeys apps del dock (también dentro de sus pilas): ya están a un toque y la regla de
 *   costumbre no las sugiere.
 * @param bluetooth dispositivo de audio conectado ahora, si lo hay, con su hora de conexión.
 * @param bluetoothOpenings por dispositivo, cuántas veces se abrió cada app al poco de conectarlo.
 * @param newVersionKeys las apps a las que se aplica «versión nueva» (las de la isla Dev); `null`
 *   las admite todas. Sin límite, las actualizaciones automáticas de Play tapaban la costumbre.
 * @param seenAt por app, la última vez que estuvo en primer plano según el sistema, también abierta
 *   fuera de Alisio (desde recientes o una notificación). Solo en memoria.
 * @param forgotten por regla y app, desde cuándo cuenta lo aprendido: lo anterior se ha olvidado.
 * @param music la app de música por defecto y cuándo sonó; `null` sin ninguna.
 */
class NowInput(
    val now: Long,
    val zone: ZoneId,
    val apps: List<NowApp>,
    val openings: Map<String, List<Long>>,
    val dockKeys: Set<String>,
    val bluetooth: ConnectedAudio? = null,
    val bluetoothOpenings: Map<String, Map<String, Int>> = emptyMap(),
    val feedback: Map<Pair<NowRule, String>, NowFeedback> = emptyMap(),
    val newVersionKeys: Set<String>? = null,
    val seenAt: Map<String, Long> = emptyMap(),
    val forgotten: Map<Pair<NowRule, String>, Long> = emptyMap(),
    val music: NowMusic? = null
) {
    /** Abierta desde Alisio o vista por el sistema en [from] o después. */
    fun usedSince(key: String, from: Long): Boolean =
        openings[key].orEmpty().any { it >= from } || (seenAt[key] ?: Long.MIN_VALUE) >= from
}

private const val HOUR_MILLIS = 60L * 60 * 1000
const val NEW_VERSION_WINDOW_MILLIS = 24 * HOUR_MILLIS
const val DISMISS_HIDE_MILLIS = 6 * HOUR_MILLIS
const val DISMISSALS_TO_SILENCE = 3
private const val BLUETOOTH_MIN_CASES = 3

/**
 * Cuánto dura la sugerencia de Bluetooth tras conectar. Más que [BLUETOOTH_WINDOW_MILLIS], la ventana
 * en la que una apertura cuenta como caso: el teléfono no siempre se mira nada más conectar.
 */
const val BLUETOOTH_SUGGEST_MILLIS = 15 * 60 * 1000L
private const val RECENT_USE_MILLIS = 6 * HOUR_MILLIS
private const val HABIT_WINDOW_MINUTES = 30

/** Cuántos días del mismo tipo mira la costumbre; el motivo largo dice «N de los últimos 5». */
const val HABIT_DAYS = 5
private const val HABIT_MIN_DAYS = 3
private const val MINUTES_PER_DAY = 24 * 60

/**
 * Tras sonar, cuánto tarda en volver a proponerse la música. Una hora: más que la ventana de la
 * costumbre (±30 min), así la pausa de las 8:05 no trae «Reanudar» a las 8:10.
 */
const val MUSIC_RECENT_MILLIS = HOUR_MILLIS

/** La primera regla que tenga algo que proponer; `null` si ninguna (el hueco se queda vacío). */
fun suggest(input: NowInput): NowSuggestion? = newVersion(input) ?: bluetooth(input) ?: music(input) ?: habit(input)

/**
 * La costumbre de poner música a esta hora, con la misma cuenta que [habit] (días del mismo tipo con
 * algo a ±30 min), pero sobre las veces que empezó a sonar. A diferencia de [habit], vale aunque la
 * app esté en el dock: lo que propone es reanudarla de un toque, no encontrarla.
 */
private fun music(input: NowInput): NowSuggestion? {
    val music = input.music ?: return null
    val key = music.appKey
    if (!music.watching || music.playing || input.apps.none { it.key == key } ||
        input.suppressed(NowRule.MUSIC, key)
    ) {
        return null
    }
    val since = input.forgotten[NowRule.MUSIC to key]
    val starts = if (since == null) music.starts else music.starts.filter { it >= since }
    val recent = listOfNotNull(music.lastPlayedAt) + starts
    if (recent.any { input.now - it in 0 until MUSIC_RECENT_MILLIS }) return null
    val days = matchingDays(starts, input)
    return if (days >= HABIT_MIN_DAYS) NowSuggestion(NowRule.MUSIC, key, evidence = days) else null
}

private fun newVersion(input: NowInput): NowSuggestion? = input.apps
    .filter { app ->
        val age = input.now - app.updatedAt
        (input.newVersionKeys == null || app.key in input.newVersionKeys) &&
            age in 0 until NEW_VERSION_WINDOW_MILLIS &&
            // Una vez abierta la versión nueva, desde Alisio o desde fuera, ya no hay nada que avisar.
            !input.usedSince(app.key, app.updatedAt) &&
            !input.suppressed(NowRule.NEW_VERSION, app.key)
    }
    .maxByOrNull { it.updatedAt }
    ?.let { NowSuggestion(NowRule.NEW_VERSION, it.key, since = it.updatedAt) }

private fun bluetooth(input: NowInput): NowSuggestion? {
    val connected = input.bluetooth ?: return null
    // La regla habla del momento de conectar. Con el dispositivo puesto durante horas, seguir
    // proponiendo su app taparía la regla de costumbre todo ese tiempo.
    if (input.now - connected.since !in 0 until BLUETOOTH_SUGGEST_MILLIS) return null
    val installed = input.apps.mapTo(HashSet()) { it.key }
    return input.bluetoothOpenings[connected.name].orEmpty()
        .filter { (key, cases) ->
            cases >= BLUETOOTH_MIN_CASES &&
                key in installed &&
                // Abierta ya en esta conexión, desde Alisio o desde fuera, no hay nada que proponer.
                !input.usedSince(key, connected.since) &&
                !input.suppressed(NowRule.BLUETOOTH, key)
        }
        .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key })
        ?.let { NowSuggestion(NowRule.BLUETOOTH, it.key, device = connected.name, evidence = it.value) }
}

/** De los cinco días anteriores como hoy, en cuántos hay alguna hora de [times] a ±30 min de ahora. */
private fun matchingDays(times: List<Long>, input: NowInput): Int {
    val days = previousDaysLike(dateOf(input.now, input.zone))
    val nowMinute = minuteOfDay(input.now, input.zone)
    return times
        .filter { nearMinute(minuteOfDay(it, input.zone), nowMinute) }
        .mapTo(HashSet()) { dateOf(it, input.zone) }
        .count { it in days }
}

private fun habit(input: NowInput): NowSuggestion? {
    val today = dateOf(input.now, input.zone)
    val installed = input.apps.mapTo(HashSet()) { it.key }
    return input.openings
        .filter { (key, _) -> key in installed && key !in input.dockKeys && !input.suppressed(NowRule.HABIT, key) }
        .mapNotNull { (key, all) ->
            // «Hoy» incluye las últimas horas: a las 00:10 no se sugiere lo que se abrió a las 23:50.
            // Lo abierto fuera de Alisio también cuenta como usado hoy.
            val seen = all + listOfNotNull(input.seenAt[key])
            if (seen.any { dateOf(it, input.zone) == today || input.now - it in 0 until RECENT_USE_MILLIS }) {
                return@mapNotNull null
            }
            // Lo olvidado no cuenta para la costumbre: se vuelve a aprender con las aperturas de después.
            val since = input.forgotten[NowRule.HABIT to key]
            val openings = if (since == null) all else all.filter { it >= since }
            val matchingDays = matchingDays(openings, input)
            if (matchingDays >= HABIT_MIN_DAYS) key to matchingDays else null
        }
        // En empate, la clave: el resultado no debe bailar entre dos apps igual de habituales.
        .maxWithOrNull(compareBy<Pair<String, Int>> { it.second }.thenByDescending { it.first })
        ?.let { NowSuggestion(NowRule.HABIT, it.first, evidence = it.second) }
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
