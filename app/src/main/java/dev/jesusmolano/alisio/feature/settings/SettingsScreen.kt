package dev.jesusmolano.alisio.feature.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.extras.expenseUrl
import dev.jesusmolano.alisio.core.extras.parseExpense
import dev.jesusmolano.alisio.core.icons.IconStyle
import dev.jesusmolano.alisio.core.pause.PAUSE_SECONDS
import dev.jesusmolano.alisio.core.pause.pausedFirst
import dev.jesusmolano.alisio.core.search.filterByName
import dev.jesusmolano.alisio.core.zones.ZoneId
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.installedIconPacks
import dev.jesusmolano.alisio.data.settings.AlisioSettings
import dev.jesusmolano.alisio.data.settings.Prefs
import dev.jesusmolano.alisio.data.settings.WallpaperDim
import dev.jesusmolano.alisio.feature.search.appLabel
import dev.jesusmolano.alisio.feature.search.webSearchApps
import dev.jesusmolano.alisio.feature.zones.ZoneEdit
import dev.jesusmolano.alisio.feature.zones.ZoneEditSheet
import dev.jesusmolano.alisio.feature.zones.ZoneView
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.components.Chip
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.PillTextField
import dev.jesusmolano.alisio.ui.components.SectionLabel
import dev.jesusmolano.alisio.ui.components.UnreadDot
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

private val ScreenMargin = 16.dp
private val UnreadAppIconSize = 48.dp

/** Las listas de apps que se despliegan dentro de la pantalla. */
private enum class Choice { ASSISTANT, WEB_SEARCH, PAUSE }

// Asistentes de IA conocidos: si están instalados salen los primeros al elegir.
private val KNOWN_ASSISTANTS =
    setOf(
        "com.anthropic.claude",
        "com.openai.chatgpt",
        "com.google.android.apps.bard",
        "ai.x.grok",
        "ai.perplexity.app.android",
        "com.microsoft.copilot",
        "com.deepseek.chat",
        "ai.mistral.chat"
    )

/**
 * Lo que la sección Zonas enseña y hace.
 *
 * @param islands las islas propias, con sus apps, en su orden.
 * @param onOpen cierra los ajustes y abre Zonas, para ver cómo queda.
 */
class ZoneSettings(
    val islands: List<ZoneView>,
    val onOpen: () -> Unit,
    val onCreate: (String) -> Unit,
    val onRename: (Long, String) -> Unit,
    val onDelete: (Long) -> Unit
)

/**
 * Ajustes de Alisio: una pantalla por secciones que crece con cada fase. Cada opción se aplica al
 * tocarla, sin botón de guardar.
 *
 * @param onContactsClick pide el permiso de contactos o, si ya está concedido, abre la ficha de la app.
 * @param bluetoothDevice nombre del dispositivo de audio Bluetooth conectado ahora, si lo hay.
 * @param onBluetoothClick lo mismo que [onContactsClick], con el permiso de dispositivos cercanos.
 * @param onNotificationsClick abre el acceso a notificaciones del sistema, donde se da y se quita.
 * @param dockApps las apps del dock y de sus pilas: cada una puede apagar su punto de no leído.
 * @param onUsageClick abre el acceso a uso del sistema, donde se da y se quita.
 * @param accessibilityEnabled si el servicio de accesibilidad está activado en el sistema.
 * @param onAccessibilityClick enseña el aviso previo o, si ya está activado, abre los ajustes de
 *   accesibilidad del sistema, donde se desactiva.
 * @param onPreviewPause enseña la pausa de esa app tal como saldrá, sin abrirla.
 */
