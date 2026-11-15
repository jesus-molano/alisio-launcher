package dev.jesusmolano.alisio.feature.home

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.search.filterByName
import dev.jesusmolano.alisio.core.search.moved
import dev.jesusmolano.alisio.core.text.takeGraphemes
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.toSections
import dev.jesusmolano.alisio.data.home.StackIcon
import dev.jesusmolano.alisio.data.settings.Prefs
import dev.jesusmolano.alisio.feature.search.appLabel
import dev.jesusmolano.alisio.feature.search.searchHint
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.PillTextField
import dev.jesusmolano.alisio.ui.components.SectionLabel
import dev.jesusmolano.alisio.ui.components.formatClock
import dev.jesusmolano.alisio.ui.components.rememberNow
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import java.time.format.DateTimeFormatter

private val DockIconSize = 52.dp
private val StackMiniIconSize = 19.dp
private val PillHeight = 56.dp
private val StackOrderRowHeight = 56.dp
private val DockHeight = 86.dp
private val ScreenMargin = 24.dp

// Alto de dock + píldora con sus separaciones: lo que el índice de Trazo deja libre por abajo.
private val BottomAreaHeight = 176.dp
private val OpenSearchDrag = 48.dp
private const val STACK_ICON_APPS = 4
private const val STACK_GLYPH_LENGTH = 2
private const val TAG = "HomeScreen"

