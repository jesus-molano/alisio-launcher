package dev.jesusmolano.alisio.feature.appmenu

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.search.filterByName
import dev.jesusmolano.alisio.core.usage.usageDuration
import dev.jesusmolano.alisio.core.zones.ZoneId
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.ShortcutEntry
import dev.jesusmolano.alisio.data.home.SwipeTarget
import dev.jesusmolano.alisio.ui.components.AlisioSheet
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.components.Chip
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.PillTextField
import dev.jesusmolano.alisio.ui.components.RoundIconButton
import dev.jesusmolano.alisio.ui.components.SectionLabel
import dev.jesusmolano.alisio.ui.components.SheetTitle
import dev.jesusmolano.alisio.ui.components.TextAction
import dev.jesusmolano.alisio.ui.components.selectedTile
import dev.jesusmolano.alisio.ui.theme.AlisioHaptics
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

// Los que caben sin que la hoja se convierta en una lista: las acciones de la app van primero.
private const val MENU_SHORTCUTS = 6
private const val PICKER_SHORTCUTS = 8
private val HeaderIconSize = 56.dp

// El icono de un hueco (44 dp) va centrado en su toque de 48 dp, y es el icono el que cae en el filo.
private val SlotRowInset = 12.dp - (AlisioTokens.MinTouch - AlisioTokens.IconSlot) / 2

/** Lo que el Interior de app necesita saber del uso de hoy y cómo pedirlo. */
class AppUsage(val granted: Boolean, val today: suspend () -> Long?, val onGrant: () -> Unit)

/** Lo que se puede hacer con el hueco del dock que ocupa la app; es lo mismo que ofrece su hoja. */
class SlotActions(val onChangeApp: () -> Unit, val onMakeStack: () -> Unit, val onClear: () -> Unit)

/**
 * «Poner en el dock»: los huecos del dock en miniatura, para llevar a uno la app de la hoja.
 *
 * @param holds si la app ya está en ese hueco, suelta o dentro de su pila.
 * @param description lo que TalkBack lee de cada hueco: las miniaturas no llevan nombre.
 * @param icon dibuja el hueco con las piezas del dock, al lado que se le pide.
 */
class DockPlacement(
    val slots: Int,
    val holds: (Int) -> Boolean,
    val description: @Composable (Int) -> String,
    val icon: @Composable (position: Int, size: Dp) -> Unit,
    val onPlace: (Int) -> Unit
)

/** Una isla de Zonas con el nombre que se enseña. */
class ZoneChoice(val id: ZoneId, val name: String)

/**
 * «Mover a otra isla»: la isla en la que está la app y las demás a las que puede ir.
 *
 * @param choices [current] y sus destinos posibles, en el orden en que se ven en Zonas.
 */
class ZoneMove(val current: ZoneId, val choices: List<ZoneChoice>, val onMove: (ZoneId) -> Unit)

