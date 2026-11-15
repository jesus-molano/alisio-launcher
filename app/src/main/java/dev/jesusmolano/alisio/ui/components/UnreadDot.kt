package dev.jesusmolano.alisio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

/**
 * Punto de acento en la esquina de un icono: la app tiene algo sin leer (§6). Decorativo; quien lo
 * usa lo cuenta en la descripción del icono.
 *
 * @param ring color de lo que hay detrás: un aro fino lo separa del icono, que puede ser del mismo tono.
 */
@Composable
fun BoxScope.UnreadDot(ring: Color = AlisioTokens.Background) {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            // Asoma un poco por fuera de la esquina redondeada, donde el icono ya no tapa.
            .offset(x = 3.dp, y = (-3).dp)
            .size(13.dp)
            .background(AlisioTokens.Accent, CircleShape)
            .border(2.dp, ring, CircleShape)
    )
}
