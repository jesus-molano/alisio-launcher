package dev.jesusmolano.alisio.data.usage

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log
import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.core.usage.ForegroundChange
import dev.jesusmolano.alisio.core.usage.ForegroundEvent
import dev.jesusmolano.alisio.core.usage.foregroundEntries
import dev.jesusmolano.alisio.core.usage.foregroundMillis
import dev.jesusmolano.alisio.core.usage.screenMillis
import dev.jesusmolano.alisio.core.usage.startOfDay
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Lo de hoy de una app: [entries] veces en primer plano, [millis] de tiempo. */
class AppToday(val entries: Int, val millis: Long)

/**
 * Acceso a uso (§7): un permiso especial que se concede en los ajustes del sistema, no con un
 * diálogo. Con él, el Interior de app enseña el uso de hoy. Solo se consulta cuando se pide: nada
 * queda escuchando.
 */
class UsageAccess(context: Context, private val clock: Clock) {
    private val appContext = context.applicationContext
    private val appOps = appContext.getSystemService(AppOpsManager::class.java)
    private val usageStats = appContext.getSystemService(UsageStatsManager::class.java)

    private val _granted = MutableStateFlow(isGranted())
    val granted: StateFlow<Boolean> = _granted.asStateFlow()

    /** Se concede y se retira fuera de Alisio: hay que volver a mirarlo al regresar. */
    fun refresh() {
        _granted.value = isGranted()
    }

    // Las dos variantes de la consulta están marcadas como obsoletas en algún nivel de API, y la
    // que las sustituye deja una nota de acceso que aquí no procede: solo se mira si está concedido.
    @Suppress("DEPRECATION")
    private fun isGranted(): Boolean = try {
        val mode =
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.packageName)
        when (mode) {
            AppOpsManager.MODE_ALLOWED -> true

            // Sin decisión en la operación manda el permiso: es el caso de concederlo por adb.
            AppOpsManager.MODE_DEFAULT ->
                appContext.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                    PackageManager.PERMISSION_GRANTED

            else -> false
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "No se ha podido comprobar el acceso a uso", e)
        false
    }

    /**
     * Tiempo en primer plano de [packageName] desde la medianoche local, o `null` si no hay acceso
     * o el sistema no responde. Se calcula con los eventos de actividad y no con las estadísticas
     * agregadas: estas van por tramos del sistema que no empiezan a medianoche.
     */
    suspend fun todayMillis(packageName: String): Long? {
        if (!isGranted()) return null
        return withContext(Dispatchers.IO) {
            val now = clock.now()
            val from = startOfDay(now, ZoneId.systemDefault())
            try {
                val events = usageStats.queryEvents(from, now) ?: return@withContext null
                val event = UsageEvents.Event()
                val own = ArrayList<ForegroundEvent>()
                while (events.getNextEvent(event)) {
                    if (event.packageName != packageName) continue
                    val change =
                        when (event.eventType) {
                            UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundChange.RESUMED
                            UsageEvents.Event.ACTIVITY_PAUSED -> ForegroundChange.PAUSED
                            UsageEvents.Event.ACTIVITY_STOPPED -> ForegroundChange.STOPPED
                            else -> continue
                        }
                    own += ForegroundEvent(event.timeStamp, event.className.orEmpty(), change)
                }
                foregroundMillis(own, from, now)
            } catch (e: RuntimeException) {
                // Acceso retirado entre la comprobación y la consulta, o usuario aún bloqueado.
                Log.w(TAG, "No se ha podido leer el uso de hoy", e)
                null
            }
        }
    }

    /**
     * Uso del teléfono desde la medianoche local: lo que han estado en primer plano todas las apps
     * menos las de [excluded] ([screenMillis]). `null` sin acceso o si el sistema no responde.
     */
    suspend fun todayScreenMillis(excluded: Set<String>): Long? {
        if (!isGranted()) return null
        return withContext(Dispatchers.IO) {
            val now = clock.now()
            val from = startOfDay(now, ZoneId.systemDefault())
            try {
                val events = usageStats.queryEvents(from, now) ?: return@withContext null
                val event = UsageEvents.Event()
                val byPackage = HashMap<String, MutableList<ForegroundEvent>>()
                while (events.getNextEvent(event)) {
                    val change = event.foregroundChange() ?: continue
                    byPackage.getOrPut(event.packageName) { ArrayList() } +=
                        ForegroundEvent(event.timeStamp, event.className.orEmpty(), change)
                }
                screenMillis(byPackage, from, now, excluded)
            } catch (e: RuntimeException) {
                Log.w(TAG, "No se ha podido leer el uso del teléfono", e)
                null
            }
        }
    }

    /**
     * Lo de hoy de [packageName] para la pausa (docs/IDEAS.md §2.4): cuántas veces ha entrado en primer
     * plano ([foregroundEntries]) y cuánto tiempo, según el sistema, también lo abierto fuera de
     * Alisio. `null` sin acceso o si el sistema no responde.
     */
    suspend fun todayVisits(packageName: String): AppToday? {
        if (!isGranted()) return null
        return withContext(Dispatchers.IO) {
            val now = clock.now()
            val from = startOfDay(now, ZoneId.systemDefault())
            try {
                val events = usageStats.queryEvents(from, now) ?: return@withContext null
                val event = UsageEvents.Event()
                val own = ArrayList<ForegroundEvent>()
                while (events.getNextEvent(event)) {
                    if (event.packageName != packageName) continue
                    val change = event.foregroundChange() ?: continue
                    own += ForegroundEvent(event.timeStamp, event.className.orEmpty(), change)
                }
                AppToday(entries = foregroundEntries(own), millis = foregroundMillis(own, from, now))
            } catch (e: RuntimeException) {
                Log.w(TAG, "No se ha podido leer el uso de hoy para la pausa", e)
                null
            }
        }
    }

    /**
     * La última vez que cada paquete pasó a primer plano desde [since], según el sistema: lo que se
     * abre desde recientes o una notificación no pasa por Alisio. Solo en memoria; vacío sin acceso.
     * Desde API 35 la consulta pide solo ese tipo de evento y es mucho más corta.
     */
    suspend fun lastResumed(since: Long): Map<String, Long> {
        if (!isGranted()) return emptyMap()
        return withContext(Dispatchers.IO) {
            val now = clock.now()
            try {
                val events =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        usageStats.queryEvents(
                            UsageEventsQuery.Builder(
                                since,
                                now
                            ).setEventTypes(UsageEvents.Event.ACTIVITY_RESUMED).build()
                        )
                    } else {
                        usageStats.queryEvents(since, now)
                    } ?: return@withContext emptyMap()
                val event = UsageEvents.Event()
                val last = HashMap<String, Long>()
                while (events.getNextEvent(event)) {
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last[event.packageName] = event.timeStamp
                }
                last
            } catch (e: RuntimeException) {
                Log.w(TAG, "No se ha podido leer qué se ha abierto fuera de Alisio", e)
                emptyMap()
            }
        }
    }

    private fun UsageEvents.Event.foregroundChange(): ForegroundChange? = when (eventType) {
        UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundChange.RESUMED
        UsageEvents.Event.ACTIVITY_PAUSED -> ForegroundChange.PAUSED
        UsageEvents.Event.ACTIVITY_STOPPED -> ForegroundChange.STOPPED
        else -> null
    }

    private companion object {
        const val TAG = "UsageAccess"
    }
}