@Composable
fun SettingsScreen(
    zones: ZoneSettings,
    prefs: Prefs,
    settings: AlisioSettings,
    apps: List<AppEntry>,
    contactsGranted: Boolean,
    onContactsClick: () -> Unit,
    bluetoothGranted: Boolean,
    bluetoothDevice: String?,
    onBluetoothClick: () -> Unit,
    notificationsGranted: Boolean,
    onNotificationsClick: () -> Unit,
    dockApps: List<AppEntry>,
    usageGranted: Boolean,
    onUsageClick: () -> Unit,
    accessibilityEnabled: Boolean,
    onAccessibilityClick: () -> Unit,
    onPreviewPause: (AppEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val packs = remember { installedIconPacks(context) }
    val webApps = remember { context.webSearchApps() }
    val iconByPackage = remember(apps) { apps.associate { it.component.packageName to it.icon } }
    // Qué lista está desplegada: solo una a la vez, para que la pantalla no se alargue.
    var expanded by remember { mutableStateOf<Choice?>(null) }
    var appFilter by remember { mutableStateOf("") }
    // La plantilla del gasto rápido mientras se escribe. Vive aquí y no en `prefs`: lo guardado
    // vuelve con retraso, y el campo perdería letras y el cursor.
    var expenseTemplate by remember { mutableStateOf(prefs.expenseTemplate) }
    // Una app por paquete: la pausa va por paquete, y la copia del perfil de trabajo repetiría fila.
    val pausable = remember(apps) { apps.distinctBy { it.component.packageName } }
    val pausedApps = pausable.filter { it.component.packageName in prefs.pausedApps }
    // El orden de la lista de pausa se fija al desplegarla: si cambiara con cada toque, la fila
    // tocada saltaría arriba bajo el dedo.
    val pauseCandidates =
        remember(pausable, expanded == Choice.PAUSE) {
            pausedFirst(pausable, prefs.pausedApps) { it.component.packageName }
        }
    // Isla propia que se está creando o cambiando en su hoja.
    var zoneEdit by remember { mutableStateOf<ZoneEdit?>(null) }
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .background(AlisioTokens.Background)
                // La pantalla se queda los toques: no llegan al Inicio de debajo.
                .pointerInput(Unit) {}
                .systemBarsPadding()
                // Con el teclado abierto al filtrar apps, el final de la lista sigue siendo alcanzable.
                .imePadding(),
        contentPadding = PaddingValues(horizontal = ScreenMargin, vertical = 16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineLarge,
                color = AlisioTokens.Text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
            )
        }

        item { SectionLabel(stringResource(R.string.settings_appearance)) }
        item {
            Option(R.string.settings_accent) {
                AlisioTokens.Accents.forEachIndexed { index, color ->
                    val description = stringResource(R.string.settings_accent_option, index + 1)
                    val chosen = index == prefs.accent
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { settings.setAccent(index) }
                            .semantics {
                                contentDescription = description
                                role = Role.RadioButton
                                selected = chosen
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(if (chosen) 36.dp else 28.dp)
                                .background(color, CircleShape)
                                .border(if (chosen) 3.dp else 0.dp, AlisioTokens.Text, CircleShape)
                        )
                    }
                }
            }
        }
        item {
            Option(R.string.settings_wallpaper_dim) {
                WallpaperDim.entries.forEach { dim ->
                    Chip(
                        label =
                            stringResource(
                                when (dim) {
                                    WallpaperDim.LIGHT -> R.string.settings_dim_light
                                    WallpaperDim.MEDIUM -> R.string.settings_dim_medium
                                    WallpaperDim.STRONG -> R.string.settings_dim_strong
                                }
                            ),
                        selected = prefs.wallpaperDim == dim,
                        onClick = { settings.setWallpaperDim(dim) }
                    )
                }
            }
        }

        item { SectionLabel(stringResource(R.string.settings_search)) }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_assistant)),
                icon = iconByPackage[prefs.assistantPackage],
                leading = "?",
                subtitle =
                    context.appLabel(prefs.assistantPackage)
                        ?: stringResource(R.string.settings_assistant_missing),
                trailing = stringResource(R.string.settings_change),
                onClick = {
                    appFilter = ""
                    expanded = if (expanded == Choice.ASSISTANT) null else Choice.ASSISTANT
                }
            )
        }
        if (expanded == Choice.ASSISTANT) {
            item {
                PillTextField(
                    value = appFilter,
                    onValueChange = { appFilter = it },
                    placeholder = stringResource(R.string.picker_search),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            // Primero los asistentes conocidos que estén instalados; después, cualquier otra app.
            val known = apps.filter { it.component.packageName in KNOWN_ASSISTANTS }
            val candidates = (known + apps).distinctBy { it.component.packageName }.filterByName(appFilter) { it.label }
            items(candidates, key = { "assistant:" + it.component.packageName }) { app ->
                val chosen = app.component.packageName == prefs.assistantPackage
                ItemRow(
                    title = AnnotatedString(app.label),
                    icon = app.icon,
                    trailing = if (chosen) stringResource(R.string.settings_selected) else null,
                    highlighted = chosen,
                    onClick = {
                        settings.setAssistant(app.component.packageName)
                        expanded = null
                    }
                )
            }
        }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_web_search)),
                icon = prefs.webSearchPackage?.let(iconByPackage::get),
                leading = "⌕",
                subtitle =
                    prefs.webSearchPackage?.let { context.appLabel(it) }
                        ?: stringResource(R.string.settings_web_search_system),
                trailing = stringResource(R.string.settings_change),
                onClick = { expanded = if (expanded == Choice.WEB_SEARCH) null else Choice.WEB_SEARCH }
            )
        }
        if (expanded == Choice.WEB_SEARCH) {
            item {
                ItemRow(
                    title = AnnotatedString(stringResource(R.string.settings_web_search_system)),
                    leading = "⌕",
                    trailing =
                        if (prefs.webSearchPackage == null) stringResource(R.string.settings_selected) else null,
                    highlighted = prefs.webSearchPackage == null,
                    onClick = {
                        settings.setWebSearch(null)
                        expanded = null
                    }
                )
            }
            items(webApps, key = { "web:" + it.packageName }) { app ->
                val chosen = app.packageName == prefs.webSearchPackage
                ItemRow(
                    title = AnnotatedString(app.label),
                    icon = iconByPackage[app.packageName],
                    leading = "⌕",
                    trailing = if (chosen) stringResource(R.string.settings_selected) else null,
                    highlighted = chosen,
                    onClick = {
                        settings.setWebSearch(app.packageName)
                        expanded = null
                    }
                )
            }
        }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_clipboard)),
                leadingIcon = R.drawable.ic_paste,
                // Encendido, lo que importa saber es el aviso del sistema, que es por lo que se apagaría.
                subtitle =
                    stringResource(
                        if (prefs.clipboardActions) R.string.settings_clipboard_on else R.string.settings_clipboard_hint
                    ),
                trailing =
                    stringResource(if (prefs.clipboardActions) R.string.settings_on else R.string.settings_off),
                highlighted = prefs.clipboardActions,
                onClick = { settings.toggleClipboardActions() }
            )
        }

        item { SectionLabel(stringResource(R.string.settings_expense)) }
        item {
            ExpenseTemplate(
                template = expenseTemplate,
                onChange = {
                    expenseTemplate = it
                    settings.setExpenseTemplate(it)
                }
            )
        }

        item { SectionLabel(stringResource(R.string.search_section_people)) }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_contacts)),
                leading = "☺",
                subtitle =
                    stringResource(
                        if (contactsGranted) R.string.settings_contacts_on else R.string.people_allow_hint
                    ),
                trailing =
                    stringResource(if (contactsGranted) R.string.settings_on else R.string.settings_allow),
                highlighted = contactsGranted,
                onClick = onContactsClick
            )
        }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_contact_photos)),
                leadingIcon = R.drawable.ic_person,
                // Sin permiso no hay fotos que enseñar: la fila dice qué falta y no se marca.
                subtitle =
                    stringResource(
                        if (contactsGranted) R.string.settings_contact_photos_hint else R.string.people_allow_hint
                    ),
                trailing = stringResource(if (prefs.contactPhotos) R.string.settings_on else R.string.settings_off),
                highlighted = prefs.contactPhotos && contactsGranted,
                onClick = { settings.toggleContactPhotos() }
            )
        }

        item { SectionLabel(stringResource(R.string.settings_icons)) }
        item {
            StyleRow(R.string.icons_color, R.string.icons_color_hint, "◼", IconStyle.Color, prefs, settings)
        }
        item {
            StyleRow(R.string.icons_mono, R.string.icons_mono_hint, "◻", IconStyle.Monochrome, prefs, settings)
        }
        item {
            StyleRow(R.string.icons_system, R.string.icons_system_hint, "◯", IconStyle.System, prefs, settings)
        }
        items(packs, key = { it.packageName }) { pack ->
            val style = IconStyle.Pack(pack.packageName)
            ItemRow(
                title = AnnotatedString(pack.label),
                leading = "▣",
                subtitle = stringResource(R.string.icons_pack_hint),
                trailing = if (style == prefs.iconStyle) stringResource(R.string.settings_selected) else null,
                highlighted = style == prefs.iconStyle,
                onClick = { settings.setIconStyle(style) }
            )
        }

        item { SectionLabel(stringResource(R.string.now_label)) }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_now)),
                leading = "✦",
                subtitle = stringResource(R.string.settings_now_hint),
                trailing = stringResource(if (prefs.nowEnabled) R.string.settings_on else R.string.settings_off),
                highlighted = prefs.nowEnabled,
                onClick = { settings.toggleNow() }
            )
        }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_bluetooth)),
                leadingIcon = R.drawable.ic_bluetooth,
                // Con permiso, la fila enseña lo que la regla ve ahora mismo en vez de explicarla.
                subtitle =
                    when {
                        !bluetoothGranted -> stringResource(R.string.settings_bluetooth_hint)
                        bluetoothDevice != null -> stringResource(R.string.now_reason_bluetooth, bluetoothDevice)
                        else -> stringResource(R.string.settings_bluetooth_none)
                    },
                trailing =
                    stringResource(if (bluetoothGranted) R.string.settings_on else R.string.settings_allow),
                highlighted = bluetoothGranted,
                onClick = onBluetoothClick
            )
        }

        item { SectionLabel(stringResource(R.string.settings_notifications)) }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_notification_access)),
                leadingIcon = R.drawable.ic_notifications,
                subtitle =
                    stringResource(
                        if (notificationsGranted) {
                            R.string.settings_notification_access_on
                        } else {
                            R.string.settings_notification_access_hint
                        }
                    ),
                trailing =
                    stringResource(if (notificationsGranted) R.string.settings_on else R.string.settings_allow),
                highlighted = notificationsGranted,
                onClick = onNotificationsClick
            )
        }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_player)),
                leadingIcon = R.drawable.ic_play,
                subtitle = stringResource(R.string.settings_player_hint),
                trailing = stringResource(if (prefs.playerEnabled) R.string.settings_on else R.string.settings_off),
                highlighted = prefs.playerEnabled,
                onClick = { settings.togglePlayer() }
            )
        }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_unread)),
                leadingIcon = R.drawable.ic_dot,
                subtitle = stringResource(R.string.settings_unread_hint),
                trailing = stringResource(if (prefs.unreadDots) R.string.settings_on else R.string.settings_off),
                highlighted = prefs.unreadDots,
                onClick = { settings.toggleUnreadDots() }
            )
        }
        if (prefs.unreadDots) {
            item { UnreadApps(dockApps, prefs.unreadMuted, settings::toggleUnreadFor) }
        }

        item { SectionLabel(stringResource(R.string.settings_dock)) }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_dock_labels)),
                leading = "Aa",
                subtitle = stringResource(R.string.settings_dock_labels_hint),
                trailing = stringResource(if (prefs.dockLabels) R.string.settings_on else R.string.settings_off),
                highlighted = prefs.dockLabels,
                onClick = { settings.toggleDockLabels() }
            )
        }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_dock_gestures)),
                leadingIcon = R.drawable.ic_swipe_up,
                subtitle = stringResource(R.string.settings_dock_gestures_hint),
                trailing = stringResource(if (prefs.dockGestures) R.string.settings_on else R.string.settings_off),
                highlighted = prefs.dockGestures,
                onClick = { settings.toggleDockGestures() }
            )
        }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_usage)),
                leadingIcon = R.drawable.ic_schedule,
                subtitle =
                    stringResource(if (usageGranted) R.string.settings_usage_on else R.string.settings_usage_hint),
                trailing = stringResource(if (usageGranted) R.string.settings_on else R.string.settings_allow),
                highlighted = usageGranted,
                onClick = onUsageClick
            )
        }

        item { SectionLabel(stringResource(R.string.settings_gestures)) }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_accessibility)),
                leadingIcon = R.drawable.ic_accessibility,
                subtitle =
                    stringResource(
                        if (accessibilityEnabled) {
                            R.string.settings_accessibility_on
                        } else {
                            R.string.settings_accessibility_hint
                        }
                    ),
                trailing =
                    stringResource(if (accessibilityEnabled) R.string.settings_on else R.string.settings_allow),
                highlighted = accessibilityEnabled,
                onClick = onAccessibilityClick
            )
        }
        item {
            GestureRow(
                title = R.string.settings_lock_double_tap,
                hint = R.string.settings_lock_double_tap_hint,
                icon = R.drawable.ic_lock,
                on = prefs.lockDoubleTap,
                serviceEnabled = accessibilityEnabled,
                onClick = { settings.toggleLockDoubleTap() }
            )
        }
        item {
            GestureRow(
                title = R.string.settings_notifications_swipe,
                hint = R.string.settings_notifications_swipe_hint,
                icon = R.drawable.ic_swipe_down,
                on = prefs.notificationsSwipe,
                serviceEnabled = accessibilityEnabled,
                onClick = { settings.toggleNotificationsSwipe() }
            )
        }

        item { SectionLabel(stringResource(R.string.settings_pause)) }
        item {
            Note(stringResource(R.string.settings_pause_hint))
            PausedApps(pausedApps, settings::togglePauseFor)
        }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_pause_apps)),
                leadingIcon = R.drawable.ic_hourglass,
                subtitle = stringResource(R.string.settings_pause_apps_hint),
                trailing = stringResource(R.string.settings_change),
                onClick = {
                    appFilter = ""
                    expanded = if (expanded == Choice.PAUSE) null else Choice.PAUSE
                }
            )
        }
        if (expanded == Choice.PAUSE) {
            item {
                PillTextField(
                    value = appFilter,
                    onValueChange = { appFilter = it },
                    placeholder = stringResource(R.string.picker_search),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            // Se pueden marcar varias: la lista no se cierra al tocar una.
            items(pauseCandidates.filterByName(appFilter) { it.label }, key = { "pause:" + it.key }) { app ->
                val packageName = app.component.packageName
                val chosen = packageName in prefs.pausedApps
                ItemRow(
                    title = AnnotatedString(app.label),
                    icon = app.icon,
                    trailing = if (chosen) stringResource(R.string.settings_pause_on) else null,
                    highlighted = chosen,
                    onClick = { settings.togglePauseFor(packageName) }
                )
            }
        }
        if (pausedApps.isNotEmpty()) {
            item {
                Option(R.string.settings_pause_duration) {
                    PAUSE_SECONDS.forEach { seconds ->
                        Chip(
                            label = stringResource(R.string.pause_seconds_short, seconds),
                            selected = prefs.pauseSeconds == seconds,
                            onClick = { settings.setPauseSeconds(seconds) }
                        )
                    }
                }
            }
            item {
                val sample = pausedApps.first()
                ItemRow(
                    title = AnnotatedString(stringResource(R.string.settings_pause_preview)),
                    icon = sample.icon,
                    subtitle = stringResource(R.string.settings_pause_preview_hint, sample.label),
                    onClick = { onPreviewPause(sample) }
                )
            }
        }

        item { SectionLabel(stringResource(R.string.settings_zones)) }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_zones_open)),
                leadingIcon = R.drawable.ic_zones,
                subtitle = stringResource(R.string.zones_search_subtitle),
                onClick = zones.onOpen
            )
        }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_zones_pinch)),
                leadingIcon = R.drawable.ic_pinch,
                subtitle = stringResource(R.string.settings_zones_pinch_hint),
                trailing = stringResource(if (prefs.zonesPinch) R.string.settings_on else R.string.settings_off),
                highlighted = prefs.zonesPinch,
                onClick = { settings.toggleZonesPinch() }
            )
        }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.settings_zones_labels)),
                leadingIcon = R.drawable.ic_title,
                subtitle = stringResource(R.string.settings_zones_labels_hint),
                trailing = stringResource(if (prefs.zonesLabels) R.string.settings_on else R.string.settings_off),
                highlighted = prefs.zonesLabels,
                onClick = { settings.toggleZonesLabels() }
            )
        }
        // Las islas propias: tocar una abre la hoja donde se renombra o se borra.
        items(zones.islands, key = { "zone:" + it.id.encode() }) { island ->
            val name = island.name.orEmpty()
            ItemRow(
                title = AnnotatedString(name),
                // Su primera app la hace reconocible; vacía, el dibujo de Zonas.
                icon = island.apps.firstOrNull()?.icon,
                leadingIcon = R.drawable.ic_zones,
                subtitle = pluralStringResource(R.plurals.zone_apps, island.apps.size, island.apps.size),
                trailing = stringResource(R.string.settings_change),
                onClick = { (island.id as? ZoneId.Custom)?.let { zoneEdit = ZoneEdit.Existing(it.id, name) } }
            )
        }
        item {
            ItemRow(
                title = AnnotatedString(stringResource(R.string.zone_new)),
                leadingIcon = R.drawable.ic_add,
                subtitle = stringResource(R.string.settings_zones_new_hint),
                onClick = { zoneEdit = ZoneEdit.New }
            )
        }
    }

    zoneEdit?.let { edit ->
        ZoneEditSheet(
            edit = edit,
            onSave = { name ->
                when (edit) {
                    ZoneEdit.New -> zones.onCreate(name)
                    is ZoneEdit.Existing -> zones.onRename(edit.id, name)
                }
                zoneEdit = null
            },
            onDelete = {
                (edit as? ZoneEdit.Existing)?.let { zones.onDelete(it.id) }
                zoneEdit = null
            },
            onDismiss = { zoneEdit = null }
        )
    }
}

