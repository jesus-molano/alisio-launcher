package dev.jesusmolano.alisio.data.usage

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.core.usage.ForegroundChange
import dev.jesusmolano.alisio.core.usage.ForegroundEvent
import dev.jesusmolano.alisio.core.usage.foregroundMillis
import dev.jesusmolano.alisio.core.usage.startOfDay
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

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

    private companion object {
        const val TAG = "UsageAccess"
    }
}
