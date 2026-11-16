package dev.jesusmolano.alisio.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.core.people.initials
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

/**
 * Avatar de una persona, igual en las filas de la barra, en su cabecera y en los ajustes: su
 * [photo], que llega ya recortada en círculo, o sus iniciales. Decorativo: el nombre va siempre al lado.
 */
@Composable
fun Avatar(name: String, size: Dp, modifier: Modifier = Modifier, photo: Bitmap? = null) {
    if (photo != null) {
        AppIcon(photo, size, modifier)
        return
    }
    Box(
        modifier.size(size).background(AlisioTokens.Fill, CircleShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials(name),
            style = if (size >= 56.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = AlisioTokens.Text
        )
    }
}
