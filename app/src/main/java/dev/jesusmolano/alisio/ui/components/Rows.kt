package dev.jesusmolano.alisio.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

/** Icono de app ya renderizado. Decorativo: quien lo usa pone el nombre al lado o debajo. */
@Composable
fun AppIcon(icon: Bitmap, size: Dp, modifier: Modifier = Modifier) {
    Image(
        bitmap = remember(icon) { icon.asImageBitmap() },
        contentDescription = null,
        modifier = modifier.size(size)
    )
}

/** Etiqueta de sección de §6; el texto se pasa ya en mayúsculas. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AlisioTokens.SectionLabel,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

/**
 * Fila de resultado o de lista: icono, título, secundario y algo al final.
 *
 * @param highlighted la marca como mejor resultado: borde y fondo de acento.
 * @param leading lo que va en lugar del icono cuando no hay uno (ajustes, cálculo).
 */
@Composable
fun ItemRow(
    title: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Bitmap? = null,
    leading: String? = null,
    subtitle: String? = null,
    trailing: String? = null,
    highlighted: Boolean = false
) {
    val shape = AlisioTokens.RowShape
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clip(shape)
                .then(
                    if (highlighted) {
                        Modifier.background(AlisioTokens.AccentSoft, shape).border(1.dp, AlisioTokens.Accent, shape)
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            AppIcon(icon, 44.dp)
        } else {
            Box(
                Modifier.size(44.dp).background(AlisioTokens.SurfaceElevated, AlisioTokens.DockIconShape),
                contentAlignment = Alignment.Center
            ) {
                // Decorativo: sin esto TalkBack leería el símbolo antes del título.
                Text(
                    text = leading.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = AlisioTokens.Accent,
                    modifier = Modifier.clearAndSetSemantics {}
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = if (highlighted) AlisioTokens.Accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Píldora pequeña para una acción o una opción. [selected] la marca con el acento. */
@Composable
fun Chip(label: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    val shape = AlisioTokens.PillShape
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) AlisioTokens.AccentSoft else AlisioTokens.Surface, shape)
            .border(1.dp, if (selected) AlisioTokens.Accent else AlisioTokens.ControlBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = AlisioTokens.Text)
    }
}
