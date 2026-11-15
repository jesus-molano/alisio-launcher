package dev.jesusmolano.alisio.feature.appmenu

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.search.filterByName
import dev.jesusmolano.alisio.core.usage.usageDuration
import dev.jesusmolano.alisio.core.zones.ZoneId
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.ShortcutEntry
import dev.jesusmolano.alisio.data.home.SwipeTarget
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.components.Chip
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.PillTextField
import dev.jesusmolano.alisio.ui.components.RoundIconButton
import dev.jesusmolano.alisio.ui.components.SectionLabel
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

// Los que caben sin que la hoja se convierta en una lista: las acciones de la app van primero.
private const val MENU_SHORTCUTS = 6
private const val PICKER_SHORTCUTS = 8
private val HeaderIconSize = 52.dp

/** Lo que el Interior de app necesita saber del uso de hoy y cómo pedirlo. */
class AppUsage(val granted: Boolean, val today: suspend () -> Long?, val onGrant: () -> Unit)

/** Lo que se puede hacer con el hueco del dock que ocupa la app; es lo mismo que ofrece su hoja. */
class SlotActions(val onChangeApp: () -> Unit, val onMakeStack: () -> Unit, val onClear: () -> Unit)

/** Una isla de Zonas con el nombre que se enseña. */
class ZoneChoice(val id: ZoneId, val name: String)

/**
 * «Mover a otra zona»: la isla en la que está la app y las demás a las que puede ir.
 *
 * @param choices [current] y sus destinos posibles, en el orden en que se ven en Zonas.
 */
class ZoneMove(val current: ZoneId, val choices: List<ZoneChoice>, val onMove: (ZoneId) -> Unit)

