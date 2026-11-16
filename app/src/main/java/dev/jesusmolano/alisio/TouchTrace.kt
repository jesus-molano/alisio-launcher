package dev.jesusmolano.alisio

import android.os.SystemClock
import android.util.Log

/**
 * Marcas del primer toque y del teclado (docs/IDEAS.md §3.1, P1) en logcat
 * (`adb logcat -s AlisioTouch`): milisegundos desde la última vuelta al Inicio. Esa ventana de
 * tiempo no se puede comprobar con un test de Compose, así que se mide en release con estas marcas,
 * como el arranque con [StartupTrace].
 */
object TouchTrace {
    private const val TAG = "AlisioTouch"

    // Solo se escriben y se leen en el hilo principal: la actividad y Compose.
    private var returnedAt = 0L
    private var touchPending = false

    /** El Inicio vuelve a verse ([how]: desde otra app o pulsando «inicio» estando ya en él). */
    fun returned(how: String) {
        returnedAt = SystemClock.uptimeMillis()
        touchPending = true
        Log.i(TAG, "vuelta al Inicio ($how)")
    }

    /** Un dedo toca la ventana: solo cuenta el primero tras cada vuelta. */
    fun touched() {
        if (!touchPending) return
        touchPending = false
        Log.i(TAG, "primer toque: ${since()} ms")
    }

    /** Un paso con su tiempo desde la vuelta; [took] añade lo que ha durado el propio paso. */
    fun mark(step: String, took: Long? = null) {
        val duration = took?.let { ", $it ms" }.orEmpty()
        Log.i(TAG, "$step: ${since()} ms desde la vuelta$duration")
    }

    private fun since(): Long = SystemClock.uptimeMillis() - returnedAt
}
