package dev.jesusmolano.alisio.feature.zones

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.zones.ZoneId
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.feature.appmenu.ZoneChoice
import dev.jesusmolano.alisio.feature.appmenu.ZoneMove
import dev.jesusmolano.alisio.ui.components.Chip
import dev.jesusmolano.alisio.ui.components.PillTextField
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

/** El nombre de una isla: el suyo si es una pila o una isla propia; si no, el de la interfaz. */
@Composable
fun zoneName(id: ZoneId, name: String?): String = name ?: stringResource(
    when (id) {
        ZoneId.Daily -> R.string.zone_daily

        ZoneId.Dev -> R.string.zone_dev

        ZoneId.System -> R.string.zone_system

        ZoneId.Rest -> R.string.zone_rest

        // Una pila o una isla propia sin nombre no debería existir; si pasa, no se queda en blanco.
        is ZoneId.Stack, is ZoneId.Custom -> R.string.zone_new
    }
)

/** Lo que el Interior de app necesita para «Mover a otra zona»; `null` si Zonas aún no ha cargado. */
@Composable
fun zoneMove(state: ZonesState?, app: AppEntry, onMove: (ZoneId) -> Unit): ZoneMove? {
    if (state == null) return null
    val current = state.zoneOf(app)
    val targets = remember(state, app.key) { state.targetsFor(app).mapTo(HashSet()) { it.id } }
    val choices =
        state.zones
            .filter { it.id == current || it.id in targets }
            .map { ZoneChoice(it.id, zoneName(it.id, it.name)) }
    return ZoneMove(current, choices, onMove)
}

/** Qué isla propia se está creando o cambiando. */
sealed interface ZoneEdit {
    data object New : ZoneEdit

    class Existing(val id: Long, val name: String) : ZoneEdit
}

/**
 * Hoja para poner nombre a una isla nueva o cambiar el de una que ya existe, que además se puede
 * borrar. Borrar pide un segundo toque sobre el mismo botón, que entonces dice qué va a pasar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneEditSheet(edit: ZoneEdit, onSave: (String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val existing = edit as? ZoneEdit.Existing
    var name by remember(edit) { mutableStateOf(existing?.name.orEmpty()) }
    var confirmDelete by remember(edit) { mutableStateOf(false) }

    fun save() {
        if (name.isNotBlank()) onSave(name)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AlisioTokens.Surface
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(if (existing == null) R.string.zone_new else R.string.zone_edit_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 24.sp),
                color = AlisioTokens.Text,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            PillTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.zone_name_hint),
                container = AlisioTokens.SurfaceElevated,
                capitalization = KeyboardCapitalization.Sentences,
                onEnter = ::save
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Chip(
                    label = stringResource(if (existing == null) R.string.zone_create else R.string.zone_save),
                    selected = name.isNotBlank(),
                    onClick = ::save
                )
                if (existing != null) {
                    Chip(
                        label =
                            stringResource(if (confirmDelete) R.string.zone_delete_confirm else R.string.zone_delete),
                        onClick = { if (confirmDelete) onDelete() else confirmDelete = true }
                    )
                }
            }
        }
    }
}
