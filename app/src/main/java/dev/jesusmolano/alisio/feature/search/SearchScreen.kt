package dev.jesusmolano.alisio.feature.search

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.people.Channel
import dev.jesusmolano.alisio.core.people.initials
import dev.jesusmolano.alisio.core.people.personKey
import dev.jesusmolano.alisio.data.contacts.Person
import dev.jesusmolano.alisio.data.settings.Prefs
import dev.jesusmolano.alisio.feature.people.ChannelButton
import dev.jesusmolano.alisio.feature.people.PersonCard
import dev.jesusmolano.alisio.feature.people.WHATSAPP_PACKAGE
import dev.jesusmolano.alisio.feature.people.contact
import dev.jesusmolano.alisio.ui.components.Chip
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.PillTextField
import dev.jesusmolano.alisio.ui.components.SectionLabel
import dev.jesusmolano.alisio.ui.components.formatClock
import dev.jesusmolano.alisio.ui.components.rememberNow
import dev.jesusmolano.alisio.ui.theme.AlisioTokens

private val ScreenMargin = 16.dp
private val CloseDrag = 64.dp

private const val ROW_CHANNELS = 3

/**
 * Barra de §5.3: resultados apilados desde abajo, junto al teclado, con el mejor pegado a la barra.
 * Enter abre ese resultado; si no hay ninguno, pregunta a Claude (o busca en la web si no está).
 *
 * @param onClose se llama tras abrir algo; quien la muestra decide cómo cerrarla.
 * @param onOpenSettings abre los ajustes de Alisio, que también salen como resultado.
 * @param onAskContacts pide los permisos de contactos y de llamada.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onAskContacts: () -> Unit,
    prefs: Prefs,
    modifier: Modifier = Modifier
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    // El texto vive aquí, no en un flujo: si volviera con retraso, el campo perdería letras y el cursor.
    var query by remember { mutableStateOf("") }
    val state = remember(query, source) { viewModel.search(query, source) }
    val listState = rememberLazyListState()
    // Persona elegida: su tarjeta sustituye a los resultados hasta que se contacta o se vuelve atrás.
    // Se guarda su clave y no el objeto: si la agenda cambia con la tarjeta abierta, la tarjeta usa
    // el teléfono y las filas de ahora, o se cierra si el contacto ya no existe.
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selected =
        remember(selectedKey, source) {
            source.index.firstNotNullOfOrNull { item ->
                (item.payload as? Target.Contact)?.person?.takeIf { item.key == selectedKey }
            }
        }
    // Si el contacto elegido desaparece de la agenda, se olvida: no debe reaparecer su tarjeta sola
    // si una lectura posterior lo trae de vuelta.
    LaunchedEffect(selectedKey, selected) {
        if (selectedKey != null && selected == null) selectedKey = null
    }
    val contactsGranted by viewModel.contactsGranted.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val focusRequester = remember { FocusRequester() }
    // Nombre del asistente de IA elegido; `null` si no está instalado, y entonces se busca en la web.
    val assistant = remember(prefs.assistantPackage) { context.appLabel(prefs.assistantPackage) }
    val webLabel = stringResource(R.string.search_chip_web)
    val copiedMessage = stringResource(R.string.search_copied)
    val whatsappInstalled = remember { context.appLabel(WHATSAPP_PACKAGE) != null }
    val whatsappWithoutRows = remember(source) { whatsappInstalled && viewModel.noWhatsappRows() }
    val whatsappIcon =
        remember(source) {
            source.index.firstNotNullOfOrNull { item ->
                (item.payload as? Target.App)?.app?.takeIf { it.component.packageName == WHATSAPP_PACKAGE }?.icon
            }
        }

    fun channelsOf(person: Person): List<Channel> =
        viewModel.orderedChannels(person, person.channels(whatsappWithoutRows), source)

    val cardChannels = selected?.let(::channelsOf).orEmpty()
    val card = selected?.takeIf { cardChannels.isNotEmpty() }

    BackHandler(enabled = selected != null) { selectedKey = null }

    LaunchedEffect(Unit) {
        viewModel.onOpened()
        focusRequester.requestFocus()
    }
    // Al cambiar los resultados la lista vuelve al que está pegado a la barra, que es el que abre Enter;
    // si no, conservaría la posición del elemento que estaba primero antes.
    LaunchedEffect(state) { listState.scrollToItem(0) }

    fun finish(opened: Boolean, failureLabel: String) {
        if (opened) {
            onClose()
        } else {
            Toast.makeText(context, resources.getString(R.string.app_launch_failed, failureLabel), Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun open(target: Target) = when (target) {
        is Target.App -> finish(viewModel.launch(target), target.app.label)

        is Target.Shortcut -> finish(viewModel.launch(target), target.shortcut.label)

        is Target.Setting -> {
            val opened = context.openSetting(target.setting.action)
            if (opened) viewModel.record(target)
            finish(opened, target.label)
        }

        is Target.AlisioSettings -> {
            viewModel.record(target)
            onOpenSettings()
        }

        is Target.Contact -> {
            selectedKey = personKey(target.person.lookupKey)
        }
    }

    /** @param direct `false` cuando no se ha tocado el botón del canal: una llamada pasa por el marcador. */
    fun contact(person: Person, channel: Channel, direct: Boolean = true) {
        val opened = context.contact(person, channel, direct)
        if (opened) viewModel.recordContact(person, channel)
        finish(opened, person.name)
    }

    fun fallback(query: String) {
        if (assistant != null) {
            finish(context.askAssistant(prefs.assistantPackage, query), assistant)
        } else {
            finish(context.searchWeb(prefs.webSearchPackage, query), webLabel)
        }
    }

    fun copyCalculation(result: String) {
        context.copyToClipboard(result)
        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        onClose()
    }

    fun onEnter() {
        val calculation = state.calculation
        val best = state.best
        when {
            // Enter usa el canal principal, pero nunca marca solo: una llamada se queda en el marcador.
            card != null -> contact(card, cardChannels.first(), direct = false)

            calculation != null -> copyCalculation(calculation)

            best != null && best.target is Target.Contact -> {
                val person = best.target.person
                val main = channelsOf(person).firstOrNull()
                if (main != null) contact(person, main, direct = false) else open(best.target)
            }

            best != null -> open(best.target)

            state.query.isNotBlank() -> fallback(state.query)
        }
    }

    val closeDragPx = with(LocalDensity.current) { CloseDrag.toPx() }
    // Deslizar hacia abajo cierra la barra, igual que deslizar hacia arriba la abre. Sobre la lista
    // solo cuenta lo que ella ya no puede desplazar; el gesto termina en cada fling.
    val closeOnPullDown =
        remember(onClose, closeDragPx) {
            object : NestedScrollConnection {
                var pulled = 0f

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput && available.y > 0) {
                        pulled += available.y
                        if (pulled > closeDragPx) {
                            pulled = 0f
                            onClose()
                        }
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    pulled = 0f
                    return Velocity.Zero
                }
            }
        }

    Column(
        modifier
            .fillMaxSize()
            .background(AlisioTokens.Background)
            .nestedScroll(closeOnPullDown)
            // Fuera de la lista. De paso, la barra se queda los toques: no llegan al Inicio de debajo.
            .pointerInput(onClose) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { _, amount -> dragged += amount },
                    onDragEnd = { if (dragged > closeDragPx) onClose() }
                )
            }
            .systemBarsPadding()
            .imePadding()
    ) {
        Text(
            text = formatClock(rememberNow()),
            style = MaterialTheme.typography.headlineLarge,
            color = AlisioTokens.TextTertiary.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 24.dp, top = 16.dp)
        )
        if (card != null) {
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = ScreenMargin, vertical = 8.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                PersonCard(
                    name = card.name,
                    channels = cardChannels,
                    learned = viewModel.mainChannel(card, cardChannels.toSet(), source)?.learned == true,
                    whatsappIcon = whatsappIcon,
                    onContact = { contact(card, it) }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = ScreenMargin, vertical = 8.dp)
            ) {
                // Con reverseLayout el primer elemento es el más cercano a la barra.
                if (state.query.isBlank()) {
                    recents(state, onOpen = ::open) { person ->
                        PersonActions(person, ::channelsOf, whatsappIcon, ::contact)
                    }
                    if (!contactsGranted) {
                        item(key = "allow-contacts") {
                            ItemRow(
                                title = AnnotatedString(stringResource(R.string.people_allow)),
                                leading = "☺",
                                subtitle = stringResource(R.string.people_allow_hint),
                                onClick = onAskContacts
                            )
                        }
                    }
                } else {
                    results(
                        state,
                        assistant,
                        onOpen = ::open,
                        onCopy = ::copyCalculation,
                        onFallback = ::fallback
                    ) { person -> PersonActions(person, ::channelsOf, whatsappIcon, ::contact) }
                }
            }
        }
        Row(
            Modifier.padding(horizontal = ScreenMargin, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (assistant != null) {
                Chip(assistant) {
                    finish(context.askAssistant(prefs.assistantPackage, state.query), assistant)
                }
            }
            Chip(stringResource(R.string.search_chip_web)) {
                finish(context.searchWeb(prefs.webSearchPackage, state.query), webLabel)
            }
            Chip(stringResource(R.string.search_chip_play)) {
                finish(context.searchPlayStore(state.query), resources.getString(R.string.search_chip_play))
            }
        }
        PillTextField(
            value = state.query,
            onValueChange = {
                query = it
                selectedKey = null
            },
            placeholder = searchHint(assistant),
            onEnter = ::onEnter,
            modifier =
                Modifier
                    .padding(start = ScreenMargin, end = ScreenMargin, bottom = 12.dp)
                    .focusRequester(focusRequester)
        )
    }
}

