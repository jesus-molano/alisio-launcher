package dev.jesusmolano.alisio.ui.components

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.ui.theme.AlisioHaptics
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

// Relleno interior de una fila: con el margen exterior `Outset`, su contenido empieza en el filo.
private val RowInset = 12.dp
private val RowVectorSize = 24.dp
private val TrailingMarkSize = 20.dp
private val TitleMarkSize = 16.dp

/** Icono de app ya renderizado. Decorativo: quien lo usa pone el nombre al lado o debajo. */
@Composable
fun AppIcon(icon: Bitmap, size: Dp, modifier: Modifier = Modifier) {
    Image(
        bitmap = remember(icon) { icon.asImageBitmap() },
        contentDescription = null,
        modifier = modifier.size(size)
    )
}

/**
 * Etiqueta de un grupo de filas (docs/DISENO.md §4.2). Lleva su aire: quien la usa no añade
 * separadores. TalkBack la trata como encabezado, para saltar de grupo en grupo.
 *
 * @param first la primera de una pantalla o de una hoja no lleva el aire de separación de grupos.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, first: Boolean = false) {
    Text(
        text = text,
        style = AlisioTokens.SectionLabel,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .padding(
                    start = RowInset,
                    end = RowInset,
                    top = if (first) 0.dp else AlisioTokens.GroupGap,
                    bottom = AlisioTokens.LabelGap
                )
                .semantics { heading() }
    )
}

/** Texto de apoyo entre las filas de un grupo, alineado con ellas. */
@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = AlisioTokens.TextSecondary,
        modifier = modifier.padding(horizontal = RowInset, vertical = 4.dp)
    )
}

/**
 * La fila de la app (docs/DISENO.md §4.3): resultado, opción de ajustes o elemento de una hoja. Sin
 * fondo ni borde; solo el destino de Enter lleva relleno.
 *
 * Al principio va, como mucho, una de estas cosas: [avatarName] (con [icon] como foto), [icon] o
 * [leadingIcon]. Al final va, como mucho, una de estas: [checked], [selected], [enterTarget] o
 * [trailing]; [actions] son botones propios y pueden acompañar a cualquiera.
 *
 * @param avatarName nombre de una persona: la fila enseña su avatar, con [icon] como foto si la hay.
 * @param titleMark dibujo pequeño pegado al título, como la marca de que la fila abre una pantalla
 *   del sistema.
 * @param subtitleLines 1 en listas de resultados; 2 donde el secundario dice un estado completo.
 * @param trailing una acción con verbo («Permitir», «Cambiar»), nunca un estado.
 * @param enterTarget es lo que abre la tecla Enter. Solo una fila a la vista lo lleva.
 * @param checked la fila es un interruptor y [onClick] lo cambia.
 * @param selected la fila es una opción de una lista y [onClick] la elige.
 * @param multiple con [selected], se pueden marcar varias: TalkBack la lee como casilla.
 * @param enabled sin requisito cumplido la fila se atenúa y no responde.
 * @param secondary una acción destructiva: va en el color secundario, no en rojo.
 * @param numeral el título es una cifra que se mira, como el resultado de una cuenta.
 * @param onClickLabel el verbo que TalkBack dice al tocar («Abrir», «Copiar»). Solo en filas sin
 *   [checked] ni [selected], que ya dicen lo que hacen con su papel.
 * @param actions botones propios de la fila, al final: tocar uno no dispara [onClick].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemRow(
    title: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Bitmap? = null,
    avatarName: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    @DrawableRes titleMark: Int? = null,
    subtitle: String? = null,
    subtitleLines: Int = 1,
    trailing: String? = null,
    enterTarget: Boolean = false,
    checked: Boolean? = null,
    selected: Boolean? = null,
    multiple: Boolean = false,
    enabled: Boolean = true,
    secondary: Boolean = false,
    numeral: Boolean = false,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val shape = AlisioTokens.RowShape
    val view = LocalView.current
    val interaction =
        when {
            checked != null ->
                Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch) { on ->
                    AlisioHaptics.toggle(view, on)
                    onClick()
                }

            selected != null && multiple ->
                Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onClick() }

            selected != null ->
                Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)

            else ->
                Modifier.combinedClickable(
                    enabled = enabled,
                    onClickLabel = onClickLabel,
                    onLongClickLabel = onLongClickLabel,
                    onLongClick = onLongClick,
                    onClick = onClick
                )
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = AlisioTokens.RowMinHeight)
                .clip(shape)
                .then(if (enterTarget) Modifier.background(AlisioTokens.AccentSoft, shape) else Modifier)
                .then(interaction)
                .alpha(if (enabled) 1f else AlisioTokens.DISABLED_ALPHA)
                .padding(horizontal = RowInset, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatarName != null || icon != null || leadingIcon != null) {
            Box(Modifier.size(AlisioTokens.IconSlot), contentAlignment = Alignment.Center) {
                when {
                    avatarName != null -> Avatar(avatarName, AlisioTokens.IconSlot, photo = icon)

                    icon != null -> AppIcon(icon, AlisioTokens.IconSlot)

                    // Un vector va suelto, sin recuadro: la caja alrededor no dice nada.
                    leadingIcon != null ->
                        Icon(
                            painter = painterResource(leadingIcon),
                            contentDescription = null,
                            tint = AlisioTokens.TextSecondary,
                            modifier = Modifier.size(RowVectorSize)
                        )
                }
            }
            Spacer(Modifier.width(AlisioTokens.IconGap))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = if (numeral) AlisioTokens.Numeral else MaterialTheme.typography.titleMedium,
                    color = if (secondary) AlisioTokens.TextSecondary else AlisioTokens.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (titleMark != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(titleMark),
                        contentDescription = null,
                        tint = AlisioTokens.TextSecondary,
                        modifier = Modifier.size(TitleMarkSize)
                    )
                }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlisioTokens.TextSecondary,
                    maxLines = subtitleLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (actions != null) actions()
        when {
            checked != null -> {
                Spacer(Modifier.width(12.dp))
                // Sin acción propia: el interruptor es de toda la fila.
                Switch(checked = checked, onCheckedChange = null, colors = switchColors())
            }

            selected == true -> TrailingMark(R.drawable.ic_check)

            // Sin nombre para TalkBack: lo que hará la fila lo dice [onClickLabel].
            enterTarget -> TrailingMark(R.drawable.ic_keyboard_return)

            trailing != null -> {
                Spacer(Modifier.width(12.dp))
                Text(text = trailing, style = MaterialTheme.typography.labelLarge, color = AlisioTokens.Accent)
            }
        }
    }
}

/** La marca de acento al final de una fila: lo elegido o lo que hace Enter. */
@Composable
private fun TrailingMark(@DrawableRes icon: Int) {
    Spacer(Modifier.width(12.dp))
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = AlisioTokens.Accent,
        modifier = Modifier.size(TrailingMarkSize)
    )
    Spacer(Modifier.width(4.dp))
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedTrackColor = AlisioTokens.Accent,
    checkedThumbColor = AlisioTokens.Background,
    uncheckedTrackColor = AlisioTokens.Fill,
    uncheckedThumbColor = AlisioTokens.TextSecondary,
    uncheckedBorderColor = Color.Transparent
)
