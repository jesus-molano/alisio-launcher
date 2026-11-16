package dev.jesusmolano.alisio.data.glance

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.jesusmolano.alisio.core.glance.Place
import dev.jesusmolano.alisio.core.glance.WeatherReading
import dev.jesusmolano.alisio.core.glance.forecastUrl
import dev.jesusmolano.alisio.core.glance.geocodingUrl
import dev.jesusmolano.alisio.core.glance.parseForecast
import dev.jesusmolano.alisio.core.glance.parseGeocoding
import dev.jesusmolano.alisio.core.glance.roundCoordinate
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private val Context.weatherStore by preferencesDataStore("weather")

private const val TAG = "WeatherSource"
private const val HTTP_TIMEOUT_MILLIS = 8_000
private const val LOCATION_TIMEOUT_MILLIS = 10_000L
private const val LOCATION_RETRY_MILLIS = 15 * 60 * 1_000L

// Una ubicación de hace menos de tres horas vale para el tiempo: el teléfono rara vez cambia de
// ciudad en ese rato, y pedir una nueva enciende la radio para nada.
private const val LOCATION_MAX_AGE_NANOS = 3 * 60 * 60 * 1_000_000_000L

/** Unas coordenadas ya redondeadas a lo que se manda (`roundCoordinate`). */
data class Coordinates(val latitude: Double, val longitude: Double)

/**
 * El tiempo y la ubicación aproximada para la línea bajo la hora. Es la única pieza de Alisio que
 * usa la red (docs/PLAN.md §7): una petición a Open-Meteo, sin clave ni cuenta, que lleva solo las
 * coordenadas redondeadas a unos 1 km. La última lectura y la última ubicación se guardan en disco
 * para enseñarlas sin red y para la salida del sol, que se calcula aquí mismo sin red.
 *
 * Nada de esto corre en segundo plano: lo pide el Inicio al volver a verse ([GlanceRepository]).
 */
class WeatherSource(context: Context) {
    private val appContext = context.applicationContext
    private val store = appContext.weatherStore
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    // Solo en memoria: al arrancar de nuevo se puede volver a pedir.
    private var lastFreshAttempt = Long.MIN_VALUE / 2

    fun locationGranted(): Boolean = appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    /** Lo guardado: la última lectura y la última ubicación aproximada. Vacío si no hay nada o no se lee. */
    suspend fun saved(): Pair<WeatherReading?, Coordinates?> {
        val stored = store.data.catch { emit(emptyPreferences()) }.first()
        val reading =
            run {
                val temperature = stored[TEMPERATURE] ?: return@run null
                WeatherReading(
                    temperature = temperature,
                    code = stored[CODE] ?: return@run null,
                    day = stored[DAY] ?: true,
                    max = stored[MAX],
                    min = stored[MIN],
                    at = stored[AT] ?: return@run null,
                    latitude = stored[READ_LATITUDE] ?: return@run null,
                    longitude = stored[READ_LONGITUDE] ?: return@run null
                )
            }
        val latitude = stored[LOCATION_LATITUDE]
        val longitude = stored[LOCATION_LONGITUDE]
        val location = if (latitude != null && longitude != null) Coordinates(latitude, longitude) else null
        return reading to location
    }