private fun LazyListScope.recents(
    state: SearchState,
    onOpen: (Target) -> Unit,
    personActions: @Composable RowScope.(Person) -> Unit
) {
    items(state.recents.size, key = { state.recents[it].key.toString() }) { index ->
        val app = state.recents[index]
        ItemRow(title = AnnotatedString(app.label), icon = app.icon, onClick = { onOpen(Target.App(app)) })
    }
    if (state.recents.isNotEmpty()) item { SectionLabel(stringResource(R.string.search_section_recents)) }
    items(state.recentPeople.size, key = { personKey(state.recentPeople[it].lookupKey) }) { index ->
        val person = state.recentPeople[index]
        ItemRow(
            title = AnnotatedString(person.name),
            leading = initials(person.name),
            actions = { personActions(person) },
            onClick = { onOpen(Target.Contact(person)) }
        )
    }
    if (state.recentPeople.isNotEmpty()) item { SectionLabel(stringResource(R.string.search_section_people)) }
}

private fun LazyListScope.results(
    state: SearchState,
    assistant: String?,
    onOpen: (Target) -> Unit,
    onCopy: (String) -> Unit,
    onFallback: (String) -> Unit,
    personActions: @Composable RowScope.(Person) -> Unit
) {
    state.calculation?.let { result ->
        item(key = "calc") {
            ItemRow(
                title = AnnotatedString(result),
                leading = "=",
                subtitle = stringResource(R.string.search_calc_hint),
                trailing = stringResource(R.string.search_enter),
                highlighted = true,
                onClick = { onCopy(result) }
            )
        }
        item { SectionLabel(stringResource(R.string.search_section_calc)) }
    }
    if (state.calculation == null && state.sections.isEmpty()) {
        item(key = "fallback") {
            val query = state.query.trim()
            val title =
                if (assistant != null) {
                    stringResource(R.string.search_ask_assistant, assistant, query)
                } else {
                    stringResource(R.string.search_ask_web, query)
                }
            ItemRow(
                title = AnnotatedString(title),
                leading = "?",
                trailing = stringResource(R.string.search_enter),
                highlighted = true,
                onClick = { onFallback(state.query) }
            )
        }
    }
    state.sections.forEach { (section, results) ->
        items(results.size, key = { "${section.name}:$it" }) { index ->
            val result = results[index]
            val best = result === state.best
            val target = result.target
            ItemRow(
                title = highlight(target.title, result.positions),
                icon = target.icon,
                leading = if (target is Target.Contact) initials(target.person.name) else "⚙",
                subtitle =
                    when (target) {
                        is Target.App -> null
                        is Target.Shortcut -> target.shortcut.app.label
                        is Target.Setting -> stringResource(R.string.search_setting_subtitle)
                        is Target.AlisioSettings -> null
                        is Target.Contact -> null
                    },
                // En una persona, lo que hace Enter lo dice el canal marcado, no la palabra «Enter».
                trailing = if (best && target !is Target.Contact) stringResource(R.string.search_enter) else null,
                highlighted = best,
                actions = (target as? Target.Contact)?.let { contact -> { personActions(contact.person) } },
                onClick = { onOpen(target) }
            )
        }
        item {
            SectionLabel(
                stringResource(
                    when (section) {
                        Section.PEOPLE -> R.string.search_section_people
                        Section.APPS -> R.string.search_section_apps
                        Section.SHORTCUTS -> R.string.search_section_shortcuts
                        Section.SETTINGS -> R.string.search_section_settings
                    }
                )
            )
        }
    }
}