/**
 * Interior de app (§5.6, §6): la hoja que sale al mantener pulsada una app del dock, de una pila,
 * de Zonas o de la barra. Enseña su uso de hoy, abre sus accesos directos, elige a dónde lleva
 * deslizar hacia arriba sobre su icono, la cambia de isla y deja a mano la ficha del sistema y,
 * según de dónde salga, las acciones de su hueco del dock o los huecos donde ponerla.
 *
 * @param shortcuts todos los accesos directos conocidos: los de [app] se listan, y cualquiera puede
 *   elegirse como destino.
 * @param target destino secundario actual del icono, si lo tiene.
 * @param gesturesEnabled con los gestos del dock apagados en los ajustes no se ofrece elegir destino.
 * @param slot acciones del hueco del dock; `null` cuando la hoja no sale de un hueco.
 * @param dock huecos donde poner la app; `null` cuando la hoja sale de un hueco, que ya es el suyo.
 * @param onGrantHomeRole pide ser la app de inicio; con valor, falta ese rol y sin él el sistema no
 *   da los accesos directos de ninguna app.
 * @param zones `null` mientras Zonas no ha cargado: entonces no se ofrece cambiar de isla.
 * @param shortcutIcon carga el icono propio de un acceso directo; `null` si no tiene.
 * @param look cambia el icono y el nombre de la app y la oculta; cada uno es un paso de la hoja.
 * @param onUnpin quita un acceso fijado a Alisio; `null` si desde aquí no se ofrece.
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
    dock: DockPlacement?,
    onGrantHomeRole: (() -> Unit)?,
    zones: ZoneMove?,
    shortcutIcon: suspend (ShortcutEntry) -> Bitmap?,
    look: AppLook,
    onOpenShortcut: (ShortcutEntry) -> Unit,
    onUnpin: ((ShortcutEntry) -> Unit)?,
    onSetTarget: (SwipeTarget?) -> Unit,
    onAppInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    // El segundo paso que ocupa la hoja, si hay alguno: nunca una hoja sobre otra.
    var step by remember { mutableStateOf(MenuStep.MAIN) }
    val own = rememberOwnShortcuts(shortcuts, app)
    val icons = rememberShortcutIcons(own, shortcutIcon)
    val back = { step = MenuStep.MAIN }

    AlisioSheet(onDismiss = onDismiss, onBack = back.takeIf { step != MenuStep.MAIN }) {
        when (step) {
            MenuStep.TARGET ->
                TargetPicker(
                    app = app,
                    apps = apps,
                    shortcuts = shortcuts,
                    own = own,
                    icons = icons,
                    target = target,
                    onPick = {
                        onSetTarget(it)
                        back()
                    },
                    onBack = back
                )

            MenuStep.ICON -> IconStep(app, look, onDone = onDismiss, onBack = back)

            MenuStep.NAME -> NameStep(app, look, onDone = onDismiss, onBack = back)

            MenuStep.MAIN -> MainStep(
                app, usage, onAppInfo, onGrantHomeRole, own, icons, onOpenShortcut, onUnpin, gesturesEnabled, target,
                zones, dock, slot, look, onStep = { step = it }
            )
        }
    }
}

/** Los pasos de la hoja: lo principal, o uno de sus segundos pasos. */
private enum class MenuStep { MAIN, TARGET, ICON, NAME }