/**
 * Interior de app (§5.6, §6): la hoja que sale al mantener pulsada una app del dock o de Zonas.
 * Enseña su uso de hoy, abre sus accesos directos, elige a dónde lleva deslizar hacia arriba sobre
 * su icono, la cambia de isla y deja a mano la ficha del sistema y las acciones del hueco.
 *
 * @param shortcuts todos los accesos directos conocidos: los de [app] se listan, y cualquiera puede
 *   elegirse como destino.
 * @param target destino secundario actual del icono, si lo tiene.
 * @param gesturesEnabled con los gestos del dock apagados en los ajustes no se ofrece elegir destino.
 * @param slot acciones del hueco del dock; `null` cuando la hoja no sale de un hueco.
 * @param zones `null` mientras Zonas no ha cargado: entonces no se ofrece cambiar de isla.
 * @param shortcutIcon carga el icono propio de un acceso directo; `null` si no tiene.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMenuSheet(
    app: AppEntry,
    apps: List<AppEntry>,
    shortcuts: List<ShortcutEntry>,
    target: SwipeTarget?,
    gesturesEnabled: Boolean,
    usage: AppUsage,
    slot: SlotActions?,
    zones: ZoneMove?,
    shortcutIcon: suspend (ShortcutEntry) -> Bitmap?,
    onOpenShortcut: (ShortcutEntry) -> Unit,
    onSetTarget: (SwipeTarget?) -> Unit,
    onAppInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    var picking by remember { mutableStateOf(false) }
    // Los de esta app, con las acciones por delante de las conversaciones.
    val own =
        remember(shortcuts, app) {
            shortcuts
                .filter {
                    it.app.component.packageName == app.component.packageName &&
                        it.app.key.userSerial == app.key.userSerial
                }
                .distinctBy { it.key }
                .sortedBy { it.isConversation }
                .take(MENU_SHORTCUTS)
        }
    val icons by produceState(emptyMap<String, Bitmap>(), own.map { it.key }) {
        own.forEach { shortcut -> shortcutIcon(shortcut)?.let { value = value + (shortcut.key to it) } }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AlisioTokens.Surface
    ) {
        if (picking) {
            TargetPicker(
                app = app,
                apps = apps,
                shortcuts = shortcuts,
                own = own,
                icons = icons,
                target = target,
                onPick = {
                    onSetTarget(it)
                    picking = false
                },
                onBack = { picking = false }
            )
        } else {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                Header(app, usage, onAppInfo)
                if (own.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.appmenu_shortcuts))
                    own.forEach { shortcut ->
                        ItemRow(
                            title = AnnotatedString(shortcut.label),
                            icon = icons[shortcut.key],
                            leadingIcon = R.drawable.ic_bolt,
                            onClick = { onOpenShortcut(shortcut) }
                        )
                    }
                }
                if (gesturesEnabled) {
                    SectionLabel(stringResource(R.string.appmenu_swipe))
                    TargetRow(target, onClick = { picking = true })
                }
                // Con una sola isla posible no hay nada que elegir.
                if (zones != null && zones.choices.size > 1) {
                    SectionLabel(stringResource(R.string.appmenu_zone))
                    // La isla actual va marcada; tocar otra mueve la app y la marca pasa a ella.
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        zones.choices.forEach { choice ->
                            val current = choice.id == zones.current
                            Chip(
                                label = choice.name,
                                selected = current,
                                modifier = Modifier.semantics { selected = current },
                                onClick = { if (!current) zones.onMove(choice.id) }
                            )
                        }
                    }
                }
                if (slot != null) {
                    SectionLabel(stringResource(R.string.appmenu_slot))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Chip(stringResource(R.string.appmenu_change_app), onClick = slot.onChangeApp)
                        Chip(stringResource(R.string.slot_make_stack), onClick = slot.onMakeStack)
                        Chip(stringResource(R.string.picker_clear), onClick = slot.onClear)
                    }
                }
            }
        }
    }
}

/** Icono, nombre y uso de hoy; al final, la ficha de la app en el sistema. */
@Composable
private fun Header(app: AppEntry, usage: AppUsage, onAppInfo: () -> Unit) {
    val today by produceState<Long?>(null, app.key, usage.granted) {
        value = if (usage.granted) usage.today() else null
    }
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app.icon, HeaderIconSize)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 24.sp),
                color = AlisioTokens.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (usage.granted) {
                val millis = today
                Text(
                    text =
                        when {
                            // Aún leyendo, o app de otro perfil: la línea guarda su sitio y no salta.
                            millis == null -> ""

                            millis <= 0L -> stringResource(R.string.app_usage_none)

                            else -> stringResource(R.string.app_usage_today, usageText(millis))
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlisioTokens.TextSecondary
                )
            } else {
                // Sin acceso a uso no hay dato que enseñar: en su lugar, el camino para concederlo.
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(AlisioTokens.PillShape)
                        .clickable(role = Role.Button, onClick = usage.onGrant),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = stringResource(R.string.app_usage_allow),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlisioTokens.Accent
                    )
                }
            }
        }
        RoundIconButton(
            R.drawable.ic_info,
            stringResource(R.string.appmenu_app_info),
            AlisioTokens.TextSecondary,
            onAppInfo
        )
    }
}

/** El uso como texto corto: «1 h 20 min», «2 h», «35 min» o «< 1 min». */
@Composable
private fun usageText(millis: Long): String {
    val usage = usageDuration(millis)
    return when {
        usage.underMinute -> stringResource(R.string.usage_under_minute)
        usage.hours == 0 -> stringResource(R.string.usage_minutes, usage.minutes)
        usage.minutes == 0 -> stringResource(R.string.usage_hours, usage.hours)
        else -> stringResource(R.string.usage_hours_minutes, usage.hours, usage.minutes)
    }
}

/** El destino actual tal como se verá al abrirlo, o la invitación a elegir uno. */
@Composable
private fun TargetRow(target: SwipeTarget?, onClick: () -> Unit) {
    when (target) {
        null ->
            ItemRow(
                title = AnnotatedString(stringResource(R.string.appmenu_swipe_none)),
                leadingIcon = R.drawable.ic_swipe_up,
                subtitle = stringResource(R.string.appmenu_swipe_none_hint),
                trailing = stringResource(R.string.appmenu_swipe_choose),
                onClick = onClick
            )

        is SwipeTarget.App ->
            ItemRow(
                title = AnnotatedString(target.app.label),
                icon = target.app.icon,
                trailing = stringResource(R.string.settings_change),
                onClick = onClick
            )

        is SwipeTarget.Shortcut ->
            ItemRow(
                title = AnnotatedString(target.shortcut.label),
                icon = target.shortcut.app.icon,
                subtitle = target.shortcut.app.label,
                trailing = stringResource(R.string.settings_change),
                onClick = onClick
            )
    }
}

