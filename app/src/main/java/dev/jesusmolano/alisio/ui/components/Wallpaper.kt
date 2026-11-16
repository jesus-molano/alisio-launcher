package dev.jesusmolano.alisio.ui.components

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import dev.jesusmolano.alisio.core.wallpaper.VEIL_MIDDLE
import dev.jesusmolano.alisio.core.wallpaper.previewVeilStops
import dev.jesusmolano.alisio.core.wallpaper.veilStops
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

/**
 * El velo que oscurece el fondo de pantalla para que el texto claro se lea (docs/DISENO.md §3): un
 * degradado vertical del color de fondo, más denso abajo. Lo usan el Inicio y sus vistas previas.
 *
 * @param dim factor de la opción elegida en los ajustes.
 * @param lightWallpaper lo que devuelve [rememberLightWallpaper].
 * @param overHome el velo se pinta encima del Inicio real, que ya lleva el suyo (la miniatura de los
 *   ajustes): solo añade lo que falta para que el conjunto se vea como el Inicio.
 */
fun Modifier.wallpaperVeil(dim: Float, lightWallpaper: Boolean, overHome: Boolean = false): Modifier {
    val stops = if (overHome) previewVeilStops(dim, lightWallpaper) else veilStops(dim, lightWallpaper)
    return drawBehind {
        drawRect(
            Brush.verticalGradient(
                0f to AlisioTokens.Background.copy(alpha = stops.top),
                VEIL_MIDDLE to AlisioTokens.Background.copy(alpha = stops.middle),
                1f to AlisioTokens.Background.copy(alpha = stops.bottom)
            )
        )
    }
}

/**
 * Si el fondo de pantalla es claro, según el sistema (su pista de «admite texto oscuro»). Se entera
 * cuando el usuario cambia de fondo. Sin datos (fondo animado, lectura fallida) se toma por oscuro:
 * el velo se queda como lo elija el usuario.
 */
@Composable
fun rememberLightWallpaper(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { WallpaperManager.getInstance(context) }
    var light by remember { mutableStateOf(manager.systemColors().supportsDarkText()) }
    DisposableEffect(manager) {
        val listener =
            WallpaperManager.OnColorsChangedListener { colors, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) light = colors.supportsDarkText()
            }
        manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager.removeOnColorsChangedListener(listener) }
    }
    return light
}

private fun WallpaperManager.systemColors(): WallpaperColors? = try {
    getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
} catch (_: RuntimeException) {
    // Algún sistema falla al leer los colores de un fondo que no puede decodificar.
    null
}

private fun WallpaperColors?.supportsDarkText(): Boolean =
    this != null && colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