/** Lo principal del Interior: cabecera, accesos directos, gesto, aspecto, isla y dock. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainStep(
    app: AppEntry,
    usage: AppUsage,
    onAppInfo: () -> Unit,
    onGrantHomeRole: (() -> Unit)?,
    own: List<ShortcutEntry>,
    icons: Map<String, Bitmap>,
    onOpenShortcut: (ShortcutEntry) -> Unit,
    onUnpin: ((ShortcutEntry) -> Unit)?,
    gesturesEnabled: Boolean,
    target: SwipeTarget?,
    zones: ZoneMove?,
    dock: DockPlacement?,
    slot: SlotActions?,
    look: AppLook,
    onStep: (MenuStep) -> Unit
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AlisioTokens.Outset)
            .padding(bottom = 16.dp)
    ) {
        Header(app, usage, onAppInfo)
        if (onGrantHomeRole != null) {
            SectionLabel(stringResource(R.string.appmenu_shortcuts))
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_home_role)),
                leadingIcon = R.drawable.ic_home,
                trailing = stringResource(R.string.settings_allow),
                onClick = onGrantHomeRole
            )
        } else if (own.isNotEmpty()) {
            SectionLabel(stringResource(R.string.appmenu_shortcuts))
            own.forEach { shortcut ->
                ItemRow(
                    title = AnnotatedString(shortcut.label),
                    icon = icons[shortcut.key],
                    leadingIcon = R.drawable.ic_bolt,
                    onClick = { onOpenShortcut(shortcut) },
                    // Uno fijado con «Añadir a inicio» se quita aquí: no hay otro sitio donde hacerlo.
                    actions =
                        if (onUnpin != null && shortcut.info.isPinned) {
                            {
                                RoundIconButton(
                                    R.drawable.ic_close,
                                    stringResource(R.string.appmenu_unpin, shortcut.label),
                                    AlisioTokens.TextSecondary,
                                    { onUnpin(shortcut) }
                                )
                            }
                        } else {
                            null
                        }
                )
            }
        }
        // Una sola fila no forma grupo: va sin etiqueta, y su título dice el gesto. El aire
        // la separa de los accesos directos, para que no se lea como uno más.
        if (gesturesEnabled) {
            Spacer(Modifier.height(AlisioTokens.GroupGap - AlisioTokens.LabelGap))
            TargetRow(target, icons, onClick = { onStep(MenuStep.TARGET) })
        }
        LookRows(
            app = app,
            onIcon = { onStep(MenuStep.ICON) },
            onName = { onStep(MenuStep.NAME) },
            onHide = { look.onHide(app, it) }
        )
        // Con una sola isla posible no hay nada que elegir.
        if (zones != null && zones.choices.size > 1) {
            SectionLabel(stringResource(R.string.appmenu_zone))
            // La isla actual va marcada; tocar otra mueve la app y la marca pasa a ella. En
            // varias líneas si no caben: una fila con desplazamiento escondería la última.
            FlowRow(
                Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                zones.choices.forEach { choice ->
                    val current = choice.id == zones.current
                    Chip(
                        label = choice.name,
                        selected = current,
                        onClick = { if (!current) zones.onMove(choice.id) }
                    )
                }
            }
        }
        if (dock != null) {
            SectionLabel(stringResource(R.string.appmenu_dock))
            DockSlots(dock)
        }
        if (slot != null) {
            SectionLabel(stringResource(R.string.appmenu_slot))
            ItemRow(
                title = AnnotatedString(stringResource(R.string.appmenu_change_app)),
                leadingIcon = R.drawable.ic_swap_horiz,
                onClick = slot.onChangeApp
            )
            ItemRow(
                title = AnnotatedString(stringResource(R.string.slot_make_stack)),
                leadingIcon = R.drawable.ic_stacks,
                onClick = slot.onMakeStack
            )
            // Lo que borra va lo último y aparte; se puede deshacer.
            Spacer(Modifier.height(AlisioTokens.GroupGap))
            ItemRow(
                title = AnnotatedString(stringResource(R.string.picker_clear)),
                leadingIcon = R.drawable.ic_close,
                secondary = true,
                onClick = slot.onClear
            )
        }
    }
}

/**
 * El selector de destino al deslizar en su propia hoja, para elegirlo sin pasar por el Interior de
 * app: lo usan los ajustes, que enseñan el destino de cada icono del dock.
 *
 * @param onSetTarget fija el destino; `null` lo quita. La hoja se cierra al elegir.
 */
@Composable
fun SwipeTargetSheet(
    app: AppEntry,
    apps: List<AppEntry>,
    shortcuts: List<ShortcutEntry>,
    target: SwipeTarget?,
    shortcutIcon: suspend (ShortcutEntry) -> Bitmap?,
    onSetTarget: (SwipeTarget?) -> Unit,
    onDismiss: () -> Unit
) {
    val own = rememberOwnShortcuts(shortcuts, app)
    val icons = rememberShortcutIcons(own, shortcutIcon)
    AlisioSheet(onDismiss = onDismiss) {
        TargetPicker(
            app = app,
            apps = apps,
            shortcuts = shortcuts,
            own = own,
            icons = icons,
            target = target,
            onPick = {
                onSetTarget(it)
                onDismiss()
            },
            onBack = null
        )
    }
}

/** Los accesos directos de [app], con las acciones por delante de las conversaciones. */
@Composable
private fun rememberOwnShortcuts(shortcuts: List<ShortcutEntry>, app: AppEntry): List<ShortcutEntry> =
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

/** El icono propio de cada acceso directo, por su clave, según se van leyendo. */
@Composable
private fun rememberShortcutIcons(
    shortcuts: List<ShortcutEntry>,
    shortcutIcon: suspend (ShortcutEntry) -> Bitmap?
): Map<String, Bitmap> {
    val icons by produceState(emptyMap<String, Bitmap>(), shortcuts.map { it.key }) {
        shortcuts.forEach { shortcut -> shortcutIcon(shortcut)?.let { value = value + (shortcut.key to it) } }
    }
    return icons
}

