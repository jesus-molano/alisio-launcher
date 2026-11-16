package dev.jesusmolano.alisio.core.dock

import kotlin.math.floor

/** Cuántos huecos caben en la base de datos: el dock enseña de 0 a este número. */
const val MAX_DOCK_SLOTS = 8

/** Los huecos de siempre (docs/PLAN.md §3). */
const val DEFAULT_DOCK_SLOTS = 6

/** Lo que mide un icono del dock con pocos huecos, y lo menos que puede medir con muchos. */
const val DOCK_ICON_MAX_DP = 52
const val DOCK_ICON_MIN_DP = 36

/** Aire que cada hueco deja a los lados de su icono, para que el nombre respire. */
const val DOCK_ITEM_SPARE_DP = 8

/** Lo que guarda la pantalla a cada lado del dock: el filo menos la mitad del aire de un hueco. */
private const val DOCK_SIDE_DP = 24 - DOCK_ITEM_SPARE_DP / 2

/** Lo guardado, siempre dentro de lo que se puede enseñar. */
fun normalizeDockSlots(stored: Int?): Int = (stored ?: DEFAULT_DOCK_SLOTS).coerceIn(0, MAX_DOCK_SLOTS)

/**
 * El lado del icono para [count] huecos en una pantalla de [widthDp]: 52 dp mientras quepan con su
 * aire y, si no, el mayor número par que quepa (los iconos se dibujan nítidos en la rejilla de
 * 2 dp), sin bajar de [DOCK_ICON_MIN_DP]. En el S26 (412 dp) salen 52 con 6, 44 con 7 y 38 con 8.
 */
fun dockIconSize(count: Int, widthDp: Float): Int {
    if (count <= 0) return DOCK_ICON_MAX_DP
    val room = (widthDp - 2 * DOCK_SIDE_DP) / count - DOCK_ITEM_SPARE_DP
    val even = (floor(room / 2f) * 2).toInt()
    return even.coerceIn(DOCK_ICON_MIN_DP, DOCK_ICON_MAX_DP)
}

/**
 * Si el dock enseña los nombres. Lo elegido manda; sin elegir, solo hasta seis huecos: con siete u
 * ocho el nombre no cabe bajo un icono más pequeño y se cortaría en casi todas las apps.
 */
fun dockLabelsShown(chosen: Boolean?, count: Int): Boolean = chosen ?: (count <= DEFAULT_DOCK_SLOTS)
