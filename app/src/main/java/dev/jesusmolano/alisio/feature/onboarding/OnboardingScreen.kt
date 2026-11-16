package dev.jesusmolano.alisio.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.ui.components.PrimaryButton
import dev.jesusmolano.alisio.ui.components.TextAction
import dev.jesusmolano.alisio.ui.components.clockAutoSize
import dev.jesusmolano.alisio.ui.components.formatClock
import dev.jesusmolano.alisio.ui.components.rememberNow
import dev.jesusmolano.alisio.ui.components.stableSystemBars
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

/** Bienvenida: lo único que pide es ser la app de inicio. El contenido va abajo, junto al pulgar. */
@Composable
fun OnboardingScreen(onSetDefault: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(stableSystemBars())
                .padding(AlisioTokens.Edge)
    ) {
        // La hora, donde estará en el Inicio y con su misma letra: lo primero que se ve ya es Alisio,
        // no una pantalla en blanco. Es un adelanto, no un dato: el lector no la anuncia.
        Text(
            text = formatClock(rememberNow()),
            style = AlisioTokens.Clock,
            color = AlisioTokens.Text,
            maxLines = 1,
            autoSize = clockAutoSize(AlisioTokens.Clock),
            modifier = Modifier.padding(top = 16.dp).clearAndSetSemantics {}
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineLarge,
            color = AlisioTokens.Text,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_body),
            style = MaterialTheme.typography.bodyLarge,
            color = AlisioTokens.TextSecondary
        )
        Spacer(Modifier.height(32.dp))
        PrimaryButton(
            text = stringResource(R.string.onboarding_set_default),
            onClick = onSetDefault,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        TextAction(stringResource(R.string.onboarding_skip), onClick = onSkip, modifier = Modifier.fillMaxWidth())
    }
}
