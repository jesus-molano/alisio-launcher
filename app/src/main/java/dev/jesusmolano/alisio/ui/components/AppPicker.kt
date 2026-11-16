package dev.jesusmolano.alisio.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.search.filterByName
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

// Claves del grupo fijo dentro de la lista, con prefijo para no coincidir con la de ningún elemento.
private const val PINNED_KEY = "picker:pinned"
private const val PINNED_LABEL_KEY = "picker:pinned-label"

/**
 * El selector de apps de toda la app (docs/DISENO.md §4.7): la lista crece desde abajo y el
 * buscador va al pie, junto al teclado, igual que la barra. La marca de elegido es siempre la misma.
 *
 * Es genérico para servir también a lo que no es una app instalada (los buscadores web): cada
 * elemento solo necesita clave, nombre e icono.
 *
 * @param key identifica cada elemento; no se puede repetir.
 * @param chosen si el elemento está elegido ahora.
 * @param onPick en modo simple, quien llama cierra; en modo múltiple marca o desmarca y no cierra.
 * @param multiple se pueden marcar varios.
 * @param pinned grupo fijo encima de la lista, separado de ella por la etiqueta «Apps» (por ejemplo
 *   «Crear una pila» o «Que elija el sistema»). Se retira al escribir: entonces solo se ve lo que se busca.
 */
@Composable
fun <T> AppPicker(
    items: List<T>,
    key: (T) -> Any,
    label: (T) -> String,
    icon: (T) -> Bitmap?,
    chosen: (T) -> Boolean,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    multiple: Boolean = false,
    searchHint: String = stringResource(R.string.picker_search),
    pinned: (@Composable ColumnScope.() -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    val found = remember(items, query) { items.filterByName(query, label) }
    Column(modifier.fillMaxWidth().imePadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // Con la lista invertida el primer elemento es el más cercano al buscador y al pulgar.
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = AlisioTokens.Outset, vertical = 8.dp)
        ) {
            items(found, key = key) { item ->
                ItemRow(
                    title = AnnotatedString(label(item)),
                    icon = icon(item),
                    leadingIcon = R.drawable.ic_search.takeIf { icon(item) == null },
                    selected = chosen(item),
                    multiple = multiple,
                    onClick = { onPick(item) }
                )
            }
            // El grupo fijo va dentro de la lista y después de las apps: por estar invertida queda
            // pegado encima de ellas y se desplaza con ellas, sin un hueco entre los dos cuando hay
            // pocas. La etiqueta separa ese grupo de las apps en todos los usos.
            if (pinned != null && query.isBlank()) {
                item(key = PINNED_LABEL_KEY) { SectionLabel(stringResource(R.string.stack_all_apps)) }
                item(key = PINNED_KEY) { Column(content = pinned) }
            }
        }
        PillTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = searchHint,
            leadingIcon = R.drawable.ic_search,
            modifier = Modifier.padding(start = AlisioTokens.Outset, end = AlisioTokens.Outset, bottom = 12.dp)
        )
    }
}

/**
 * [AppPicker] en su propia hoja, para elegir desde una fila de ajustes: la fila solo enseña lo
 * elegido y esta hoja ofrece el resto.
 */
@Composable
fun <T> AppPickerSheet(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    label: (T) -> String,
    icon: (T) -> Bitmap?,
    chosen: (T) -> Boolean,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    multiple: Boolean = false,
    pinned: (@Composable ColumnScope.() -> Unit)? = null
) {
    AlisioSheet(onDismiss = onDismiss) {
        SheetTitle(title)
        AppPicker(
            items = items,
            key = key,
            label = label,
            icon = icon,
            chosen = chosen,
            onPick = onPick,
            modifier = Modifier.weight(1f),
            multiple = multiple,
            pinned = pinned
        )
    }
}
