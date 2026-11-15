package dev.jesusmolano.alisio

import android.os.Process
import android.os.SystemClock
import android.util.Log

/**
 * Marcas del arranque en logcat (`adb logcat -s AlisioStartup`): milisegundos desde que el sistema
 * creó el proceso hasta cada paso. `am start -W` solo mide hasta el primer fotograma y no dice nada
 * con el teléfono bloqueado; estas marcas dicen además cuándo están las preferencias y los iconos,
 * que es cuando el Inicio sirve para algo (docs/PLAN.md §11, fase 5).
 */
object StartupTrace {
    private const val TAG = "AlisioStartup"

    fun mark(step: String) {
        Log.i(TAG, "$step: ${SystemClock.uptimeMillis() - Process.getStartUptimeMillis()} ms")
    }
}
