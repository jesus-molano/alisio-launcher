package dev.jesusmolano.alisio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Lo que tarda en irse la barra si no se toca.
private const val UNDO_MILLIS = 6_000L
private val UndoBarHeight = 56.dp

/**
 * El único patrón para lo que borra configuración (docs/DISENO.md §4.8): se hace al primer toque y
 * durante unos segundos se puede deshacer. No hay confirmaciones de dos toques.
 */
@Stable
class UndoState internal constructor(
    private val scope: CoroutineScope,
    private val actionLabel: String,
    private val accessibility: AccessibilityManager?
) {
    internal val host = SnackbarHostState()
    private var showing: Job? = null

    /**
     * Enseña la barra con [message] y la acción «Deshacer». Una barra nueva sustituye a la que
     * hubiera: lo anterior ya no se puede deshacer.
     *
     * @param onUndo devuelve las cosas a como estaban; quien llama guarda lo que haga falta para ello.
     */
    fun show(message: String, onUndo: () -> Unit) {
        showing?.cancel()
        showing =
            scope.launch {
                // Con lector de pantalla o con controles por conmutador, el sistema pide más tiempo.
                val timeout =
                    accessibility?.calculateRecommendedTimeoutMillis(
                        originalTimeoutMillis = UNDO_MILLIS,
                        containsText = true,
                        containsControls = true
                    ) ?: UNDO_MILLIS
                val result =
                    withTimeoutOrNull(timeout) {
                        host.showSnackbar(message, actionLabel, duration = SnackbarDuration.Indefinite)
                    }
                if (result == SnackbarResult.ActionPerformed) onUndo()
            }
    }

    /** Retira la barra sin deshacer nada: al cambiar de pantalla, lo borrado ya no viene a cuento. */
    fun dismiss() {
        showing?.cancel()
    }
}

@Composable
fun rememberUndoState(): UndoState {
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.undo)
    val accessibility = LocalAccessibilityManager.current
    return remember(scope, label, accessibility) { UndoState(scope, label, accessibility) }
}

/** La barra de deshacer de la actividad; la pone quien dibuja [UndoBar]. */
val LocalUndo = staticCompositionLocalOf<UndoState> { error("Falta un UndoState: lo provee la actividad") }

/**
 * Donde aparece la barra de deshacer: una sola por actividad, anclada abajo, encima de lo que haya.
 * Una superficie opaca en píldora con el texto de lo que se ha hecho y «Deshacer».
 */
@Composable
fun UndoBar(state: UndoState, modifier: Modifier = Modifier) {
    SnackbarHost(state.host, modifier) { data ->
        Row(
            Modifier
                .padding(horizontal = AlisioTokens.Outset)
                .fillMaxWidth()
                .heightIn(min = UndoBarHeight)
                .background(AlisioTokens.Surface, AlisioTokens.PillShape)
                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyLarge,
                color = AlisioTokens.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            data.visuals.actionLabel?.let { TextAction(it, onClick = data::performAction) }
        }
    }
}
