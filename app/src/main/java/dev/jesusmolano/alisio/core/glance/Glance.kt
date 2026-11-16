package dev.jesusmolano.alisio.core.glance

/**
 * Las piezas que pueden ir en la línea bajo la hora. [id] es lo que se guarda: no cambia aunque
 * cambie el nombre del valor.
 */
enum class GlanceItem(val id: String) {
    ALARM("alarm"),
    EVENT("event"),
    WEATHER("weather"),
    BATTERY("battery"),

    /** La próxima salida o puesta del sol, calculada sin red a partir del sitio del tiempo. */
    SUN("sun"),

    /** Cuánto se ha usado el teléfono hoy, con el acceso a uso que ya da el uso de cada app. */
    SCREEN_TIME("screen");

    companion object {
        fun fromId(id: String): GlanceItem? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Lo que sale la primera vez: la alarma y la batería, que no piden permisos ni red. Lo demás se
 * enciende en los ajustes, donde se ve qué pide.
 */
val DEFAULT_GLANCE: List<GlanceItem> = listOf(GlanceItem.ALARM, GlanceItem.BATTERY)

/** Las piezas elegidas, en su orden, desde lo guardado. `null` es «nunca se tocó»: las de por defecto. */
fun parseGlance(stored: String?): List<GlanceItem> {
    if (stored == null) return DEFAULT_GLANCE
    return stored.split(',').mapNotNull { GlanceItem.fromId(it.trim()) }.distinct()
}

fun formatGlance(items: List<GlanceItem>): String = items.distinct().joinToString(",") { it.id }

/** Enciende [item] al final de la línea o la quita si ya estaba. */
fun toggleGlance(items: List<GlanceItem>, item: GlanceItem): List<GlanceItem> =
    if (item in items) items - item else items + item

/** Sube [item] un puesto; el primero se queda donde está. */
fun raiseGlance(items: List<GlanceItem>, item: GlanceItem): List<GlanceItem> {
    val index = items.indexOf(item)
    if (index <= 0) return items
    return items.toMutableList().apply {
        removeAt(index)
        add(index - 1, item)
    }
}

/** Cuándo se enseña la batería. [id] es lo que se guarda. */
enum class BatteryShow(val id: String) {
    ALWAYS("always"),

    /** Por debajo de [LOW_BATTERY_PERCENT]: es cuando hace falta saberlo. */
    LOW("low"),
    CHARGING("charging");

    companion object {
        fun parse(id: String?): BatteryShow = entries.firstOrNull { it.id == id } ?: LOW
    }
}

const val LOW_BATTERY_PERCENT = 30

/** Si la batería tiene algo que decir con el umbral elegido. */
fun showBattery(percent: Int, charging: Boolean, show: BatteryShow): Boolean = when (show) {
    BatteryShow.ALWAYS -> true
    BatteryShow.LOW -> percent < LOW_BATTERY_PERCENT
    BatteryShow.CHARGING -> charging
}

/** Lo que mira la línea hacia delante: la alarma y los eventos de las próximas 12 horas. */
const val GLANCE_WINDOW_MILLIS = 12 * 60 * 60_000L

/** La alarma, si suena dentro de la ventana; una alarma ya pasada (aún sin borrar) no cuenta. */
fun alarmToShow(alarmAt: Long?, now: Long): Long? = alarmAt?.takeIf { it > now && it - now <= GLANCE_WINDOW_MILLIS }

/**
 * Un evento del calendario tal como lo lee Alisio.
 *
 * @param id el de su evento (no el de la repetición): con él se abre en el calendario.
 * @param declined el dueño ha dicho que no va: no es su próximo evento.
 */
data class CalendarEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val declined: Boolean
)

/**
 * El próximo evento que empieza en la ventana. Los de todo el día no cuentan: no tienen hora a la
 * que llegar y taparían el de las 17:30. A igual hora, el primero de la lista.
 */
fun nextEvent(events: List<CalendarEvent>, now: Long): CalendarEvent? = events
    .filter { !it.allDay && !it.declined && it.begin >= now && it.begin - now <= GLANCE_WINDOW_MILLIS }
    .minByOrNull { it.begin }

/** Menos de un minuto de uso no se enseña: recién encendido no hay nada que contar. */
const val SCREEN_TIME_MIN_MILLIS = 60_000L

fun screenTimeToShow(millis: Long?): Long? = millis?.takeIf { it >= SCREEN_TIME_MIN_MILLIS }
