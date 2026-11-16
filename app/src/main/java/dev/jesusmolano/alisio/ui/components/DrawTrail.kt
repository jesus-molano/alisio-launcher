package dev.jesusmolano.alisio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

// El rastro es fino, como un trazo de lápiz; la sombra, el doble, para que se vea sobre un fondo claro.
private val TrailWidth = 3.dp
private val TrailShadowWidth = 7.dp
private val TrailColor = AlisioTokens.Text.copy(alpha = 0.85f)
private val TrailShadow = Color.Black.copy(alpha = 0.22f)

/**
 * El rastro del dedo mientras se dibuja una letra en el Inicio (o una muestra en los ajustes). No
 * recibe toques: solo dibuja [points], en coordenadas de quien lo pinta.
 */
@Composable
fun DrawTrail(points: List<Offset>, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    Canvas(modifier.fillMaxSize()) {
        val path =
            Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
        drawPath(
            path,
            TrailShadow,
            style = Stroke(TrailShadowWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(path, TrailColor, style = Stroke(TrailWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
