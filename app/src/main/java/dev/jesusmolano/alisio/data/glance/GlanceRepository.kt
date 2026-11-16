package dev.jesusmolano.alisio.data.glance

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import dev.jesusmolano.alisio.core.glance.CalendarEvent
import dev.jesusmolano.alisio.core.glance.GLANCE_WINDOW_MILLIS
import dev.jesusmolano.alisio.core.glance.GlanceItem
import dev.jesusmolano.alisio.core.glance.Place
import dev.jesusmolano.alisio.core.glance.WeatherReading
import dev.jesusmolano.alisio.core.glance.weatherIsStale
import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.data.settings.Prefs
import dev.jesusmolano.alisio.data.usage.UsageAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "GlanceRepository"
private const val PERCENT = 100
private const val WEATHER_RETRY_MILLIS = 10 * 60 * 1_000L

/** La batería tal como la cuenta el sistema. */
data class BatteryState(val percent: Int, val charging: Boolean)

/** La próxima alarma del reloj y la pantalla del reloj que la enseña. */
class NextAlarm(val at: Long, val show: PendingIntent?)

/**
 * Lo que la línea bajo la hora puede decir, sin filtrar: qué se enseña y cuándo lo deciden las
 * funciones de `core/glance` con la hora de cada minuto.
 *
 * @param events los eventos de las próximas 12 horas, ya leídos del calendario.
 * @param location la última ubicación aproximada conocida, para el tiempo y la salida del sol.
 * @param screenMillis el uso del teléfono hoy; `null` sin acceso a uso.
 */
data class GlanceData(
    val battery: BatteryState? = null,
    val alarm: NextAlarm? = null,
    val events: List<CalendarEvent> = emptyList(),
    val weather: WeatherReading? = null,
    val location: Coordinates? = null,
    val screenMillis: Long? = null
)

/**
 * Las fuentes de la línea bajo la hora del Inicio (batería, alarma, calendario, tiempo y uso).
 * Alisio no trabaja en segundo plano: los receptores solo existen entre [start] y [stop], que
 * llama la actividad en `onStart` y `onStop`, y lo demás se lee en [refresh], al volver al Inicio.
 * Lo que no está encendido en [Prefs.glance] no se lee.
 */