/** Icono, nombre y uso de hoy; al final, la ficha de la app en el sistema. */
@Composable
private fun Header(app: AppEntry, usage: AppUsage, onAppInfo: () -> Unit) {
    val today by produceState<Long?>(null, app.key, usage.granted) {
        value = if (usage.granted) usage.today() else null
    }
    Row(
        // Sin aire debajo: lo pone la etiqueta del primer grupo.
        Modifier.fillMaxWidth().padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.icon, HeaderIconSize)
        Spacer(Modifier.width(AlisioTokens.IconGap))
        Column(Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.headlineSmall,
                color = AlisioTokens.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (app.suspended) {
                // El sistema la tiene en pausa: lo dice antes que el uso, que es lo que explica el icono apagado.
                Text(
                    text = stringResource(R.string.app_suspended),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlisioTokens.TextSecondary
                )
            } else if (usage.granted) {
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
                // Su relleno interior se compensa para que el texto empiece donde el nombre.
                TextAction(
                    stringResource(R.string.app_usage_allow),
                    onClick = usage.onGrant,
                    modifier = Modifier.offset(x = (-12).dp)
                )
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

/** El uso como texto corto: «1 h 20 min», «2 h», «35 min» o «< 1 min». También lo usa la línea bajo la hora. */
@Composable
internal fun usageText(millis: Long): String {
    val usage = usageDuration(millis)
    return when {
        usage.underMinute -> stringResource(R.string.usage_under_minute)
        usage.hours == 0 -> stringResource(R.string.usage_minutes, usage.minutes)
        usage.minutes == 0 -> stringResource(R.string.usage_hours, usage.hours)
        else -> stringResource(R.string.usage_hours_minutes, usage.hours, usage.minutes)
    }
}

/**
 * El gesto de deslizar sobre el icono y a dónde lleva: con destino, su icono, para verlo como se
 * verá al abrirlo; sin él, el dibujo del gesto.
 */
@Composable
private fun TargetRow(target: SwipeTarget?, icons: Map<String, Bitmap>, onClick: () -> Unit) {
    val title = AnnotatedString(stringResource(R.string.appmenu_swipe))
    when (target) {
        null ->
            ItemRow(
                title = title,
                leadingIcon = R.drawable.ic_swipe_up,
                subtitle = stringResource(R.string.appmenu_swipe_none),
                trailing = stringResource(R.string.appmenu_swipe_choose),
                onClick = onClick
            )

        is SwipeTarget.App ->
            ItemRow(
                title = title,
                icon = target.app.icon,
                subtitle = target.app.label,
                trailing = stringResource(R.string.settings_change),
                onClick = onClick
            )

        // El icono de su app repetiría el de la cabecera cuando el acceso es de la misma app: se usa
        // el suyo propio si está cargado y, si no, el dibujo del gesto.
        is SwipeTarget.Shortcut ->
            ItemRow(
                title = title,
                icon = icons[target.shortcut.key],
                leadingIcon = R.drawable.ic_swipe_up,
                subtitle =
                    stringResource(R.string.appmenu_swipe_shortcut, target.shortcut.label, target.shortcut.app.label),
                trailing = stringResource(R.string.settings_change),
                onClick = onClick
            )
    }
}

/**
 * Los huecos del dock, de filo a filo como en el Inicio. Tocar uno pone ahí la app; el que ya la
 * tiene lleva el anillo de elegido y no hace nada.
 */
@Composable
private fun DockSlots(dock: DockPlacement) {
    val shape = AlisioTokens.DockIconShape
    val view = LocalView.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = SlotRowInset),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        repeat(dock.slots) { position ->
            val here = dock.holds(position)
            val description = dock.description(position)
            Box(
                Modifier
                    .size(AlisioTokens.MinTouch)
                    .clip(shape)
                    .selectedTile(here, shape)
                    .selectable(selected = here, role = Role.RadioButton) {
                        if (!here) {
                            AlisioHaptics.done(view)
                            dock.onPlace(position)
                        }
                    }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center
            ) { dock.icon(position, AlisioTokens.IconSlot) }
        }
    }
}

/**
 * Elige el destino secundario: una app o un acceso directo de cualquier app. Sin texto, salen
 * primero los accesos directos de la propia app, que es lo más probable; al escribir se busca en todo.
 */
@Composable
private fun ColumnScope.TargetPicker(
    app: AppEntry,
    apps: List<AppEntry>,
    shortcuts: List<ShortcutEntry>,
    own: List<ShortcutEntry>,
    icons: Map<String, Bitmap>,
    target: SwipeTarget?,
    onPick: (SwipeTarget?) -> Unit,
    onBack: (() -> Unit)?
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

    SheetTitle(stringResource(R.string.appmenu_swipe_title), onBack = onBack)
    // Invertida como AppPicker: lo primero que se emite queda abajo, junto al buscador. Por eso van
    // primero las apps (la A, o la que mejor coincide, junto al pulgar) y después, encima, los accesos
    // directos y «Quitar el destino». Cada etiqueta se emite tras sus filas para quedar encima de ellas.
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = AlisioTokens.Outset, vertical = 8.dp)
    ) {
        items(foundApps, key = { "app:" + it.key }) { candidate ->
            ItemRow(
                title = AnnotatedString(candidate.label),
                icon = candidate.icon,
                selected = candidate.key == chosenApp,
                onClick = { onPick(SwipeTarget.App(candidate)) }
            )
        }
        // Con accesos directos encima, la etiqueta separa las apps de ellos.
        if (foundShortcuts.isNotEmpty() && foundApps.isNotEmpty()) {
            item(key = "label:apps") { SectionLabel(stringResource(R.string.stack_all_apps)) }
        }
        // Sin texto son los de la propia app; al buscar, los de cualquier app.
        val shortcutsLabel = if (query.isBlank()) R.string.appmenu_shortcuts else R.string.search_section_shortcuts
        shortcutRows(foundShortcuts, shortcutsLabel, icons, chosenShortcut, onPick)
        if (query.isBlank() && target != null) {
            item(key = "clear") {
                ItemRow(
                    title = AnnotatedString(stringResource(R.string.appmenu_swipe_clear)),
                    leadingIcon = R.drawable.ic_close,
                    secondary = true,
                    onClick = { onPick(null) }
                )
            }
        }
    }
    // El buscador va al pie, junto al teclado, como en la barra.
    PillTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = stringResource(R.string.appmenu_swipe_search),
        leadingIcon = R.drawable.ic_search,
        modifier =
            Modifier
                .imePadding()
                .padding(start = AlisioTokens.Outset, end = AlisioTokens.Outset, bottom = 12.dp)
    )
}

/**
 * Filas de accesos directos bajo su etiqueta, para una lista invertida: se emiten de la última a la
 * primera y la etiqueta al final, así se leen en su orden de arriba abajo. Sin ninguno no pinta nada.
 */
private fun LazyListScope.shortcutRows(
    shortcuts: List<ShortcutEntry>,
    @StringRes label: Int,
    icons: Map<String, Bitmap>,
    chosenKey: String?,
    onPick: (SwipeTarget?) -> Unit
) {
    if (shortcuts.isEmpty()) return
    items(shortcuts.asReversed(), key = { it.key }) { shortcut ->
        ItemRow(
            title = AnnotatedString(shortcut.label),
            // Su icono propio si ya se cargó para la hoja; si no, el de su app, que dice de quién es.
            icon = icons[shortcut.key] ?: shortcut.app.icon,
            subtitle = shortcut.app.label,
            selected = shortcut.key == chosenKey,
            onClick = { onPick(SwipeTarget.Shortcut(shortcut)) }
        )
    }
    item(key = "label:$label") { SectionLabel(stringResource(label)) }
}
