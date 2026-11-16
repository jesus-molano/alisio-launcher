package dev.jesusmolano.alisio

import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesusmolano.alisio.data.home.SlotConfig
import dev.jesusmolano.alisio.ui.components.AlisioSheet
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.Note
import dev.jesusmolano.alisio.ui.components.PrimaryButton
import dev.jesusmolano.alisio.ui.components.SectionLabel
import dev.jesusmolano.alisio.ui.components.SheetTitle
import dev.jesusmolano.alisio.ui.theme.AlisioTheme
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import dev.jesusmolano.alisio.ui.theme.accentColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Lo elegido como destino: la barra, o la posición del hueco de una pila.
private const val TO_SEARCH = -1

/**
 * «Añadir a inicio» desde otra app (docs/IDEAS.md §2.3): el sistema la abre con la petición de fijar
 * un acceso directo. Es una hoja con el estilo de Alisio sobre la app que lo pide: dice qué se fija
 * y dónde, en la barra (sale al buscar) o en una de las pilas del dock. No se declara la de widgets:
 * el sistema les diría a las apps que Alisio los acepta.
 */
class PinShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcherApps = getSystemService(LauncherApps::class.java)
        val request =
            try {
                launcherApps.getPinItemRequest(intent)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Petición de fijar ilegible", e)
                null
            }
        val shortcut = request?.shortcutInfo
        if (request == null || request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT ||
            shortcut == null || !request.isValid
        ) {
            finish()
            return
        }
        val container = (application as AlisioApp).container
        setContent {
            val prefs by container.settings.prefs.collectAsStateWithLifecycle()
            val dock by container.homeConfig.dock.collectAsStateWithLifecycle()
            val apps by container.appsRepository.apps.collectAsStateWithLifecycle()
            val loaded = prefs ?: return@setContent
            val app = apps.orEmpty().firstOrNull {
                it.component.packageName == shortcut.`package` &&
                    it.user == shortcut.userHandle
            }
            val icon by produceState<Bitmap?>(null, shortcut.id) {
                value =
                    withContext(Dispatchers.IO) {
                        try {
                            launcherApps.getShortcutIconDrawable(shortcut, resources.displayMetrics.densityDpi)
                                ?.toBitmap()
                        } catch (e: RuntimeException) {
                            Log.w(TAG, "Icono del acceso directo imposible de leer", e)
                            null
                        }
                    }
            }
            AlisioTheme(accent = accentColor(loaded.accent, loaded.customAccent), font = loaded.font) {
                PinSheet(
                    label = (shortcut.shortLabel ?: shortcut.longLabel)?.toString().orEmpty(),
                    appLabel = app?.label ?: shortcut.`package`,
                    icon = icon ?: app?.icon,
                    stacks =
                        dock.orEmpty().mapIndexedNotNull { position, slot ->
                            (slot as? SlotConfig.Stack)?.let { position to it.name }
                        },
                    onPin = { destination ->
                        val key = container.appsRepository.acceptPin(request)
                        val stack = dock.orEmpty().getOrNull(destination) as? SlotConfig.Stack
                        val message =
                            when {
                                key == null -> getString(R.string.pin_failed)

                                stack != null -> {
                                    container.homeConfig.addToStack(destination, key)
                                    getString(R.string.pin_done_stack, stack.name)
                                }

                                else -> getString(R.string.pin_done_search)
                            }
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    // Sin aceptar, el sistema da la petición por rechazada y se lo dice a la app.
                    onDismiss = ::finish
                )
            }
        }
    }

    private companion object {
        const val TAG = "PinShortcut"
    }
}

/**
 * La hoja: qué se fija («Fijar ‹Ir a casa› de Maps»), cómo se verá en la barra y dónde. La barra
 * es el destino por defecto: no ocupa sitio y el acceso sale al buscar su nombre.
 *
 * @param stacks las pilas del dock, por su hueco y su nombre.
 */
@Composable
private fun PinSheet(
    label: String,
    appLabel: String,
    icon: Bitmap?,
    stacks: List<Pair<Int, String>>,
    onPin: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var destination by remember { mutableIntStateOf(TO_SEARCH) }
    AlisioSheet(onDismiss = onDismiss, expanded = false) {
        // El título solo lleva lo que se fija: la app va justo debajo, en la muestra, y así cabe en una línea.
        SheetTitle(stringResource(R.string.pin_title, label))
        // Las filas llevan su margen, como en las demás hojas: así su contenido cae en el filo.
        Column(Modifier.padding(horizontal = AlisioTokens.Outset)) {
            // Tal como saldrá en la barra: su nombre, el de su app y su icono.
            ItemRow(
                title = AnnotatedString(label),
                icon = icon,
                leadingIcon = if (icon == null) R.drawable.ic_push_pin else null,
                subtitle = appLabel,
                // Es la muestra de la barra: tocarla elige la barra.
                onClick = { destination = TO_SEARCH }
            )
            SectionLabel(stringResource(R.string.pin_where))
            ItemRow(
                title = AnnotatedString(stringResource(R.string.pin_to_search)),
                leadingIcon = R.drawable.ic_search,
                subtitle = stringResource(R.string.pin_to_search_hint),
                selected = destination == TO_SEARCH,
                onClick = { destination = TO_SEARCH }
            )
            stacks.forEach { (position, name) ->
                ItemRow(
                    title = AnnotatedString(stringResource(R.string.pin_to_stack, name)),
                    leadingIcon = R.drawable.ic_stacks,
                    selected = destination == position,
                    onClick = { destination = position }
                )
            }
            if (stacks.isEmpty()) Note(stringResource(R.string.pin_no_stacks))
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = stringResource(R.string.pin_confirm),
            onClick = { onPin(destination) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = AlisioTokens.Outset),
            icon = R.drawable.ic_push_pin
        )
        Spacer(Modifier.height(24.dp))
    }
}
