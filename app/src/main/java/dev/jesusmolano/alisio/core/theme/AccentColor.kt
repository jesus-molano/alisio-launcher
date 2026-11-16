package dev.jesusmolano.alisio.core.theme

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/** Índice de acento que dice «el color propio» en lugar de uno de los cuatro de la paleta. */
const val CUSTOM_ACCENT = -1

/**
 * Contraste mínimo del texto sobre el acento (WCAG AA para texto normal). El texto que va sobre el
 * acento es el del botón principal, y tiene que leerse sin esfuerzo.
 */
const val ACCENT_MIN_CONTRAST = 4.5

/** Un color en tono (0 a 360), saturación y brillo (0 a 1), que es como se elige a mano. */
data class Hsv(val hue: Float, val saturation: Float, val value: Float)

/** De [Hsv] a ARGB opaco. */
fun Hsv.toArgb(): Int {
    val h = ((hue % 360f) + 360f) % 360f / 60f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val chroma = v * s
    val x = chroma * (1 - abs(h % 2 - 1))
    val (r, g, b) =
        when (floor(h).toInt()) {
            0 -> Triple(chroma, x, 0f)
            1 -> Triple(x, chroma, 0f)
            2 -> Triple(0f, chroma, x)
            3 -> Triple(0f, x, chroma)
            4 -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }
    val m = v - chroma
    return argb(r + m, g + m, b + m)
}

/** De ARGB a [Hsv]. Un gris no tiene tono: sale 0. */
fun hsvOf(argb: Int): Hsv {
    val r = channel(argb, RED_SHIFT)
    val g = channel(argb, GREEN_SHIFT)
    val b = channel(argb, BLUE_SHIFT)
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    val hue =
        when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
    return Hsv((hue + 360f) % 360f, if (max == 0f) 0f else delta / max, max)
}

/** Luminancia relativa de WCAG 2: de 0 (negro) a 1 (blanco). */
fun relativeLuminance(argb: Int): Double {
    fun linear(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    return 0.2126 * linear(channel(argb, RED_SHIFT)) +
        0.7152 * linear(channel(argb, GREEN_SHIFT)) +
        0.0722 * linear(channel(argb, BLUE_SHIFT))
}

/** Contraste de WCAG 2 entre dos colores: de 1 (iguales) a 21 (negro sobre blanco). */
fun contrastRatio(a: Int, b: Int): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** Si [text] se lee sobre [accent] con el contraste que pide [ACCENT_MIN_CONTRAST]. */
fun isLegibleAccent(accent: Int, text: Int): Boolean = contrastRatio(accent, text) >= ACCENT_MIN_CONTRAST

/**
 * El color más cercano a [argb] sobre el que [text] (oscuro) se lee: conserva el tono y sube el
 * brillo; si ni con el brillo entero basta, quita saturación. Un color que ya se lee vuelve igual.
 */
fun legibleAccent(argb: Int, text: Int): Int {
    if (isLegibleAccent(argb, text)) return argb
    val start = hsvOf(argb)
    for (step in 1..STEPS) {
        val candidate = start.copy(value = start.value + (1f - start.value) * step / STEPS).toArgb()
        if (isLegibleAccent(candidate, text)) return candidate
    }
    for (step in 1..STEPS) {
        val candidate = Hsv(start.hue, start.saturation * (1f - step.toFloat() / STEPS), 1f).toArgb()
        if (isLegibleAccent(candidate, text)) return candidate
    }
    return WHITE
}

/**
 * Propuestas de acento a partir de los colores principales del fondo de pantalla, ya legibles con
 * [text]: hasta [limit], sin dos casi iguales. Un color sin apenas saturación (un gris) no da un
 * acento que se distinga del texto, así que no se propone.
 */
fun accentProposals(wallpaperColors: List<Int>, text: Int, limit: Int = 3): List<Int> {
    val result = ArrayList<Int>()
    for (color in wallpaperColors) {
        if (hsvOf(color).saturation < MIN_PROPOSAL_SATURATION) continue
        val legible = legibleAccent(color, text)
        if (result.none { near(it, legible) }) result += legible
        if (result.size == limit) break
    }
    return result
}

/** Dos acentos que a la vista son el mismo: tono a menos de 12° y brillo y saturación parecidos. */
private fun near(a: Int, b: Int): Boolean {
    val x = hsvOf(a)
    val y = hsvOf(b)
    val hue = abs(x.hue - y.hue).let { minOf(it, 360f - it) }
    return hue < NEAR_HUE && abs(x.saturation - y.saturation) < NEAR_AMOUNT && abs(x.value - y.value) < NEAR_AMOUNT
}

/** El color como «#RRGGBB», para leerlo y para TalkBack. */
fun hexOf(argb: Int): String = "#%06X".format(argb and RGB_MASK)

private fun channel(argb: Int, shift: Int): Float = ((argb shr shift) and CHANNEL_MASK) / 255f

private fun argb(r: Float, g: Float, b: Float): Int {
    fun byte(c: Float) = (c.coerceIn(0f, 1f) * 255f).roundToInt()
    return OPAQUE or (byte(r) shl RED_SHIFT) or (byte(g) shl GREEN_SHIFT) or byte(b)
}

private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val BLUE_SHIFT = 0
private const val CHANNEL_MASK = 0xFF
private const val RGB_MASK = 0xFFFFFF
private const val OPAQUE = 0xFF shl 24
private const val WHITE = -1
private const val STEPS = 40
private const val MIN_PROPOSAL_SATURATION = 0.12f
private const val NEAR_HUE = 12f
private const val NEAR_AMOUNT = 0.15f