/**
 * La plantilla del gasto rápido y, debajo, la dirección que abriría el gasto de ejemplo: así se ve
 * cómo queda mientras se escribe. Sin plantilla, o con una que no vale, dice que está apagado.
 */
@Composable
private fun ExpenseTemplate(template: String, onChange: (String) -> Unit) {
    val sample = stringResource(R.string.settings_expense_sample)
    val url = remember(template, sample) { parseExpense(sample)?.let { expenseUrl(template, it) } }
    Note(stringResource(R.string.settings_expense_hint, sample))
    PillTextField(
        value = template,
        onValueChange = onChange,
        placeholder = stringResource(R.string.settings_expense_placeholder),
        modifier = Modifier.padding(vertical = 8.dp)
    )
    when {
        url != null -> {
            Note(stringResource(R.string.settings_expense_preview, sample))
            Note(url, color = AlisioTokens.Accent)
        }

        template.isBlank() -> Note(stringResource(R.string.settings_expense_off))

        else -> Note(stringResource(R.string.settings_expense_invalid))
    }
}

/** Texto de apoyo entre las filas de una sección. */
@Composable
private fun Note(text: String, color: Color = AlisioTokens.TextSecondary) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    )
}

/**
 * Un gesto del Inicio que depende del servicio de accesibilidad. Sin el servicio la fila dice qué
 * falta en vez de describir el gesto, y no se marca aunque el gesto esté encendido: no hace nada.
 */