/**
 * Inicio de §6: reloj y fecha arriba; abajo, el dock y la píldora de la barra. Cada hueco del dock
 * es una app o una pila con nombre; mantenerlo pulsado lo configura. Deslizar hacia arriba en una
 * zona vacía o tocar la píldora abre la barra; mantener pulsada una zona vacía abre los ajustes de
 * Alisio. Trazo va en el borde derecho.
 *
 * @param homePresses sube cada vez que se pulsa «inicio» estando ya en Alisio; cierra las hojas abiertas.
 * @param searchOpen con la barra encima, Trazo se retira: su índice seguiría recibiendo toques y
 *   quitándole al sistema el gesto atrás del borde derecho.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    homePresses: Int,
    searchOpen: Boolean,
    prefs: Prefs,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val loadedDock by viewModel.dock.collectAsStateWithLifecycle()
    val dock = loadedDock.orEmpty()
    val context = LocalContext.current
    val resources = LocalResources.current
    val bars = WindowInsets.systemBars.asPaddingValues()
    val dim = prefs.wallpaperDim.factor
    // Posición del hueco que se está configurando y de la pila abierta.
    var editing by remember { mutableStateOf<Int?>(null) }
    var openStack by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(homePresses) {
        editing = null
        openStack = null
    }

    fun launch(app: AppEntry) {
        if (!viewModel.launch(app)) {
            Toast.makeText(context, resources.getString(R.string.app_launch_failed, app.label), Toast.LENGTH_SHORT)
                .show()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                // Velo sobre el fondo de pantalla, más denso abajo, y brillo suave del acento al pie.
                drawRect(
                    Brush.verticalGradient(
                        0f to AlisioTokens.Background.copy(alpha = (0.38f * dim).coerceAtMost(1f)),
                        0.55f to AlisioTokens.Background.copy(alpha = (0.30f * dim).coerceAtMost(1f)),
                        // Abajo nunca baja de lo que hace legibles el dock y la píldora.
                        1f to AlisioTokens.Background.copy(alpha = (0.82f * dim).coerceIn(0.6f, 1f))
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(AlisioTokens.Accent.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width / 2, size.height),
                        radius = size.width * 0.9f
                    )
                )
            }
            .pointerInput(onOpenSearch) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { _, amount -> dragged += amount },
                    onDragEnd = { if (dragged < -OpenSearchDrag.toPx()) onOpenSearch() }
                )
            }
            .pointerInput(onOpenSettings) { detectTapGestures(onLongPress = { onOpenSettings() }) }
    ) {
        ClockAndDate(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = bars.calculateTopPadding() + 40.dp, start = ScreenMargin, end = ScreenMargin)
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = ScreenMargin, end = ScreenMargin, bottom = bars.calculateBottomPadding() + 16.dp)
        ) {
            // Mientras carga, el dock guarda su sitio para que la píldora no salte.
            Row(Modifier.fillMaxWidth().heightIn(min = DockHeight)) {
                dock.forEachIndexed { position, slot ->
                    val itemModifier = Modifier.weight(1f)
                    val showLabel = prefs.dockLabels
                    when (slot) {
                        DockSlot.Empty ->
                            DockItem(
                                label = stringResource(R.string.dock_empty),
                                description = stringResource(R.string.dock_empty_description),
                                onClick = { editing = position },
                                onLongClick = { editing = position },
                                showLabel = showLabel,
                                modifier = itemModifier
                            ) { Glyph("+", muted = true) }

                        is DockSlot.App ->
                            DockItem(
                                label = slot.app.label,
                                description = null,
                                onClick = { launch(slot.app) },
                                onLongClick = { editing = position },
                                showLabel = showLabel,
                                modifier = itemModifier
                            ) { AppIcon(slot.app.icon, DockIconSize) }

                        is DockSlot.Stack ->
                            DockItem(
                                label = slot.name,
                                description = null,
                                onClick = { if (slot.apps.isEmpty()) editing = position else openStack = position },
                                onLongClick = { editing = position },
                                showLabel = showLabel,
                                modifier = itemModifier
                            ) { StackSlotIcon(slot) }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            // También depende de las apps instaladas: instalar o quitar el asistente cambia el texto.
            val assistant = remember(prefs.assistantPackage, apps) { context.appLabel(prefs.assistantPackage) }
            SearchPill(searchHint(assistant), onOpenSearch)
        }

        val sections = remember(apps) { apps.orEmpty().toSections { it.label } }
        if (!searchOpen) {
            Trace(
                sections = sections,
                railPadding =
                    PaddingValues(
                        top = bars.calculateTopPadding() + 16.dp,
                        bottom = bars.calculateBottomPadding() + BottomAreaHeight
                    ),
                onLaunch = ::launch
            )
        }
    }

    openStack?.let { position ->
        val stack = dock.getOrNull(position) as? DockSlot.Stack
        if (stack == null) {
            openStack = null
        } else {
            StackSheet(
                stack = stack,
                onLaunch = {
                    openStack = null
                    launch(it)
                },
                onEdit = {
                    openStack = null
                    editing = position
                },
                onDismiss = { openStack = null }
            )
        }
    }

    editing?.let { position ->
        val defaultStackName = stringResource(R.string.stack_default_name)
        SlotSheet(
            slot = dock.getOrNull(position) ?: DockSlot.Empty,
            apps = apps.orEmpty(),
            onPickApp = { app ->
                viewModel.setDockApp(position, app)
                editing = null
            },
            onToggleInStack = { viewModel.toggleInStack(position, it) },
            onMakeStack = { viewModel.makeDockStack(position, defaultStackName) },
            onRenameStack = { viewModel.renameDockStack(position, it) },
            onSetStackIcon = { icon, glyph -> viewModel.setStackIcon(position, icon, glyph) },
            onReorderStack = { viewModel.reorderStack(position, it) },
            onClear = {
                viewModel.clearDockSlot(position)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun ClockAndDate(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = rememberNow()
    val time = formatClock(now)
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(now, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        now.format(DateTimeFormatter.ofPattern(pattern, locale)).replaceFirstChar { it.titlecase(locale) }
    }
    val noRipple = remember { MutableInteractionSource() }
    Column(modifier) {
        Text(
            text = time,
            style = AlisioTokens.Clock,
            color = AlisioTokens.Text,
            modifier =
                Modifier.clickable(noRipple, indication = null) {
                    context.openClockApp()
                }
        )
        Text(
            text = date,
            style = MaterialTheme.typography.bodyLarge,
            color = AlisioTokens.Text.copy(alpha = 0.82f),
            modifier =
                Modifier
                    .clickable(noRipple, indication = null) {
                        context.open(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
                    }
                    .heightIn(min = 48.dp)
                    .padding(start = 6.dp, top = 4.dp, bottom = 12.dp, end = 12.dp)
        )
    }
}

/**
 * Abre la app de Reloj. `ACTION_SHOW_ALARMS` exige el permiso `SET_ALARM` (en One UI falla sin él),
 * así que solo se usa para saber qué app es el reloj y se abre por su icono de inicio.
 */
