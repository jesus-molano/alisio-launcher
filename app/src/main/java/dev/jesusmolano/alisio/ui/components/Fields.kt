package dev.jesusmolano.alisio.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

private val FieldMinHeight = 56.dp
private val FieldIconSize = 20.dp
private val TitleEditIconSize = 18.dp

/**
 * El texto de un campo con su cursor. Solo se rehace si [value] cambia desde fuera (el botón de
 * borrar): reconstruirlo en cada tecla movería el cursor y rompería el texto en composición.
 */
@Composable
private fun rememberFieldValue(
    value: String,
    onValueChange: (String) -> Unit
): Pair<TextFieldValue, (TextFieldValue) -> Unit> {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    return field to {
        // Se compara con lo que tenía el campo, no con `value`: dos ediciones en un mismo frame
        // (una letra y su borrado) deben avisar las dos.
        val previous = field.text
        field = it
        if (it.text != previous) onValueChange(it.text)
    }
}

/**
 * Campo de texto en píldora: la barra de búsqueda y los campos de las hojas. Sin borde, también al
 * escribir; con texto lleva un botón para borrarlo. Crece si el texto no cabe en su alto mínimo.
 *
 * @param leadingIcon dibujo al principio, como la lupa de un buscador.
 * @param maxLines más de una línea para textos largos, como una dirección.
 * @param onEnter lo que hace la tecla de acción del teclado; `null` la deja como «hecho».
 * @param emptyActions botones al final del campo mientras está vacío, en el sitio que después ocupa
 *   el de borrar: atajos que dejan de hacer falta en cuanto se escribe.
 */
@Composable
fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
    maxLines: Int = 1,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    onEnter: (() -> Unit)? = null,
    emptyActions: (@Composable RowScope.() -> Unit)? = null
) {
    val typing = value.isNotEmpty()
    val (field, onFieldChange) = rememberFieldValue(value, onValueChange)
    BasicTextField(
        value = field,
        onValueChange = onFieldChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = maxLines == 1,
        maxLines = maxLines,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = AlisioTokens.Text),
        cursorBrush = SolidColor(AlisioTokens.Accent),
        keyboardOptions =
            KeyboardOptions(
                capitalization = capitalization,
                autoCorrectEnabled = false,
                imeAction = if (onEnter != null) ImeAction.Go else ImeAction.Done
            ),
        keyboardActions = KeyboardActions(onGo = { onEnter?.invoke() }),
        decorationBox = { innerField ->
            Row(
                Modifier
                    .heightIn(min = FieldMinHeight)
                    .background(AlisioTokens.Fill, AlisioTokens.PillShape)
                    .padding(start = 20.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    Icon(
                        painter = painterResource(leadingIcon),
                        contentDescription = null,
                        tint = AlisioTokens.TextSecondary,
                        modifier = Modifier.size(FieldIconSize)
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    if (!typing) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AlisioTokens.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerField()
                }
                when {
                    typing ->
                        RoundIconButton(
                            R.drawable.ic_close,
                            stringResource(R.string.search_clear),
                            AlisioTokens.TextSecondary,
                            onClick = { onValueChange("") },
                            iconSize = FieldIconSize
                        )

                    emptyActions != null -> emptyActions()

                    else -> Spacer(Modifier.width(16.dp))
                }
            }
        }
    )
}

/**
 * Un nombre que se edita donde se lee: el título de una hoja (una pila, una isla) con un lápiz que
 * dice que se puede tocar. Sin píldora: es el título, no un campo más de la hoja.
 */
@Composable
fun TitleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onEnter: (() -> Unit)? = null
) {
    val (field, onFieldChange) = rememberFieldValue(value, onValueChange)
    BasicTextField(
        value = field,
        onValueChange = onFieldChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(color = AlisioTokens.Text),
        cursorBrush = SolidColor(AlisioTokens.Accent),
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = false,
                imeAction = if (onEnter != null) ImeAction.Go else ImeAction.Done
            ),
        keyboardActions = KeyboardActions(onGo = { onEnter?.invoke() }),
        decorationBox = { innerField ->
            Row(
                Modifier.heightIn(min = AlisioTokens.MinTouch).padding(horizontal = AlisioTokens.Edge),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.headlineSmall,
                            color = AlisioTokens.TextTertiary,
                            maxLines = 1
                        )
                    }
                    innerField()
                }
                Spacer(Modifier.width(12.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = null,
                    tint = AlisioTokens.TextSecondary,
                    modifier = Modifier.size(TitleEditIconSize)
                )
            }
        }
    )
}
