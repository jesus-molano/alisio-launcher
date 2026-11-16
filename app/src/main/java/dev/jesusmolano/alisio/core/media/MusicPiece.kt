package dev.jesusmolano.alisio.core.media

/**
 * Cuándo enseñar la pieza «Música» (docs/PLAN.md §11, «Música siempre a mano»): `null` la oculta ya;
 * un número, los milisegundos que hay que esperar sin sonar nada antes de enseñarla.
 *
 * Al tocarla, la app tarda en sonar y su sesión pasa por estados intermedios (en pausa, cargando,
 * cambio de canción). Si la pieza siguiera cada uno, se quitaría, volvería y se quitaría otra vez
 * mientras entra el reproductor. Por eso se oculta desde el toque ([starting]) y, una vez oculta
 * ([hiddenBefore]), solo vuelve tras [MUSIC_PIECE_GRACE_MILLIS] seguidos sin música.
 *
 * Sin escuchar las sesiones ([watching], sin acceso a notificaciones) no se sabe si suena algo: la
 * pieza sale siempre, como si no sonara nada. Si se ocultara al tocarla, volvería sola a los
 * [MUSIC_PIECE_GRACE_MILLIS] con la música ya sonando.
 *
 * @param playing si suena algo, de la app que sea.
 * @param starting si hay una orden de reanudar en marcha.
 * @param hiddenBefore si la pieza estaba oculta: al arrancar el Inicio sin música sale sin esperar.
 */
fun musicPieceDelay(watching: Boolean, playing: Boolean, starting: Boolean, hiddenBefore: Boolean): Long? = when {
    !watching -> 0L
    playing || starting -> null
    hiddenBefore -> MUSIC_PIECE_GRACE_MILLIS
    else -> 0L
}

/**
 * Pensado para cubrir lo que tarda una app en pasar de una canción a otra o en cargar tras el toque;
 * aún sin medir en el S26 (docs/PLAN.md §11). Pausar a mano también espera esto: el reproductor ya
 * enseña la pausa, así que la pieza no hace falta antes.
 */
const val MUSIC_PIECE_GRACE_MILLIS = 2_500L