@Composable
private fun GestureRow(
    @StringRes title: Int,
    @StringRes hint: Int,
    @DrawableRes icon: Int,
    on: Boolean,
    serviceEnabled: Boolean,
    onClick: () -> Unit
) {
    ItemRow(
        title = AnnotatedString(stringResource(title)),
        leadingIcon = icon,
        subtitle = stringResource(if (serviceEnabled) hint else R.string.settings_gesture_needs_service),
        trailing = stringResource(if (on) R.string.settings_on else R.string.settings_off),
        highlighted = on && serviceEnabled,
        onClick = onClick
    )
}

/**
 * Las apps que llevan pausa, con su icono: es lo elegido, a la vista. Tocar una le quita la pausa;
 * para añadir otras está la lista de debajo.
 */
@Composable
private fun PausedApps(apps: List<AppEntry>, onRemove: (String) -> Unit) {
    if (apps.isEmpty()) {
        Note(stringResource(R.string.settings_pause_none))
        return
    }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp)) {
        apps.forEach { app ->
            val description = stringResource(R.string.settings_pause_remove, app.label)
            Box(
                Modifier
                    .size(64.dp)
                    .clip(AlisioTokens.RowShape)
                    .clickable(role = Role.Button) { onRemove(app.component.packageName) }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center
            ) { AppIcon(app.icon, UnreadAppIconSize) }
        }
    }
}