    /**
     * La ubicación aproximada de ahora: la última que conozca el sistema si es reciente y, si no,
     * una nueva del mejor proveedor encendido, con diez segundos como mucho. `null` sin permiso,
     * con la ubicación apagada o si el sistema no da ninguna. Se guarda redondeada.
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Coordinates? {
        if (!locationGranted()) return null
        val found =
            withContext(Dispatchers.IO) {
                try {
                    val providers = locationManager.getProviders(true)
                    val last = providers.mapNotNull { locationManager.getLastKnownLocation(it) }.maxByOrNull { it.time }
                    if (last != null && last.ageNanos() < LOCATION_MAX_AGE_NANOS) return@withContext last
                    // Donde no llega la posición (interior, sin datos), cada vuelta al Inicio volvería a
                    // esperar hasta 10 s: se intenta una vez cada tanto y, mientras, vale la última.
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastFreshAttempt < LOCATION_RETRY_MILLIS) return@withContext last
                    lastFreshAttempt = now
                    val provider =
                        listOf(
                            LocationManager.FUSED_PROVIDER,
                            LocationManager.NETWORK_PROVIDER,
                            LocationManager.GPS_PROVIDER
                        ).firstOrNull { it in providers } ?: return@withContext last
                    withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) { fresh(provider) } ?: last
                } catch (e: RuntimeException) {
                    // Permiso retirado a medias o proveedor que desaparece: se usa lo guardado.
                    Log.w(TAG, "No se ha podido leer la ubicación", e)
                    null
                }
            } ?: return null
        val coordinates = Coordinates(roundCoordinate(found.latitude), roundCoordinate(found.longitude))
        write {
            it[LOCATION_LATITUDE] = coordinates.latitude
            it[LOCATION_LONGITUDE] = coordinates.longitude
        }
        return coordinates
    }

    @SuppressLint("MissingPermission")
    private suspend fun fresh(provider: String): Location? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        locationManager.getCurrentLocation(provider, signal, appContext.mainExecutor) { location ->
            if (continuation.isActive) continuation.resume(location)
        }
    }

    private fun Location.ageNanos(): Long = SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos

    /** Pide el tiempo de ese sitio y lo guarda. `null` sin red o si la respuesta no se entiende. */
    suspend fun fetch(latitude: Double, longitude: Double, now: Long): WeatherReading? {
        val body = get(forecastUrl(latitude, longitude)) ?: return null
        val reading = parseForecast(body, now, latitude, longitude) ?: return null
        write {
            it[TEMPERATURE] = reading.temperature
            it[CODE] = reading.code
            it[DAY] = reading.day
            if (reading.max == null) it.remove(MAX) else it[MAX] = reading.max
            if (reading.min == null) it.remove(MIN) else it[MIN] = reading.min
            it[AT] = reading.at
            it[READ_LATITUDE] = reading.latitude
            it[READ_LONGITUDE] = reading.longitude
        }
        return reading
    }

    /** Busca ciudades por su nombre para fijar el tiempo. `null` sin red: no es lo mismo que «nada». */
    suspend fun searchPlaces(name: String, language: String): List<Place>? {
        if (name.isBlank()) return emptyList()
        val body = get(geocodingUrl(name, language)) ?: return null
        return parseGeocoding(body)
    }

    /** Solo lo que lleva la URL sale del teléfono: ni cabeceras propias ni cookies. */
    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        val connection =
            try {
                URL(url).openConnection() as HttpURLConnection
            } catch (e: IOException) {
                Log.w(TAG, "No se ha podido preparar la petición", e)
                return@withContext null
            }
        try {
            connection.connectTimeout = HTTP_TIMEOUT_MILLIS
            connection.readTimeout = HTTP_TIMEOUT_MILLIS
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "Open-Meteo ha respondido ${connection.responseCode}")
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: IOException) {
            // Sin red, o la red se corta a mitad: se queda lo guardado.
            Log.i(TAG, "Sin respuesta de Open-Meteo: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun write(change: (MutablePreferences) -> Unit) {
        try {
            store.edit { change(it) }
        } catch (e: IOException) {
            Log.w(TAG, "No se ha podido guardar el tiempo", e)
        }
    }

    private companion object {
        val TEMPERATURE = doublePreferencesKey("temperature")
        val CODE = intPreferencesKey("code")
        val DAY = booleanPreferencesKey("day")
        val MAX = doublePreferencesKey("max")
        val MIN = doublePreferencesKey("min")
        val AT = longPreferencesKey("at")
        val READ_LATITUDE = doublePreferencesKey("read_latitude")
        val READ_LONGITUDE = doublePreferencesKey("read_longitude")
        val LOCATION_LATITUDE = doublePreferencesKey("location_latitude")
        val LOCATION_LONGITUDE = doublePreferencesKey("location_longitude")
    }
}
