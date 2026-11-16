package dev.jesusmolano.alisio.feature.zones

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.zones.ZoneId
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.sortedAlphabetically
import dev.jesusmolano.alisio.feature.appmenu.ZoneChoice
import dev.jesusmolano.alisio.feature.appmenu.ZoneMove
import dev.jesusmolano.alisio.ui.components.AlisioSheet
import dev.jesusmolano.alisio.ui.components.AppPicker
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.LocalUndo
import dev.jesusmolano.alisio.ui.components.PrimaryButton
import dev.jesusmolano.alisio.ui.components.TitleTextField
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

/** Lo que el Interior de app necesita para «Mover a otra isla»; `null` si Zonas aún no ha cargado. */
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
 * Hoja de una isla propia. El nombre es el título y se escribe ahí mismo; debajo, todas las apps
 * para marcar las suyas de una vez, sin arrastrarlas una a una. Una isla nueva pide primero el
 * nombre y, al crearla, la misma hoja pasa a sus apps.
 *
 * Todo se guarda al tocarlo, como en la hoja de una pila: no hay botón de guardar. Borrar la isla
 * se puede deshacer desde la barra de la actividad.
 */
@Composable
fun ZoneEditSheet(edit: ZoneEdit, viewModel: ZonesViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val undo = LocalUndo.current
    val resources = LocalResources.current
    val existing = edit as? ZoneEdit.Existing
    // El nombre se edita en local y se guarda en cada cambio: releerlo de la base movería el cursor.
    var name by remember(edit) { mutableStateOf(existing?.name.orEmpty()) }
    // `null` mientras la isla nueva no existe.
    var id by remember(edit) { mutableStateOf(existing?.id) }
    // Las islas que había al pulsar «Crear»: la que aparezca después es la nueva. `createZone` no
    // devuelve su id porque escribe en otro hilo.
    var before by remember(edit) { mutableStateOf<Set<Long>?>(null) }
    val custom = state?.custom
    LaunchedEffect(custom, before) {
        val known = before ?: return@LaunchedEffect
        custom?.firstOrNull { it.id !in known }?.let {
            id = it.id
            before = null
        }
    }
    val focus = remember { FocusRequester() }
    // Una isla nueva no tiene nada más que su nombre: el teclado sale ya, sin un toque de más.
    LaunchedEffect(edit) { if (existing == null) focus.requestFocus() }

    val zone = id
    AlisioSheet(onDismiss = onDismiss) {
        // El foco de la hoja es el de su ventana, no el de la pantalla que la abre.
        val focusManager = LocalFocusManager.current

        fun create() {
            val current = custom ?: return
            if (name.isBlank() || before != null) return
            before = current.mapTo(HashSet()) { it.id }
            viewModel.createZone(name)
            // El teclado se retira: lo siguiente es marcar apps, y la lista necesita el sitio.
            focusManager.clearFocus()
        }

        TitleTextField(
            value = name,
            onValueChange = { typed ->
                name = typed
                // Un nombre en blanco no se guarda: la isla conserva el último que tuvo.
                id?.let { viewModel.renameZone(it, typed) }
            },
            placeholder = stringResource(R.string.zone_name_hint),
            modifier = Modifier.focusRequester(focus),
            onEnter = if (zone == null) ({ create() }) else null
        )
        val current = state
        if (zone == null || current == null) {
            PrimaryButton(
                text = stringResource(R.string.zone_create),
                onClick = { create() },
                enabled = name.isNotBlank(),
                modifier =
                    Modifier
                        .imePadding()
                        .fillMaxWidth()
                        .padding(horizontal = AlisioTokens.Outset)
                        .padding(top = AlisioTokens.SheetTitleGap, bottom = 16.dp)
            )
        } else {
            ZoneApps(
                zone = zone,
                state = current,
                onToggle = { app, inZone ->
                    // Sacarla es devolverla a la isla que le toca sin asignación.
                    viewModel.move(app, if (inZone) current.defaultZone(app) else ZoneId.Custom(zone))
                },
                onDelete = {
                    val saved = current.custom.firstOrNull { it.id == zone }?.name ?: name
                    undo.show(resources.getString(R.string.undo_zone_deleted, saved), viewModel.deleteZone(zone))
                    onDismiss()
                }
            )
        }
    }
}

/**
 * Todas las apps, con las de la isla marcadas. Las que ya eran suyas al abrir la hoja van primero,
 * junto al buscador; las que se marcan después no se recolocan bajo el dedo.
 *
 * @param onToggle la app tocada y si ahora mismo está en la isla.
 */
@Composable
private fun ColumnScope.ZoneApps(
    zone: Long,
    state: ZonesState,
    onToggle: (AppEntry, Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val target = ZoneId.Custom(zone)
    val members =
        remember(zone) {
            state.zones.firstOrNull { it.id == target }?.apps.orEmpty().mapTo(HashSet()) { it.key.toString() }
        }
    val apps =
        remember(state, members) {
            state.zones
                .flatMap { it.apps }
                .sortedAlphabetically({ it.label }, { it.key })
                .sortedByDescending { it.key.toString() in members }
        }
    AppPicker(
        items = apps,
        key = { it.key.toString() },
        label = { it.label },
        icon = { it.icon },
        chosen = { state.zoneOf(it) == target },
        onPick = { onToggle(it, state.zoneOf(it) == target) },
        modifier = Modifier.weight(1f),
        multiple = true,
        pinned = {
            // Lo que borra va aparte, en el color secundario, y se puede deshacer.
            ItemRow(
                title = AnnotatedString(stringResource(R.string.zone_delete)),
                leadingIcon = R.drawable.ic_delete,
                secondary = true,
                onClick = onDelete
            )
        }
    )
}
