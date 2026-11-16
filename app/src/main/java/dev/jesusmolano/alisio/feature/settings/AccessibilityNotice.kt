package dev.jesusmolano.alisio.feature.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.ui.components.PrimaryButton
import dev.jesusmolano.alisio.ui.components.RoundIconButton
import dev.jesusmolano.alisio.ui.components.SectionLabel
import dev.jesusmolano.alisio.ui.components.TextAction
import dev.jesusmolano.alisio.ui.components.stableSystemBars
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

/**
 * El aviso que sale antes de mandar a los ajustes de accesibilidad del sistema (§5.8): qué hace el
 * servicio, qué no hace, por qué hace falta y cómo se activa. Un servicio de accesibilidad puede
 * mucho, y el aviso del sistema no distingue uno de otro: aquí se dice lo que hace este. Los dos
 * gestos van dibujados; lo demás es letra pequeña, porque es un consentimiento y no puede faltar.
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
                .windowInsetsPadding(stableSystemBars())
                .padding(vertical = 16.dp)
    ) {
        // El texto se desplaza si no cabe (letra grande); los botones se quedan siempre a la vista.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            RoundIconButton(
                R.drawable.ic_arrow_back,
                stringResource(R.string.appmenu_back),
                AlisioTokens.Text,
                onDismiss,
                // El dibujo de la flecha cae en el filo.
                modifier = Modifier.padding(start = AlisioTokens.Outset)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.accessibility_notice_title),
                style = MaterialTheme.typography.headlineLarge,
                color = AlisioTokens.Text,
                modifier = Modifier.padding(horizontal = AlisioTokens.Edge).semantics { heading() }
            )
            Column(Modifier.padding(horizontal = AlisioTokens.Outset)) {
                SectionLabel(stringResource(R.string.accessibility_notice_does_label))
                Fact(R.drawable.ic_lock, R.string.accessibility_notice_does_lock)
                Fact(R.drawable.ic_swipe_down, R.string.accessibility_notice_does_notifications)
                SectionLabel(stringResource(R.string.accessibility_notice_does_not_label))
                Fact(R.drawable.ic_visibility_off, R.string.accessibility_notice_does_not)
            }
            Spacer(Modifier.height(AlisioTokens.GroupGap))
            SmallPrint(R.string.accessibility_notice_why)
            Spacer(Modifier.height(12.dp))
            SmallPrint(R.string.accessibility_notice_how)
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.padding(horizontal = AlisioTokens.Edge)) {
            PrimaryButton(
                text = stringResource(R.string.accessibility_notice_continue),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            TextAction(
                stringResource(R.string.accessibility_notice_dismiss),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Una cosa que el servicio hace o no hace, con su dibujo. Tiene las medidas de una fila de la app
 * (hueco de icono y segundo filo), pero no se puede tocar: informa, no lleva a ningún sitio.
 */
@Composable
private fun Fact(@DrawableRes icon: Int, @StringRes text: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = AlisioTokens.RowMinHeight)
            .padding(horizontal = AlisioTokens.Edge - AlisioTokens.Outset, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(AlisioTokens.IconSlot), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = AlisioTokens.TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(AlisioTokens.IconGap))
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            color = AlisioTokens.Text
        )
    }
}

/** Lo que hay que poder leer antes de aceptar, sin competir con lo que el servicio hace. */
@Composable
private fun SmallPrint(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = AlisioTokens.TextSecondary,
        modifier = Modifier.padding(horizontal = AlisioTokens.Edge)
    )
}
