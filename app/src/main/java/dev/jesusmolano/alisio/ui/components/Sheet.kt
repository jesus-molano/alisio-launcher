package dev.jesusmolano.alisio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

/**
 * La hoja inferior de la app (docs/DISENO.md §4.6): una sola superficie, un asa discreta y el
 * contenido en filas. Nunca se abre una hoja sobre otra: un segundo paso cambia el contenido de la
 * misma y pone [SheetTitle] con su botón de volver.
 *
 * @param expanded `true` la abre entera, sin el paso intermedio a media altura; es lo que necesita
 *   una hoja con lista o con campo de texto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlisioSheet(onDismiss: () -> Unit, expanded: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = expanded),
        shape = AlisioTokens.SheetShape,
        containerColor = AlisioTokens.Surface,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 16.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(AlisioTokens.Fill, CircleShape)
            )
        },
        content = content
    )
}

/**
 * Título de una hoja, en el filo.
 *
 * @param onBack con valor, la hoja está en un segundo paso: el título lleva delante el botón de volver.
 */
@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = AlisioTokens.MinTouch)
            // El botón de volver trae su propio aire: su dibujo cae en el filo.
            .padding(start = if (onBack != null) AlisioTokens.Outset else AlisioTokens.Edge, end = AlisioTokens.Edge)
            .padding(bottom = AlisioTokens.SheetTitleGap - 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            RoundIconButton(R.drawable.ic_arrow_back, stringResource(R.string.appmenu_back), AlisioTokens.Text, onBack)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = AlisioTokens.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() }
        )
    }
}