private fun Context.openClockApp() {
    // Con varias apps de reloj y ninguna predeterminada, `resolveActivity` devuelve el selector del
    // sistema (paquete «android»), que no tiene icono de inicio: se toma la primera app real.
    val clockPackage =
        packageManager.queryIntentActivities(Intent(AlarmClock.ACTION_SHOW_ALARMS), 0)
            .map { it.activityInfo.packageName }
            .firstOrNull { it != "android" }
    val intent = clockPackage?.let(packageManager::getLaunchIntentForPackage)
    if (intent == null) Log.w(TAG, "No hay app de reloj que abrir") else open(intent)
}

private fun Context.open(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: RuntimeException) {
        // Sin app de reloj o de calendario que lo atienda: no hay nada que abrir.
        Log.w(TAG, "No se ha podido abrir $intent", e)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockItem(
    label: String,
    description: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Column(
        modifier =
            modifier
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                // Sin etiqueta visible, el nombre sigue llegando a TalkBack.
                .semantics { contentDescription = description ?: label }
                .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(DockIconSize), contentAlignment = Alignment.Center) { icon() }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            DockLabel(label)
        }
    }
}

@Composable
private fun DockLabel(label: String) {
    Text(
        text = label,
        style = AlisioTokens.DockLabel,
        color = AlisioTokens.Text.copy(alpha = 0.9f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 2.dp)
    )
}

/** Icono de dock hecho con un carácter: el hueco vacío («+») y la pila que aún no tiene apps. */
@Composable
private fun Glyph(text: String, muted: Boolean) {
    val shape = AlisioTokens.DockIconShape
    Box(
        Modifier
            .size(DockIconSize)
            .background(AlisioTokens.SurfaceElevated.copy(alpha = if (muted) 0.55f else 1f), shape)
            .border(1.dp, AlisioTokens.ControlBorder, shape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = if (muted) AlisioTokens.TextSecondary else AlisioTokens.Accent
        )
    }
}

/**
 * Icono de pila según su modo: carpeta con sus cuatro primeras apps, el icono de la primera o un
 * símbolo propio. Sin apps (o sin símbolo escrito) cae en la inicial de su nombre.
 */
