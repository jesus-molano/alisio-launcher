package dev.jesusmolano.alisio.core.trace

/**
 * Si un dedo que sale del índice de Trazo hacia dentro es el gesto «atrás» del sistema y no un trazo
 * (docs/PLAN.md §11, «Gestos sin pisarse»). El índice excluye el gesto atrás del borde derecho, así
 * que One UI no se lo queda: sin esta regla, un «atrás» rápido elegía la letra a la altura del dedo y
 * abría la app que quedara debajo al soltar.
 *
 * Es «atrás» si sale antes de [EDGE_FLING_MILLIS] desde que se apoyó y sin haberse movido en
 * vertical más que [slop]: quien usa Trazo se para a ver la letra o la busca subiendo y bajando.
 *
 * @param elapsedMillis tiempo desde que se apoyó el dedo hasta que sale del índice.
 * @param verticalTravel lo más que se ha alejado en vertical del punto de apoyo mientras estaba en el índice.
 * @param slop distancia que aún cuenta como dedo quieto (el `touchSlop` del sistema).
 */
fun isEdgeFling(elapsedMillis: Long, verticalTravel: Float, slop: Float): Boolean =
    elapsedMillis < EDGE_FLING_MILLIS && verticalTravel <= slop

/**
 * Medido en el S26 con `input swipe` a velocidad constante (docs/PLAN.md §11): un «atrás» de 300 ms
 * salía del índice (48 dp) a los 125 ms y abría una app; uno de 120 ms ya lo frenaba el armado de la
 * rejilla. 200 ms deja margen a un «atrás» lento sin pedir mucha pausa a quien toca una letra para
 * ver sus apps. Falta confirmarlo con un pulgar real, que acelera y frena.
 */
const val EDGE_FLING_MILLIS = 200L
