package dev.jesusmolano.alisio.core.trace

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Cuándo se ve el índice de Trazo en el borde derecho.
 *
 * @param id lo que se guarda en las preferencias; no cambia aunque cambie el nombre del valor.
 */
enum class TraceRail(val id: String) {
    /** Las letras se ven siempre. */
    ALWAYS("always"),

    /** El borde responde igual, pero las letras solo salen mientras el dedo está en él. */
    EDGE("edge"),

    /** Sin índice: el borde derecho es del sistema (gesto atrás) y a las apps se llega por la barra o Zonas. */
    NEVER("never");

    companion object {
        /** Un valor que no se reconoce (preferencia de otra versión, archivo dañado) cae en [ALWAYS]. */
        fun parse(id: String?): TraceRail = entries.firstOrNull { it.id == id } ?: ALWAYS
    }
}

/**
 * El índice en reposo: una franja vertical con [count] letras repartidas por igual entre [top] y
 * [bottom], en píxeles. Elige la letra en la que empieza un trazo; después manda [TraceBand].
 */
class RestRail(val count: Int, val top: Float, val bottom: Float) {
    val pitch: Float = if (count > 0) (bottom - top) / count else 0f

    fun centerOf(index: Int): Float = top + (index + 0.5f) * pitch

    /** La letra bajo [y]; fuera de la franja, la del extremo más cercano. */
    fun letterAt(y: Float): Int = if (count == 0 || pitch <= 0f) {
        0
    } else {
        floor((y - top) / pitch).toInt().coerceIn(0, count - 1)
    }

    /**
     * Cada cuántas letras se escribe una cuando no caben todas a [minLabelPitch]: las demás se
     * dibujan como un punto. 1 es que caben todas.
     */
    fun labelStep(minLabelPitch: Float): Int = if (pitch <= 0f) 1 else max(1, ceil(minLabelPitch / pitch).toInt())

    /**
     * Las letras que se escriben en reposo con un paso de [step]; las demás son un punto. Se escriben
     * siempre [first] (la primera letra de verdad: con «#» delante sería la «A») y la última, que son
     * los extremos que el pulgar busca, y entre ellas se reparten por igual: nunca dos escritas a
     * menos de [step], ni dos puntos de más antes de la última. Lo que va antes de [first] («#») solo
     * se escribe si caben todas.
     */
    fun labels(step: Int, first: Int = 0): Set<Int> {
        if (count == 0) return emptySet()
        if (step <= 1) return (0 until count).toSet()
        val start = first.coerceIn(0, count - 1)
        val span = count - 1 - start
        val gaps = span / step
        if (gaps == 0) return setOf(start, count - 1)
        return (0..gaps).mapTo(HashSet()) { start + (it * span + gaps / 2) / gaps }
    }

    companion object {
        /**
         * Dónde va el índice en reposo. Se pega abajo (al pie del Inicio, cerca del pulgar) y sube
         * solo lo que piden sus letras a [maxPitch]. No pasa de [reachTop] ni de [softTop] mientras
         * las letras quepan a [minPitch]; si no caben, sube lo justo, sin pasar nunca de [hardTop].
         */
        fun place(
            count: Int,
            softTop: Float,
            hardTop: Float,
            bottom: Float,
            reachTop: Float,
            maxPitch: Float,
            minPitch: Float
        ): RestRail {
            var top = bottom - count * maxPitch
            top = max(top, max(softTop, reachTop))
            if (bottom - top < count * minPitch) top = bottom - count * minPitch
            top = max(top, hardTop).coerceAtMost(bottom)
            return RestRail(count, top, bottom)
        }
    }
}
