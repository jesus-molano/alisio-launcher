package dev.jesusmolano.alisio.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Vocabulario háptico de Alisio (docs/IDEAS.md §2.1): cada vibración tiene un significado fijo y
 * se pide por él, no por la constante. Las constantes semánticas de Android 14 (API 34) van con su
 * comprobación de versión porque `minSdk` es 31; por debajo queda la de siempre. Todas respetan el
 * ajuste de vibración táctil del sistema, porque pasan por [View.performHapticFeedback].
 */
object AlisioHaptics {
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private val semantic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /** Paso: cambiar de letra o de app en Trazo, o pasar sobre una isla que acepta la app arrastrada. */
    fun step(view: View) {
        view.performHapticFeedback(
            if (semantic) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
        )
    }

    /** Hecho: el gesto ha terminado en una acción (soltar sobre una app, Enter, soltar en una isla). */
    fun done(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    /** Límite: lo pedido no se puede hacer (soltar donde no cabe, un gesto que el sistema ignora). */
    fun limit(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    /**
     * Umbral de un deslizar: [armed] al cruzarlo (soltar ya hace algo) y `false` al volver atrás. Así
     * se nota también cuándo se ha desarmado, no solo cuándo se ha armado.
     */
    fun threshold(view: View, armed: Boolean) {
        val constant =
            if (semantic) {
                if (armed) {
                    HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
                } else {
                    HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
                }
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
        view.performHapticFeedback(constant)
    }

    /** Empieza un arrastre: una app de Zonas o una app de una pila se despega de su sitio. */
    fun dragStart(view: View) {
        view.performHapticFeedback(
            if (semantic) HapticFeedbackConstants.DRAG_START else HapticFeedbackConstants.LONG_PRESS
        )
    }

    /** Un interruptor cambia: encendido y apagado suenan distinto donde el sistema lo permite. */
    fun toggle(view: View, on: Boolean) {
        val constant =
            if (semantic) {
                if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
            } else {
                HapticFeedbackConstants.CLOCK_TICK
            }
        view.performHapticFeedback(constant)
    }
}