/** Una opción con su nombre encima y sus valores en fila. */
@Composable
private fun Option(@StringRes title: Int, values: @Composable () -> Unit) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = AlisioTokens.Text,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp)
    )
    Row(
        modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { values() }
}

/**
 * Las apps del dock tal como se ven en él: con su punto si está encendido y atenuadas sin él si está
 * apagado. Tocar una lo cambia. Se enseñan con el punto puesto aunque ahora no tengan nada sin leer:
 * aquí se elige cómo quedará, no se mira el estado.
 */
@Composable
private fun UnreadApps(apps: List<AppEntry>, muted: Set<String>, onToggle: (String) -> Unit) {
    Text(
        text =
            stringResource(
                if (apps.isEmpty()) R.string.settings_unread_apps_empty else R.string.settings_unread_apps
            ),
        style = MaterialTheme.typography.bodyMedium,
        color = AlisioTokens.TextSecondary,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp)
    )
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp)) {
        apps.forEach { app ->
            val packageName = app.component.packageName
            val on = packageName !in muted
            val description = stringResource(R.string.settings_unread_app, app.label)
            Box(
                Modifier
                    .size(64.dp)
                    .clip(AlisioTokens.RowShape)
                    .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle(packageName) })
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(UnreadAppIconSize)) {
                    AppIcon(app.icon, UnreadAppIconSize, Modifier.alpha(if (on) 1f else 0.38f))
                    if (on) UnreadDot()
                }
            }
        }
    }
}

@Composable
private fun StyleRow(
    @StringRes title: Int,
    @StringRes hint: Int,
    glyph: String,
    style: IconStyle,
    prefs: Prefs,
    settings: AlisioSettings
) {
    ItemRow(
        title = AnnotatedString(stringResource(title)),
        leading = glyph,
        subtitle = stringResource(hint),
        trailing = if (style == prefs.iconStyle) stringResource(R.string.settings_selected) else null,
        highlighted = style == prefs.iconStyle,
        onClick = { settings.setIconStyle(style) }
    )
}
