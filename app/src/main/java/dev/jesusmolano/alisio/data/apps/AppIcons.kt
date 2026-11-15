package dev.jesusmolano.alisio.data.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.createBitmap

/**
 * Icono de lista: el monocromo del icono adaptativo (Android 13+) teñido con [tint] sobre una
 * superficie; si la app no lo trae, su icono normal.
 */
internal fun renderIcon(drawable: Drawable, sizePx: Int, tint: Int, surface: Int): Bitmap {
    val bitmap = createBitmap(sizePx, sizePx)
    val canvas = Canvas(bitmap)
    val monochrome =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && drawable is AdaptiveIconDrawable) {
            drawable.monochrome
        } else {
            null
        }
    if (monochrome == null) {
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }
    val radius = sizePx * CORNER_FRACTION
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = surface }
    canvas.drawRoundRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), radius, radius, paint)
    // La capa mide 108 dp y solo se ven los 72 centrales: se dibuja a 1,5× y centrada.
    val inset = -(sizePx * LAYER_OVERDRAW).toInt()
    monochrome.mutate().apply {
        setTint(tint)
        setBounds(inset, inset, sizePx - inset, sizePx - inset)
        draw(canvas)
    }
    return bitmap
}

private const val CORNER_FRACTION = 0.33f
private const val LAYER_OVERDRAW = 0.25f