private val Target.title: String
    get() =
        when (this) {
            is Target.App -> app.label
            is Target.Shortcut -> shortcut.label
            is Target.Setting -> label
            is Target.AlisioSettings -> label
            is Target.Contact -> person.name
        }

private val Target.icon
    get() =
        when (this) {
            is Target.App -> app.icon
            is Target.Shortcut -> shortcut.app.icon
            is Target.Setting -> null
            is Target.AlisioSettings -> null
            is Target.Contact -> null
        }

/** Pinta en acento las letras del título que coinciden con lo escrito. */
private fun highlight(title: String, positions: IntArray): AnnotatedString = buildAnnotatedString {
    append(title)
    positions.forEach { position ->
        // Las posiciones son del nombre normalizado; solo valen si el título tiene esa letra.
        if (position < title.length) addStyle(SpanStyle(color = AlisioTokens.Accent), position, position + 1)
    }
}

/** Texto de ayuda de la barra: nombra al asistente elegido si está instalado. */
@Composable
internal fun searchHint(assistant: String?): String = if (assistant != null) {
    stringResource(R.string.search_hint, assistant)
} else {
    stringResource(R.string.search_hint_plain)
}

/**
 * Los primeros canales de una persona como botones dentro de su fila: se contacta con un toque, sin
 * abrir la tarjeta. El primero es el principal; el resto de canales está en la tarjeta.
 */
@Composable
private fun PersonActions(
    person: Person,
    channelsOf: (Person) -> List<Channel>,
    whatsappIcon: Bitmap?,
    onContact: (Person, Channel, Boolean) -> Unit
) {
    channelsOf(person).take(ROW_CHANNELS).forEachIndexed { index, channel ->
        ChannelButton(
            channel = channel,
            onClick = { onContact(person, channel, true) },
            whatsappIcon = whatsappIcon,
            main = index == 0
        )
    }
}