@Composable
private fun StackSlotIcon(stack: DockSlot.Stack) {
    val initial = stack.name.trim().takeGraphemes(1).uppercase().ifEmpty { "+" }
    when {
        stack.icon == StackIcon.SYMBOL -> Glyph(stack.glyph.ifBlank { initial }, muted = false)

        stack.apps.isEmpty() -> Glyph(initial, muted = false)

        stack.icon == StackIcon.FIRST_APP -> AppIcon(stack.apps.first().icon, DockIconSize)

        else -> {
            val shape = AlisioTokens.DockIconShape
            Column(
                Modifier
                    .size(DockIconSize)
                    .background(AlisioTokens.SurfaceElevated.copy(alpha = 0.9f), shape)
                    .border(1.dp, AlisioTokens.ControlBorder, shape)
                    .padding(5.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                stack.apps.take(STACK_ICON_APPS).chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { AppIcon(it.icon, StackMiniIconSize) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPill(hint: String, onClick: () -> Unit) {
    val shape = AlisioTokens.PillShape
    Box(
        Modifier
            .fillMaxWidth()
            .height(PillHeight)
            .background(AlisioTokens.Surface.copy(alpha = 0.88f), shape)
            .border(1.dp, AlisioTokens.ControlBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyLarge,
            color = AlisioTokens.TextSecondary
        )
    }
}

/** Hoja con las apps de una pila: un toque abre la app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StackSheet(stack: DockSlot.Stack, onLaunch: (AppEntry) -> Unit, onEdit: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AlisioTokens.Surface) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stack.name,
                style = MaterialTheme.typography.headlineLarge,
                color = AlisioTokens.Text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            stack.apps.forEach { app ->
                ItemRow(title = AnnotatedString(app.label), icon = app.icon, onClick = { onLaunch(app) })
            }
            TextButton(onClick = onEdit, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.stack_edit))
            }
        }
    }
}

/**
 * Hoja para configurar un hueco del dock: elegir su app, convertirlo en pila o vaciarlo. Si ya es
 * una pila, cambia su nombre y marca qué apps contiene; en ese caso elegir una app no cierra la hoja.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotSheet(
    slot: DockSlot,
    apps: List<AppEntry>,
    onPickApp: (AppEntry) -> Unit,
    onToggleInStack: (AppEntry) -> Unit,
    onMakeStack: () -> Unit,
    onRenameStack: (String) -> Unit,
    onSetStackIcon: (StackIcon, String) -> Unit,
    onReorderStack: (List<AppEntry>) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val stack = slot as? DockSlot.Stack
    var query by remember { mutableStateOf("") }
    // El nombre se edita en local y se guarda en cada cambio: releerlo de la base movería el cursor.
    var name by remember(stack != null) { mutableStateOf(stack?.name.orEmpty()) }
    var glyph by remember(stack != null) { mutableStateOf(stack?.glyph.orEmpty()) }
    val inStack = remember(stack?.apps) { stack?.apps.orEmpty().map { it.key }.toSet() }
    val filtered =
        remember(apps, query) { apps.filterByName(query) { it.label } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AlisioTokens.Surface
    ) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (stack == null) {
                Text(
                    text = stringResource(R.string.slot_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = AlisioTokens.Text,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            } else {
                PillTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) onRenameStack(it.trim())
                    },
                    placeholder = stringResource(R.string.stack_name_hint),
                    container = AlisioTokens.SurfaceElevated,
                    capitalization = KeyboardCapitalization.Sentences
                )
                // Cada opción se enseña dibujada con las apps de esta pila, no descrita con palabras.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StackIcon.entries.forEach { mode ->
                        StackIconOption(
                            preview = DockSlot.Stack(stack.name, stack.apps, mode, glyph),
                            label =
                                stringResource(
                                    when (mode) {
                                        StackIcon.FOLDER -> R.string.stack_icon_folder
                                        StackIcon.FIRST_APP -> R.string.stack_icon_first
                                        StackIcon.SYMBOL -> R.string.stack_icon_symbol
                                    }
                                ),
                            selected = stack.icon == mode,
                            onClick = { onSetStackIcon(mode, glyph) }
                        )
                    }
                }
                if (stack.icon == StackIcon.SYMBOL) {
                    PillTextField(
                        value = glyph,
                        onValueChange = {
                            // Un símbolo o un emoji: no cabe más en el icono.
                            glyph = it.takeGraphemes(STACK_GLYPH_LENGTH)
                            onSetStackIcon(StackIcon.SYMBOL, glyph)
                        },
                        placeholder = stringResource(R.string.stack_symbol_hint),
                        container = AlisioTokens.SurfaceElevated
                    )
                }
            }
            PillTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.picker_search),
                container = AlisioTokens.SurfaceElevated
            )
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (query.isBlank()) {
                if (stack == null) {
                    item {
                        ItemRow(
                            title = AnnotatedString(stringResource(R.string.slot_make_stack)),
                            leading = "▦",
                            subtitle = stringResource(R.string.slot_make_stack_hint),
                            onClick = onMakeStack
                        )
                    }
                }
                if (slot != DockSlot.Empty) {
                    item {
                        ItemRow(
                            title =
                                AnnotatedString(
                                    stringResource(
                                        if (stack == null) R.string.picker_clear else R.string.slot_remove_stack
                                    )
                                ),
                            leading = "–",
                            onClick = onClear
                        )
                    }
                }
            }
            if (stack != null && query.isBlank() && stack.apps.size > 1) {
                item { SectionLabel(stringResource(R.string.stack_order_title)) }
                item { StackOrder(stack.apps, onReorderStack) }
                item { SectionLabel(stringResource(R.string.stack_all_apps)) }
            }
            items(filtered, key = { it.key.toString() }) { app ->
                val checked = app.key in inStack
                ItemRow(
                    title = AnnotatedString(app.label),
                    icon = app.icon,
                    trailing = if (checked) stringResource(R.string.picker_checked) else null,
                    highlighted = checked,
                    onClick = { if (stack == null) onPickApp(app) else onToggleInStack(app) }
                )
            }
        }
    }
}

/** Una forma de dibujar el icono de la pila, con su vista previa; la elegida lleva el acento. */
@Composable
private fun StackIconOption(preview: DockSlot.Stack, label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = AlisioTokens.RowShape
    Column(
        modifier =
            Modifier
                .clip(shape)
                .then(
                    if (selected) {
                        Modifier.background(AlisioTokens.AccentSoft, shape).border(1.dp, AlisioTokens.Accent, shape)
                    } else {
                        Modifier
                    }
                )
                .clickable(role = Role.RadioButton, onClick = onClick)
                .semantics { this.selected = selected }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StackSlotIcon(preview)
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = AlisioTokens.DockLabel,
            color = if (selected) AlisioTokens.Accent else AlisioTokens.TextSecondary
        )
    }
}

/**
 * Las apps de la pila en su orden, que es el de la hoja y el del icono de carpeta. Se reordenan
 * arrastrando el asa de la derecha; TalkBack ofrece «subir» y «bajar» en el asa.
 *
 * El orden se lleva en local mientras dura el arrastre y se guarda al soltar: escribir en cada
 * cruce de fila haría saltar la lista con cada respuesta de la base de datos.
 */
@Composable
private fun StackOrder(apps: List<AppEntry>, onReorder: (List<AppEntry>) -> Unit) {
    var order by remember(apps) { mutableStateOf(apps) }
    var dragging by remember { mutableStateOf<AppEntry?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { StackOrderRowHeight.toPx() }
    val moveUp = stringResource(R.string.stack_move_up)
    val moveDown = stringResource(R.string.stack_move_down)

    fun move(app: AppEntry, by: Int): Boolean {
        val index = order.indexOf(app)
        val target = index + by
        if (index < 0 || target !in order.indices) return false
        order = order.moved(index, target)
        return true
    }

    Column {
        order.forEach { app ->
            key(app.key.toString()) {
                val isDragging = dragging === app
                val handle = stringResource(R.string.stack_drag, app.label)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(StackOrderRowHeight)
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer { translationY = if (isDragging) offset else 0f }
                            .background(
                                if (isDragging) AlisioTokens.SurfaceElevated else Color.Transparent,
                                AlisioTokens.RowShape
                            )
                            .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(app.icon, 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = AlisioTokens.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = handle
                                customActions =
                                    listOf(
                                        CustomAccessibilityAction(moveUp) {
                                            move(app, -1).also { if (it) onReorder(order) }
                                        },
                                        CustomAccessibilityAction(moveDown) {
                                            move(app, 1).also { if (it) onReorder(order) }
                                        }
                                    )
                            }
                            .pointerInput(app.key) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = app
                                        offset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        offset += amount.y
                                        // Al pasar de media fila, la app cambia de puesto y el arrastre sigue desde ahí.
                                        val step =
                                            when {
                                                offset > rowHeightPx / 2 -> 1
                                                offset < -rowHeightPx / 2 -> -1
                                                else -> 0
                                            }
                                        if (step != 0 && move(app, step)) offset -= step * rowHeightPx
                                    },
                                    onDragEnd = {
                                        dragging = null
                                        offset = 0f
                                        onReorder(order)
                                    },
                                    onDragCancel = {
                                        dragging = null
                                        offset = 0f
                                        order = apps
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("≡", style = MaterialTheme.typography.titleLarge, color = AlisioTokens.TextSecondary)
                    }
                }
            }
        }
    }
}
