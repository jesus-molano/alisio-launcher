package dev.jesusmolano.alisio.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.feature.search.openPlayStoreApp
import dev.jesusmolano.alisio.ui.components.AlisioSheet
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.Note
import dev.jesusmolano.alisio.ui.components.SectionLabel
import dev.jesusmolano.alisio.ui.components.SheetTitle
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

/**
 * Lo que ha pasado al cargar una copia.
 *
 * @param missing las apps que la configuración usa y no están instaladas: paquete y nombre.
 * @param undo vuelve a poner la configuración de antes de cargarla.
 */
class BackupResult(val missing: List<Pair<String, String>>, val undo: () -> Unit)

/**
 * Guardar y cargar la configuración (docs/IDEAS.md, A16). Cada una abre el selector de documentos
 * del sistema. El resultado lo atiende la actividad, no quien lo pidió: si la actividad se rehace con
 * el selector abierto (al girar el teléfono), el archivo elegido llega a la instancia nueva.
 */
class BackupActions(val save: () -> Unit, val load: () -> Unit)

/** Al pie de la portada: la copia no es de ninguna sección. Cargar sustituye toda la configuración. */
@Composable
internal fun BackupRows(actions: BackupActions) {
    SectionLabel(stringResource(R.string.backup_title))
    ItemRow(
        title = AnnotatedString(stringResource(R.string.backup_save)),
        leadingIcon = R.drawable.ic_download,
        onClick = actions.save
    )
    ItemRow(
        title = AnnotatedString(stringResource(R.string.backup_load)),
        leadingIcon = R.drawable.ic_upload,
        subtitle = stringResource(R.string.backup_load_hint),
        subtitleLines = 2,
        onClick = actions.load
    )
}

/**
 * Las apps que usa la copia recién cargada y no están instaladas, con su ficha de Play Store. Es
 * otra ventana y tapa la barra de deshacer: por eso lleva su propio «Deshacer la carga», y la barra
 * sale al cerrarla ([onDismiss]), con su tiempo entero.
 */
@Composable
fun BackupMissingSheet(missing: List<Pair<String, String>>, onUndo: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlisioSheet(onDismiss = onDismiss, expanded = false) {
        SheetTitle(pluralStringResource(R.plurals.backup_missing_title, missing.size, missing.size))
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AlisioTokens.Outset)
        ) {
            Note(stringResource(R.string.backup_missing_hint))
            Spacer(Modifier.height(8.dp))
            missing.forEach { (packageName, label) ->
                ItemRow(
                    title = AnnotatedString(label),
                    leadingIcon = R.drawable.ic_open_in_new,
                    subtitle = packageName.takeIf { it != label },
                    trailing = stringResource(R.string.backup_install),
                    onClick = { context.openPlayStoreApp(packageName) }
                )
            }
            // Lo que deshace va lo último y aparte, como en las demás hojas.
            Spacer(Modifier.height(AlisioTokens.GroupGap))
            ItemRow(
                title = AnnotatedString(stringResource(R.string.backup_undo_load)),
                leadingIcon = R.drawable.ic_restart,
                secondary = true,
                onClick = onUndo
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
