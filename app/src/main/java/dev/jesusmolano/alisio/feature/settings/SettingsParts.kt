package dev.jesusmolano.alisio.feature.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.ui.components.DockIcon
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

// Ancho de una app en una rejilla de los ajustes: su icono y sitio para el nombre.
private val AppTileWidth = AlisioTokens.DockIconSize + 20.dp

// Con esto el primer icono de una rejilla cae en el filo, como el primer dibujo de una fila.
private val AppGridInset = AlisioTokens.Edge - AlisioTokens.Outset - (AppTileWidth - AlisioTokens.DockIconSize) / 2

/**
 * Una opción con interruptor que depende de un acceso (docs/DISENO.md §2.6, «sin requisito»). Sin
 * el acceso, la fila se atenúa y no enseña estado: encendida o no, no hace nada, y decir «sí»
 * sería mentir. Lo que falta lo dice la fila de [RequirementRow] que va antes en su grupo.
 *
 * @param met el acceso del que depende está concedido.
 */
@Composable
internal fun DependentSwitch(
    @StringRes title: Int,
    @DrawableRes icon: Int,
    on: Boolean,
    met: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null
) {
    ItemRow(
        title = AnnotatedString(stringResource(title)),
        leadingIcon = icon,
        subtitle = subtitle,
        subtitleLines = 2,
        checked = on.takeIf { met },
        enabled = met,
        onClick = onToggle
    )
}

/**
 * El acceso que le falta a un grupo de opciones, como primera fila del grupo, con la acción de
 * concederlo. Lleva la marca de que se sale a una pantalla del sistema.
 */
@Composable
internal fun RequirementRow(@StringRes title: Int, onGrant: () -> Unit) {
    ItemRow(
        title = AnnotatedString(stringResource(title)),
        leadingIcon = R.drawable.ic_info,
        titleMark = R.drawable.ic_open_in_new,
        trailing = stringResource(R.string.settings_allow),
        onClick = onGrant
    )
}

/**
 * Rejilla de apps dentro de una página de ajustes, con el aspecto que tienen en el dock. Pasa a otra
 * línea si no caben: una fila con desplazamiento escondería las últimas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppGrid(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier.fillMaxWidth().padding(horizontal = AppGridInset),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

/**
 * Una app de una [AppGrid]: su icono como en el dock y su nombre debajo.
 *
 * @param description lo que hace tocarla, para TalkBack: el nombre solo no lo dice.
 * @param interaction cómo responde al toque (botón o interruptor).
 * @param dimmed la app está apagada para lo que la rejilla elige.
 * @param icon el icono y, si hace falta, su distintivo.
 */
@Composable
internal fun AppTile(
    label: String,
    description: String,
    interaction: Modifier,
    dimmed: Boolean = false,
    unread: Boolean = false,
    icon: @Composable () -> Unit
) {
    DockIcon(
        label = label,
        showLabel = true,
        unread = unread,
        modifier =
            Modifier
                .width(AppTileWidth)
                .clip(AlisioTokens.RowShape)
                .then(interaction)
                .semantics { contentDescription = description }
                .padding(vertical = 8.dp)
                .alpha(if (dimmed) AlisioTokens.DISABLED_ALPHA else 1f),
        icon = icon
    )
}