class GlanceRepository(
    context: Context,
    private val prefs: StateFlow<Prefs?>,
    private val usage: UsageAccess,
    private val clock: Clock,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val weather = WeatherSource(appContext)
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    private val _data = MutableStateFlow(GlanceData())
    val data: StateFlow<GlanceData> = _data.asStateFlow()

    private val _calendarGranted = MutableStateFlow(calendarPermission())
    val calendarGranted: StateFlow<Boolean> = _calendarGranted.asStateFlow()

    private val _locationGranted = MutableStateFlow(weather.locationGranted())
    val locationGranted: StateFlow<Boolean> = _locationGranted.asStateFlow()

    private var started = false
    private var observingCalendar = false

    // Una sola lectura del tiempo a la vez: volver al Inicio dos veces seguidas no pide dos.
    private val weatherReads = Mutex()

    // El último intento de pedir el tiempo, bajo [weatherReads]. Solo en memoria.
    private var lastFetchPlace: Coordinates? = null
    private var lastFetchAttempt = 0L

    private val batteryReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                _data.update { it.copy(battery = intent.battery()) }
            }
        }

    private val alarmReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                readAlarm()
            }
        }

    // Un evento nuevo o movido con el Inicio a la vista sale sin tener que salir y volver.
    private val calendarObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scope.launch { readEvents() }
            }
        }

    init {
        scope.launch {
            val (reading, location) = weather.saved()
            _data.update { it.copy(weather = it.weather ?: reading, location = it.location ?: location) }
        }
        // Encender una pieza o cambiar de ciudad con el Inicio vivo (los ajustes van encima de él)
        // la lee al momento, para que la miniatura enseñe el dato real.
        scope.launch {
            prefs.filterNotNull()
                .map { Triple(it.glance.toSet(), it.weatherPlace, it.dayAccent) }
                .distinctUntilChanged()
                .collect { if (started) refresh() }
        }
    }

    /**
     * El Inicio se ve: empieza a escuchar la batería, la alarma y el calendario. La primera lectura
     * de lo demás la pide `onResume` con [refresh], que llega justo después.
     */
    fun start() {
        if (started) return
        started = true
        val battery =
            ContextCompat.registerReceiver(
                appContext,
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        // El aviso de batería se queda guardado en el sistema: llega al registrarse, sin esperar.
        _data.update { it.copy(battery = battery?.battery()) }
        ContextCompat.registerReceiver(
            appContext,
            alarmReceiver,
            IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        observeCalendar()
    }

    /**
     * Escucha los cambios del calendario si hay permiso. Se llama también desde [refresh]: el permiso
     * se concede con un diálogo que solo pausa la actividad, sin el onStop y onStart que harían falta
     * para volver a pasar por [start].
     */
    private fun observeCalendar() {
        if (!started || observingCalendar || !calendarPermission()) return
        try {
            appContext.contentResolver.registerContentObserver(
                CalendarContract.Events.CONTENT_URI,
                true,
                calendarObserver
            )
            observingCalendar = true
        } catch (e: SecurityException) {
            Log.w(TAG, "No se puede observar el calendario", e)
        }
    }

    /** El Inicio deja de verse: nada queda escuchando. */
    fun stop() {
        if (!started) return
        started = false
        appContext.unregisterReceiver(batteryReceiver)
        appContext.unregisterReceiver(alarmReceiver)
        appContext.contentResolver.unregisterContentObserver(calendarObserver)
        observingCalendar = false
    }

    /**
     * Vuelve a leer lo que no avisa solo (el calendario y el uso de hoy), la alarma y, si lo
     * guardado tiene más de media hora, el tiempo. Los permisos se conceden fuera: se miran también.
     */
    fun refresh() {
        _calendarGranted.value = calendarPermission()
        observeCalendar()
        _locationGranted.value = weather.locationGranted()
        readAlarm()
        scope.launch { readEvents() }
        scope.launch { readScreenTime() }
        scope.launch { refreshWeather() }
    }

    /** Busca ciudades para fijar el tiempo; `null` sin red. */
    suspend fun searchPlaces(name: String, language: String): List<Place>? = weather.searchPlaces(name, language)

    private fun enabled(item: GlanceItem): Boolean = prefs.value?.glance?.contains(item) == true

    private fun readAlarm() {
        if (!enabled(GlanceItem.ALARM)) {
            _data.update { it.copy(alarm = null) }
            return
        }
        val info = alarmManager.nextAlarmClock
        val creator = info?.showIntent?.creatorPackage
        // Calendarios y automatizaciones también ponen «alarmas»: solo cuenta la del reloj
        // (docs/IDEAS.md §2.5). Sin creador conocido no se puede saber, y no se enseña.
        val fromClock = creator != null && creator in clockPackages()
        _data.update {
            it.copy(
                alarm = if (info != null &&
                    fromClock
                ) {
                    NextAlarm(info.triggerTime, info.showIntent)
                } else {
                    null
                }
            )
        }
    }

    private fun clockPackages(): Set<String> = try {
        appContext.packageManager.queryIntentActivities(Intent(AlarmClock.ACTION_SHOW_ALARMS), 0)
            .mapTo(HashSet()) { it.activityInfo.packageName }
    } catch (e: RuntimeException) {
        Log.w(TAG, "No se han podido buscar las apps de reloj", e)
        emptySet()
    }

    private suspend fun readEvents() {
        if (!enabled(GlanceItem.EVENT) || !calendarPermission()) {
            _data.update { it.copy(events = emptyList()) }
            return
        }
        val now = clock.now()
        val events =
            withContext(Dispatchers.IO) {
                val uri =
                    CalendarContract.Instances.CONTENT_URI.buildUpon()
                        .also {
                            ContentUris.appendId(it, now)
                            ContentUris.appendId(it, now + GLANCE_WINDOW_MILLIS)
                        }
                        .build()
                try {
                    appContext.contentResolver.query(
                        uri,
                        INSTANCE_COLUMNS,
                        "${CalendarContract.Instances.VISIBLE} = 1",
                        null,
                        "${CalendarContract.Instances.BEGIN} ASC"
                    )?.use { cursor ->
                        buildList {
                            while (cursor.moveToNext()) {
                                add(
                                    CalendarEvent(
                                        id = cursor.getLong(0),
                                        title = cursor.getString(1).orEmpty(),
                                        begin = cursor.getLong(2),
                                        end = cursor.getLong(3),
                                        allDay = cursor.getInt(4) != 0,
                                        declined =
                                            cursor.getInt(5) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
                                    )
                                )
                            }
                        }
                    }.orEmpty()
                } catch (e: RuntimeException) {
                    // Permiso retirado entre la comprobación y la consulta, o calendario sin proveedor.
                    Log.w(TAG, "No se ha podido leer el calendario", e)
                    emptyList()
                }
            }
        _data.update { it.copy(events = events) }
    }

    private suspend fun readScreenTime() {
        val millis = if (enabled(
                GlanceItem.SCREEN_TIME
            )
        ) {
            usage.todayScreenMillis(setOf(appContext.packageName))
        } else {
            null
        }
        _data.update { it.copy(screenMillis = millis) }
    }

    /**
     * La ubicación hace falta para el tiempo, para el sol y para el color del día; la petición, solo
     * para el tiempo y si lo guardado ya no vale ([weatherIsStale]). Con una ciudad fijada no se mira
     * la ubicación. Sin permiso de ubicación, el color del día se calcula con la zona horaria.
     */
    private suspend fun refreshWeather() {
        val current = prefs.value ?: return
        val wantsWeather = GlanceItem.WEATHER in current.glance
        if (!wantsWeather && GlanceItem.SUN !in current.glance && !current.dayAccent) return
        weatherReads.withLock {
            val city = current.weatherPlace
            val where =
                if (city != null) {
                    Coordinates(city.latitude, city.longitude)
                } else {
                    weather.currentLocation()?.also { found -> _data.update { it.copy(location = found) } }
                        ?: _data.value.location
                }
                    ?: return
            if (!wantsWeather) return
            val now = clock.now()
            if (!weatherIsStale(_data.value.weather, now, where.latitude, where.longitude)) return
            // Sin red, lo guardado sigue caducado: sin esta espera cada vuelta abriría otra conexión.
            // Otro sitio (una ciudad nueva) se pide en el acto.
            val attempt = SystemClock.elapsedRealtime()
            if (where == lastFetchPlace && attempt - lastFetchAttempt < WEATHER_RETRY_MILLIS) return
            lastFetchPlace = where
            lastFetchAttempt = attempt
            weather.fetch(where.latitude, where.longitude, now)?.let { reading ->
                _data.update { it.copy(weather = reading) }
            }
        }
    }

    private fun calendarPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun Intent.battery(): BatteryState? {
        val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        // «Cargando» es «con el cable puesto» (o la base), también lleno: el estado del sistema se
        // queda a veces en «cargando» un rato después de desenchufar.
        val charging = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        return BatteryState(level * PERCENT / scale, charging)
    }

    private companion object {
        val INSTANCE_COLUMNS =
            arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.SELF_ATTENDEE_STATUS
            )
    }
}
