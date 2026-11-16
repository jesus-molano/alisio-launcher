package dev.jesusmolano.alisio.data.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import dev.jesusmolano.alisio.core.icons.IconStyle
import dev.jesusmolano.alisio.core.icons.centerSquare

/** Lado de los iconos de app tal como se dibujan y se guardan: el del dock (`AlisioTokens.DockIconSize`). */
internal const val ICON_SIZE_DP = 52

/**
 * Dibuja el icono de una app según [style].
 *
 * @param packIcon el icono que el paquete de iconos tiene para esta app, si lo hay; manda sobre [style].
 * @param tint color de la capa monocroma; [surface] es el fondo donde hace falta uno.
 */
internal fun renderIcon(
    drawable: Drawable,
    packIcon: Drawable?,
    style: IconStyle,
    sizePx: Int,
    tint: Int,
    surface: Int
): Bitmap {
    val bitmap = createBitmap(sizePx, sizePx)
    val canvas = Canvas(bitmap)
    when {
        // Los paquetes traen el icono ya con su forma: se dibuja tal cual.
        packIcon != null -> drawWhole(canvas, packIcon, sizePx)

        style == IconStyle.System -> drawWhole(canvas, drawable, sizePx)

        style == IconStyle.Monochrome -> drawMonochrome(canvas, drawable, sizePx, tint, surface)

        else -> drawUniform(canvas, drawable, sizePx, surface)
    }
    return bitmap
}

/** Un dibujo de un paquete de iconos elegido para una sola app: ya trae su forma y va tal cual. */
internal fun renderPackIcon(packIcon: Drawable, sizePx: Int): Bitmap {
    val bitmap = createBitmap(sizePx, sizePx)
    drawWhole(Canvas(bitmap), packIcon, sizePx)
    return bitmap
}

/**
 * Una imagen de la galería como icono: su cuadrado central, a [sizePx] y recortado a la forma común
 * de los iconos ([roundedSquare]), para que no desentone junto a los demás.
 */
internal fun renderImageIcon(image: Bitmap, sizePx: Int): Bitmap {
    val square = centerSquare(image.width, image.height)
    val bitmap = createBitmap(sizePx, sizePx)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    Canvas(bitmap).withClip(roundedSquare(sizePx)) {
        drawBitmap(
            image,
            Rect(square.left, square.top, square.left + square.size, square.top + square.size),
            Rect(0, 0, sizePx, sizePx),
            paint
        )
    }
    return bitmap
}

private fun drawWhole(canvas: Canvas, drawable: Drawable, sizePx: Int) {
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(canvas)
}

/** Los adaptativos se recortan al cuadrado redondeado; los antiguos, con silueta propia, se encajan sobre [surface]. */
private fun drawUniform(canvas: Canvas, drawable: Drawable, sizePx: Int, surface: Int) {
    canvas.withClip(roundedSquare(sizePx)) {
        if (drawable is AdaptiveIconDrawable) {
            listOfNotNull(drawable.background, drawable.foreground).forEach { drawLayer(this, it, sizePx) }
        } else {
            drawPaint(Paint().apply { color = surface })
            val inset = (sizePx * LEGACY_INSET).toInt()
            drawable.setBounds(inset, inset, sizePx - inset, sizePx - inset)
            drawable.draw(this)
        }
    }
}

private fun drawMonochrome(canvas: Canvas, drawable: Drawable, sizePx: Int, tint: Int, surface: Int) {
    val monochrome =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && drawable is AdaptiveIconDrawable) {
            drawable.monochrome
        } else {
            null
        }
    if (monochrome != null) {
        canvas.withClip(roundedSquare(sizePx)) {
            drawPaint(Paint().apply { color = surface })
            drawLayer(this, monochrome.mutate().apply { setTint(tint) }, sizePx)
        }
        return
    }
    // Sin capa temática: el icono a color pasado a grises, para que no desentone con los demás.
    val color = createBitmap(sizePx, sizePx)
    drawUniform(Canvas(color), drawable, sizePx, surface)
    val gray = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }
    canvas.drawBitmap(color, 0f, 0f, gray)
}

/** Cada capa adaptativa mide 108 dp y solo se ven los 72 centrales: se dibuja a 1,5× y centrada. */
private fun drawLayer(canvas: Canvas, layer: Drawable, sizePx: Int) {
    val inset = -(sizePx * LAYER_OVERDRAW).toInt()
    layer.setBounds(inset, inset, sizePx - inset, sizePx - inset)
    layer.draw(canvas)
}

private fun roundedSquare(sizePx: Int): Path {
    val size = sizePx.toFloat()
    val radius = size * CORNER_FRACTION
    return Path().apply { addRoundRect(0f, 0f, size, size, radius, radius, Path.Direction.CW) }
}

private const val CORNER_FRACTION = 0.33f
private const val LAYER_OVERDRAW = 0.25f
private const val LEGACY_INSET = 0.12f
