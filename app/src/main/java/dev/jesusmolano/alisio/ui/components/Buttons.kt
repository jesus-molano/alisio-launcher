package dev.jesusmolano.alisio.ui.components

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

private val PrimaryHeight = 56.dp
private val ChipHeight = 40.dp
private val ButtonIconSize = 20.dp

/**
 * La acción principal de una pantalla: relleno de acento. Como mucho una por pantalla.
 *
 * @param icon dibujo antes del texto.
 * @param trailingIcon dibujo en el extremo final, como la marca de Enter cuando el botón es su
 *   destino. Pensado para un botón de ancho completo.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    @DrawableRes trailingIcon: Int? = null
) {
    val shape = AlisioTokens.PillShape
    Row(
        modifier
            .heightIn(min = PrimaryHeight)
            .alpha(if (enabled) 1f else AlisioTokens.DISABLED_ALPHA)
            .clip(shape)
            .background(AlisioTokens.Accent, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Con marca al final, un hueco igual al principio deja el texto centrado en el botón.
        if (trailingIcon != null) Spacer(Modifier.width(ButtonIconSize))
        Row(
            Modifier.weight(1f, fill = trailingIcon != null),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                ButtonIcon(icon, AlisioTokens.Background)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = AlisioTokens.Background,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        if (trailingIcon != null) ButtonIcon(trailingIcon, AlisioTokens.Background)
    }
}

/** Una acción secundaria con forma de botón: relleno discreto, icono y texto en el color del texto. */
@Composable
fun TonalButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    val shape = AlisioTokens.PillShape
    Row(
        modifier
            .heightIn(min = AlisioTokens.MinTouch)
            .clip(shape)
            .background(AlisioTokens.Fill, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            ButtonIcon(icon, AlisioTokens.Text)
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = AlisioTokens.Text)
    }
}

/**
 * Una acción escrita, sin caja.
 *
 * @param secondary una acción destructiva o de menor peso: va en el color secundario, no en acento.
 */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    enabled: Boolean = true
) {
    Box(
        modifier
            .heightIn(min = AlisioTokens.MinTouch)
            .alpha(if (enabled) 1f else AlisioTokens.DISABLED_ALPHA)
            .clip(AlisioTokens.PillShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (secondary) AlisioTokens.TextSecondary else AlisioTokens.Accent,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Botón redondo de 48 dp con solo un dibujo; [description] es lo que lee TalkBack.
 *
 * @param container color de un círculo de fondo; sin él, el dibujo va suelto.
 * @param state lo que TalkBack añade tras el nombre cuando el relleno dice algo («Principal»).
 */
@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    container: Color? = null,
    state: String? = null
) {
    Box(
        modifier
            .size(AlisioTokens.MinTouch)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                if (state != null) stateDescription = state
            },
        contentAlignment = Alignment.Center
    ) {
        if (container != null) Box(Modifier.size(ChipHeight).background(container, CircleShape))
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Píldora pequeña: 40 dp a la vista dentro de 48 dp de toque.
 *
 * @param selected `null` es una salida (una acción, como «Web»); con valor, la píldora es una opción
 *   entre varias y TalkBack dice si está elegida.
 * @param icon dibujo antes del texto.
 * @param appIcon icono de una app antes del texto; tiene preferencia sobre [icon].
 */
@Composable
fun Chip(
    label: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    appIcon: Bitmap? = null,
    selected: Boolean? = null,
    onClick: () -> Unit
) {
    val shape = AlisioTokens.PillShape
    // El toque es de la caja de 48 dp, pero la respuesta se pinta sobre la píldora que se ve.
    val interactions = remember { MutableInteractionSource() }
    val press =
        if (selected == null) {
            Modifier.clickable(interactions, indication = null, role = Role.Button, onClick = onClick)
        } else {
            Modifier.selectable(selected, interactions, indication = null, role = Role.RadioButton, onClick = onClick)
        }
    Box(modifier.heightIn(min = AlisioTokens.MinTouch).then(press), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .heightIn(min = ChipHeight)
                .clip(shape)
                .background(if (selected == true) AlisioTokens.AccentSoft else AlisioTokens.Fill, shape)
                .indication(interactions, ripple())
                .padding(start = if (icon != null || appIcon != null) 12.dp else 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                appIcon != null -> {
                    AppIcon(appIcon, ButtonIconSize)
                    Spacer(Modifier.width(8.dp))
                }

                icon != null -> {
                    ButtonIcon(icon, if (selected == true) AlisioTokens.Accent else AlisioTokens.TextSecondary)
                    Spacer(Modifier.width(8.dp))
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected == true) AlisioTokens.Accent else AlisioTokens.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ButtonIcon(@DrawableRes icon: Int, tint: Color) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(ButtonIconSize)
    )
}
