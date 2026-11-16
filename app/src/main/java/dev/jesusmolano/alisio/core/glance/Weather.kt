package dev.jesusmolano.alisio.core.glance

import dev.jesusmolano.alisio.core.json.JsonException
import dev.jesusmolano.alisio.core.json.list
import dev.jesusmolano.alisio.core.json.number
import dev.jesusmolano.alisio.core.json.obj
import dev.jesusmolano.alisio.core.json.parseJson
import dev.jesusmolano.alisio.core.json.text
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/** Lo guardado deja de valer a los 30 minutos: entonces se pide otra lectura al volver al Inicio. */
const val WEATHER_MAX_AGE_MILLIS = 30 * 60_000L

/**
 * Dos decimales son unos 1,1 km: basta para el tiempo y no manda la posición exacta del teléfono.
 * Una ubicación que solo se mueve dentro de esa celda no pide una lectura nueva.
 */
private const val COORDINATE_DECIMALS = 2
private const val GEOCODING_RESULTS = 6

/** El estado del cielo que se dibuja, agrupado desde los códigos WMO que da Open-Meteo. */
enum class WeatherKind {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    STORM
}

/**
 * Código WMO 4677 (el que usa Open-Meteo en `weather_code`) agrupado en lo que se distingue de un
 * vistazo. Un código que no está en la tabla cuenta como nublado: es lo más neutro.
 */
fun weatherKind(code: Int): WeatherKind = when (code) {
    0 -> WeatherKind.CLEAR
    1, 2 -> WeatherKind.PARTLY_CLOUDY
    3 -> WeatherKind.CLOUDY
    45, 48 -> WeatherKind.FOG
    51, 53, 55, 56, 57 -> WeatherKind.DRIZZLE
    61, 63, 65, 66, 67, 80, 81, 82 -> WeatherKind.RAIN
    71, 73, 75, 77, 85, 86 -> WeatherKind.SNOW
    95, 96, 99 -> WeatherKind.STORM
    else -> WeatherKind.CLOUDY
}

/**
 * Una lectura del tiempo, tal como se guarda para enseñarla sin red.
 *
 * @param day si es de día en ese sitio: un cielo despejado de noche se dibuja con la luna.
 * @param at cuándo se leyó; con ella se decide si hace falta otra.
 * @param latitude y [longitude] dónde se pidió, ya redondeadas.
 */
data class WeatherReading(
    val temperature: Double,
    val code: Int,
    val day: Boolean,
    val max: Double?,
    val min: Double?,
    val at: Long,
    val latitude: Double,
    val longitude: Double
) {
    val kind: WeatherKind get() = weatherKind(code)
}

/** Un sitio elegido a mano por su nombre, o el que da la ubicación aproximada. */
data class Place(val name: String, val region: String?, val latitude: Double, val longitude: Double)

/** Coordenada redondeada a lo que se manda y se compara. */
fun roundCoordinate(value: Double): Double {
    val factor = Math.pow(10.0, COORDINATE_DECIMALS.toDouble())
    return (value * factor).roundToInt() / factor
}

/**
 * Si hay que volver a leer el tiempo: no hay nada guardado, lo guardado tiene más de media hora o
 * es de otro sitio (otra ciudad elegida, o la ubicación ha cambiado de celda).
 */
fun weatherIsStale(saved: WeatherReading?, now: Long, latitude: Double, longitude: Double): Boolean {
    if (saved == null) return true
    if (now - saved.at >= WEATHER_MAX_AGE_MILLIS || now < saved.at) return true
    return saved.latitude != roundCoordinate(latitude) || saved.longitude != roundCoordinate(longitude)
}

/** La petición a Open-Meteo: temperatura y código del cielo de ahora, y máxima y mínima de hoy. */
fun forecastUrl(latitude: Double, longitude: Double): String {
    val lat = format(roundCoordinate(latitude))
    val lon = format(roundCoordinate(longitude))
    return "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
        "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
        "&timezone=auto&forecast_days=1"
}

/** La búsqueda de una ciudad por su nombre, con los nombres en [language] (código ISO de dos letras). */
fun geocodingUrl(name: String, language: String): String {
    // La variante con `Charset` es de API 33; minSdk es 31.
    val query = URLEncoder.encode(name.trim(), "UTF-8")
    val lang = URLEncoder.encode(language, "UTF-8")
    return "https://geocoding-api.open-meteo.com/v1/search?name=$query&count=$GEOCODING_RESULTS" +
        "&language=$lang&format=json"
}

// Punto como separador decimal, sea cual sea el idioma del teléfono: la API no entiende la coma.
private fun format(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

/**
 * Lee la respuesta de [forecastUrl]. Devuelve `null` si falta la temperatura o el código: sin ellos
 * no hay nada que enseñar y se queda lo guardado.
 */
fun parseForecast(json: String, at: Long, latitude: Double, longitude: Double): WeatherReading? {
    val root = try {
        parseJson(json) as? Map<*, *>
    } catch (_: JsonException) {
        null
    } ?: return null
    val current = root.obj("current") ?: return null
    val temperature = current.number("temperature_2m") ?: return null
    val code = current.number("weather_code")?.toInt() ?: return null
    val daily = root.obj("daily")
    return WeatherReading(
        temperature = temperature,
        code = code,
        // Sin el dato, de día: el dibujo del sol es el que menos sorprende.
        day = current.number("is_day")?.let { it != 0.0 } ?: true,
        max = (daily?.list("temperature_2m_max")?.firstOrNull() as? Double),
        min = (daily?.list("temperature_2m_min")?.firstOrNull() as? Double),
        at = at,
        latitude = roundCoordinate(latitude),
        longitude = roundCoordinate(longitude)
    )
}

/** Lee la respuesta de [geocodingUrl]: los sitios que se llaman así, en el orden de la API. */
fun parseGeocoding(json: String): List<Place> {
    val root = try {
        parseJson(json) as? Map<*, *>
    } catch (_: JsonException) {
        null
    } ?: return emptyList()
    return root.list("results").orEmpty().mapNotNull { item ->
        val place = item as? Map<*, *> ?: return@mapNotNull null
        val name = place.text("name") ?: return@mapNotNull null
        val latitude = place.number("latitude") ?: return@mapNotNull null
        val longitude = place.number("longitude") ?: return@mapNotNull null
        // La región y el país distinguen los sitios que se llaman igual (Mérida, Santiago).
        val region =
            listOfNotNull(place.text("admin1"), place.text("country"))
                .distinct()
                .joinToString(", ")
                .ifBlank { null }
        Place(name, region, latitude, longitude)
    }
}
