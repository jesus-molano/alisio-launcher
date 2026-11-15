package dev.jesusmolano.alisio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

/**
 * Campo de texto en píldora de 56 dp: la barra de búsqueda y los campos de las hojas. Mientras hay
 * texto lleva borde de acento y un botón para borrarlo.
 *
 * @param onEnter lo que hace la tecla de acción del teclado; `null` la deja como «hecho».
 */
@Composable
fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    container: Color = AlisioTokens.Surface,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    onEnter: (() -> Unit)? = null
) {
    val shape = AlisioTokens.PillShape
    val typing = value.isNotEmpty()
    // El campo guarda su propio cursor y su texto en composición. Solo se rehace si el texto cambia
    // desde fuera (el botón de borrar): reconstruirlo en cada tecla movería el cursor.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    BasicTextField(
        value = field,
        onValueChange = {
            // Se compara con lo que tenía el campo, no con `value`: dos ediciones en un mismo frame
            // (una letra y su borrado) deben avisar las dos.
            val previous = field.text
            field = it
            if (it.text != previous) onValueChange(it.text)
        },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
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
                    .height(56.dp)
                    .background(container, shape)
                    .border(1.dp, if (typing) AlisioTokens.Accent else AlisioTokens.ControlBorder, shape)
                    .padding(start = 22.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (!typing) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AlisioTokens.TextSecondary
                        )
                    }
                    innerField()
                }
                if (typing) {
                    val clear = stringResource(R.string.search_clear)
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(shape)
                            .clickable { onValueChange("") }
                            .semantics { contentDescription = clear },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", style = MaterialTheme.typography.titleMedium, color = AlisioTokens.TextSecondary)
                    }
                } else {
                    Spacer(Modifier.size(18.dp))
                }
            }
        }
    )
}
