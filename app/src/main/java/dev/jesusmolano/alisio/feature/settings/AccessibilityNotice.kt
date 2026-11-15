package dev.jesusmolano.alisio.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

private val ScreenMargin = 24.dp

/**
 * El aviso que sale antes de mandar a los ajustes de accesibilidad del sistema (§5.8): qué hace el
 * servicio, qué no hace, por qué hace falta y cómo se activa. Un servicio de accesibilidad puede
 * mucho, y el aviso del sistema no distingue uno de otro: aquí se dice lo que hace este.
 *
 * @param onContinue abre los ajustes de accesibilidad del sistema, donde se activa.
 */
@Composable
fun AccessibilityNotice(onContinue: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(AlisioTokens.Background)
                // El aviso se queda los toques: no llegan a los ajustes de debajo.
                .pointerInput(Unit) {}
                .systemBarsPadding()
                .padding(horizontal = ScreenMargin, vertical = 16.dp)
    ) {
        // El texto se desplaza si no cabe (letra grande); los botones se quedan siempre a la vista.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.accessibility_notice_title),
                style = MaterialTheme.typography.headlineLarge,
                color = AlisioTokens.Text,
                modifier = Modifier.semantics { heading() }
            )
            Part(R.string.accessibility_notice_does_label, R.string.accessibility_notice_does)
            Part(R.string.accessibility_notice_does_not_label, R.string.accessibility_notice_does_not)
            Part(R.string.accessibility_notice_why_label, R.string.accessibility_notice_why)
            Part(R.string.accessibility_notice_how_label, R.string.accessibility_notice_how)
            Spacer(Modifier.height(24.dp))
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.accessibility_notice_continue))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.accessibility_notice_dismiss))
        }
    }
}

/** Un apartado del aviso: su etiqueta y su texto. */
@Composable
private fun Part(@StringRes label: Int, @StringRes body: Int) {
    Spacer(Modifier.height(24.dp))
    Text(
        text = stringResource(label),
        style = AlisioTokens.SectionLabel,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(body),
        style = MaterialTheme.typography.bodyLarge,
        color = AlisioTokens.Text
    )
}