/**
 * Elige el destino secundario: una app o un acceso directo de cualquier app. Sin texto, salen
 * primero los accesos directos de la propia app, que es lo más probable; al escribir se busca en todo.
 */
@Composable
private fun TargetPicker(
    app: AppEntry,
    apps: List<AppEntry>,
    shortcuts: List<ShortcutEntry>,
    own: List<ShortcutEntry>,
    icons: Map<String, Bitmap>,
    target: SwipeTarget?,
    onPick: (SwipeTarget?) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    // Deslizar sobre una app para abrir esa misma app no aporta nada.
    val others = remember(apps, app) { apps.filter { it.key != app.key } }
    val foundApps = remember(others, query) { others.filterByName(query) { it.label } }
    val foundShortcuts =
        remember(shortcuts, own, query) {
            if (query.isBlank()) {
                own
            } else {
                shortcuts.distinctBy { it.key }.filterByName(query) { it.label }.take(PICKER_SHORTCUTS)
            }
        }
    val chosenApp = (target as? SwipeTarget.App)?.app?.key
    val chosenShortcut = (target as? SwipeTarget.Shortcut)?.shortcut?.key
    val selected = stringResource(R.string.settings_selected)

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(R.drawable.ic_arrow_back, stringResource(R.string.appmenu_back), AlisioTokens.Text, onBack)
            Text(
                text = stringResource(R.string.appmenu_swipe_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 24.sp),
                color = AlisioTokens.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        PillTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.appmenu_swipe_search),
            container = AlisioTokens.SurfaceElevated
        )
    }
    LazyColumn(Modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        if (query.isBlank() && target != null) {
            item {
                ItemRow(
                    title = AnnotatedString(stringResource(R.string.appmenu_swipe_clear)),
                    leadingIcon = R.drawable.ic_remove,
                    onClick = { onPick(null) }
                )
            }
        }
        // Sin texto, los accesos directos de la app van arriba; al buscar, las apps ganan a igual nombre.
        if (query.isBlank()) {
            shortcutRows(foundShortcuts, R.string.appmenu_shortcuts, icons, chosenShortcut, selected, onPick)
            if (foundShortcuts.isNotEmpty()) item { SectionLabel(stringResource(R.string.stack_all_apps)) }
        }
        items(foundApps, key = { "app:" + it.key }) { candidate ->
            val chosen = candidate.key == chosenApp
            ItemRow(
                title = AnnotatedString(candidate.label),
                icon = candidate.icon,
                trailing = if (chosen) selected else null,
                highlighted = chosen,
                onClick = { onPick(SwipeTarget.App(candidate)) }
            )
        }
        if (query.isNotBlank()) {
            shortcutRows(foundShortcuts, R.string.search_section_shortcuts, icons, chosenShortcut, selected, onPick)
        }
    }
}

/** Filas de accesos directos bajo su etiqueta; sin ninguno no pinta ni la etiqueta. */
private fun LazyListScope.shortcutRows(
    shortcuts: List<ShortcutEntry>,
    @StringRes label: Int,
    icons: Map<String, Bitmap>,
    chosenKey: String?,
    selected: String,
    onPick: (SwipeTarget?) -> Unit
) {
    if (shortcuts.isEmpty()) return
    item { SectionLabel(stringResource(label)) }
    items(shortcuts, key = { it.key }) { shortcut ->
        val chosen = shortcut.key == chosenKey
        ItemRow(
            title = AnnotatedString(shortcut.label),
            // Su icono propio si ya se cargó para la hoja; si no, el de su app, que dice de quién es.
            icon = icons[shortcut.key] ?: shortcut.app.icon,
            subtitle = shortcut.app.label,
            trailing = if (chosen) selected else null,
            highlighted = chosen,
            onClick = { onPick(SwipeTarget.Shortcut(shortcut)) }
        )
    }
}
