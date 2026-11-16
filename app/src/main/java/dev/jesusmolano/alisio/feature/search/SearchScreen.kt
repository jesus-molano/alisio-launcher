package dev.jesusmolano.alisio.feature.search

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.icu.util.Calendar
import android.os.SystemClock
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
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
import dev.jesusmolano.alisio.TouchTrace
import dev.jesusmolano.alisio.core.extras.ClipContent
import dev.jesusmolano.alisio.core.extras.ClockCommand
import dev.jesusmolano.alisio.core.people.Channel
import dev.jesusmolano.alisio.core.people.initials
import dev.jesusmolano.alisio.core.people.personKey
import dev.jesusmolano.alisio.core.settings.SettingsDestination
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.contacts.ContactPhotos
import dev.jesusmolano.alisio.data.contacts.Person
import dev.jesusmolano.alisio.data.settings.Prefs
import dev.jesusmolano.alisio.data.system.PhoneResult
import dev.jesusmolano.alisio.feature.people.ChannelButton
import dev.jesusmolano.alisio.feature.people.PersonCard
import dev.jesusmolano.alisio.feature.people.WHATSAPP_PACKAGE
import dev.jesusmolano.alisio.feature.people.contact
import dev.jesusmolano.alisio.feature.people.openContact
import dev.jesusmolano.alisio.feature.people.rememberContactPhoto
import dev.jesusmolano.alisio.feature.settings.icon
import dev.jesusmolano.alisio.ui.components.Chip
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.LocalUndo
import dev.jesusmolano.alisio.ui.components.PillTextField
import dev.jesusmolano.alisio.ui.components.RoundIconButton
import dev.jesusmolano.alisio.ui.components.SectionLabel
import dev.jesusmolano.alisio.ui.components.formatClock
import dev.jesusmolano.alisio.ui.components.rememberNow
import dev.jesusmolano.alisio.ui.theme.AlisioHaptics
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val CloseDrag = 64.dp

private const val ROW_CHANNELS = 3

// Lo que tarda en empezar a salir el teclado cuando la petición ha llegado (docs/IDEAS.md §3.1).
private const val IME_RETRY_MILLIS = 150L
private const val IME_WAIT_MILLIS = 2_000L

/**
 * Barra de §5.3: resultados apilados desde abajo, junto al teclado, con el mejor pegado a la barra.
 * Enter abre ese resultado; si no hay ninguno, pregunta a Claude (o busca en la web si no está).
 *
 * @param onClose se llama tras abrir algo; quien la muestra decide cómo cerrarla.
 * @param onOpenSettings abre los ajustes de Alisio, que también salen como resultado.
 * @param onAskContacts pide los permisos de contactos y de llamada.
 * @param onOpenZones abre Zonas, que sale como resultado y, con el campo vacío, como botón.
 * @param onAppMenu abre el Interior de una app, al mantenerla pulsada.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onClose: () -> Unit,
    onOpenSettings: (SettingsDestination) -> Unit,
    onOpenZones: () -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onAskContacts: () -> Unit,
    prefs: Prefs,
    modifier: Modifier = Modifier
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    // El texto vive aquí, no en un flujo: si volviera con retraso, el campo perdería letras y el cursor.
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    // Lo que había en el portapapeles al abrir la barra, si tiene acción. Vive solo mientras la barra
    // está abierta: no se guarda ni sobrevive a cerrarla (§5.8).
    var clip by remember { mutableStateOf<ClipContent?>(null) }
    val windowInfo = LocalWindowInfo.current
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
    val undo = LocalUndo.current
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current
    // Nombre del asistente de IA elegido; `null` si no está instalado, y entonces se busca en la web.
    val assistant = remember(prefs.assistantPackage) { context.appLabel(prefs.assistantPackage) }
    val webLabel = stringResource(R.string.search_chip_web)
    val copiedMessage = stringResource(R.string.search_copied)
    val whatsappInstalled = remember { context.appLabel(WHATSAPP_PACKAGE) != null }
    val whatsappWithoutRows = remember(source) { whatsappInstalled && viewModel.noWhatsappRows() }
    val whatsappIcon = remember(source) { source.appIcon(WHATSAPP_PACKAGE) }
    // Las salidas llevan el icono de la app a la que mandan, para reconocerlas sin leer.
    val assistantIcon = remember(source, prefs.assistantPackage) { source.appIcon(prefs.assistantPackage) }
    val playIcon = remember(source) { source.appIcon(PLAY_STORE_PACKAGE) }
    val quickOptions =
        remember(prefs.homeNotes, prefs.phoneCommands) { QuickOptions(prefs.homeNotes, prefs.phoneCommands) }
    val state =
        remember(query, source, whatsappWithoutRows, quickOptions) {
            viewModel.search(query, source, quickOptions) { person, channel ->
                channel in person.channels(whatsappWithoutRows)
            }
        }
    val scope = rememberCoroutineScope()

    // Con un canal delante del nombre («wa laura»), ese canal es el primero: el del botón y el de Enter.
    fun channelsOf(person: Person): List<Channel> {
        val ordered = viewModel.orderedChannels(person, person.channels(whatsappWithoutRows), source)
        val asked = state.channel?.takeIf { it in ordered } ?: return ordered
        return listOf(asked) + (ordered - asked)
    }

    // Apagadas en los ajustes, nadie pide fotos y todos los avatares son iniciales.
    val photos = viewModel.photos.takeIf { prefs.contactPhotos }

    val cardChannels = selected?.let(::channelsOf).orEmpty()
    val card = selected?.takeIf { cardChannels.isNotEmpty() }

    BackHandler(enabled = selected != null) { selectedKey = null }

    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible by rememberUpdatedState(WindowInsets.isImeVisible)
    LaunchedEffect(Unit) {
        viewModel.onOpened()
        val openedAt = SystemClock.uptimeMillis()
        TouchTrace.mark("barra abierta")
        focusRequester.requestFocus()
        // Sin el foco de la ventana el sistema descarta la petición del teclado. Justo al volver al
        // Inicio la barra puede abrirse antes de que la ventana lo recupere: se pide al tenerlo.
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        keyboard?.show()
        // Si el teclado no empieza a salir enseguida, se vuelve a pedir el foco y el teclado, una vez.
        if (withTimeoutOrNull(IME_RETRY_MILLIS) { snapshotFlow { imeVisible }.first { it } } == null) {
            TouchTrace.mark("teclado pedido otra vez")
            focusRequester.requestFocus()
            keyboard?.show()
        }
        // Con un teclado físico no sale ninguno: la espera tiene tope para no quedarse colgada.
        withTimeoutOrNull(IME_WAIT_MILLIS) { snapshotFlow { imeVisible }.first { it } }?.let {
            TouchTrace.mark("teclado visible", took = SystemClock.uptimeMillis() - openedAt)
        }
    }
    // Una sola lectura por apertura, porque Android avisa al usuario en cada una. Espera al foco de la
    // ventana: sin él, Android no deja leer el portapapeles y la lectura se perdería.
    LaunchedEffect(Unit) {
        if (prefs.clipboardActions) {
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
            clip = context.clipboardContent()
        }
    }
    // Al cambiar los resultados la lista vuelve al que está pegado a la barra, que es el que abre Enter;
    // si no, conservaría la posición del elemento que estaba primero antes. Lo mismo cuando llega la
    // acción del portapapeles, que se coloca ahí.
    LaunchedEffect(state, clip) { listState.scrollToItem(0) }

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
            onOpenSettings(target.page)
        }

        is Target.Zones -> {
            viewModel.record(target)
            onOpenZones()
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

    fun runQuick(quick: Quick) {
        when (quick) {
            is Quick.Clock -> {
                val opened = context.setClock(quick.command)
                // El reloj no enseña su pantalla (EXTRA_SKIP_UI): sin este aviso no se sabría si ha valido.
                if (opened) {
                    val done =
                        when (quick.command) {
                            is ClockCommand.Alarm -> R.string.search_alarm_set
                            is ClockCommand.Timer -> R.string.search_timer_set
                        }
                    val message = resources.getString(done, context.clockValue(quick.command))
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                finish(opened, resources.getString(R.string.search_clock_app))
            }

            is Quick.Note -> {
                viewModel.addNote(quick.text)
                // La nota se ve al cerrarse la barra, bajo la hora: el aviso solo confirma que se guardó.
                Toast.makeText(context, resources.getString(R.string.note_saved), Toast.LENGTH_SHORT).show()
                onClose()
            }

            is Quick.Phone -> {
                scope.launch {
                    when (val result = viewModel.runPhone(quick.command)) {
                        // Hecho sin salir: se dice cómo ha quedado, porque la barra se cierra enseguida.
                        is PhoneResult.Done -> {
                            Toast.makeText(
                                context,
                                resources.phoneDone(quick.command, result.on),
                                Toast.LENGTH_SHORT
                            ).show()
                            onClose()
                        }

                        PhoneResult.Opened -> onClose()

                        PhoneResult.Failed ->
                            Toast.makeText(
                                context,
                                resources.getString(R.string.phone_failed, resources.phoneTitle(quick.command)),
                                Toast.LENGTH_SHORT
                            ).show()
                    }
                }
            }
        }
    }

    fun runClip(content: ClipContent) = finish(context.openClip(content), content.text)

    fun onEnter() {
        val calculation = state.calculation
        val quick = state.quick
        val best = state.best
        val copied = clip
        // Enter siempre responde al dedo: «hecho» si hay algo que hacer y «límite» con la barra vacía
        // y sin nada copiado, donde no hace nada.
        if (card == null && state.query.isBlank() && copied == null) {
            AlisioHaptics.limit(view)
        } else {
            AlisioHaptics.done(view)
        }
        when {
            // Enter usa el canal principal, pero nunca marca solo: una llamada se queda en el marcador.
            card != null -> contact(card, cardChannels.first(), direct = false)

            calculation != null -> copyCalculation(calculation)

            quick != null && state.quickFirst -> runQuick(quick)

            // Con la barra vacía lo único destacado es la acción del portapapeles.
            state.query.isBlank() -> if (copied != null) runClip(copied)

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
        if (card != null) {
            // Con muchos canales y la letra grande la tarjeta puede no caber sobre el teclado.
            Box(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState(), reverseScrolling = true),
                contentAlignment = Alignment.BottomCenter
            ) {
                val learned =
                    state.channel == null && viewModel.mainChannel(card, cardChannels.toSet(), source)?.learned == true
                PersonCard(
                    name = card.name,
                    channels = cardChannels,
                    learned = learned,
                    onForget = {
                        val restore = viewModel.forgetChannel(card)
                        undo.show(resources.getString(R.string.undo_channel_forgotten, card.name), restore)
                    },
                    onContact = { contact(card, it) },
                    onBack = { selectedKey = null },
                    onOpenContact = { finish(context.openContact(card), card.name) },
                    photo = rememberContactPhoto(card, photos)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                reverseLayout = true,
                // Arriba, aire bajo la barra de estado: la última fila no se corta en seco contra ella.
                contentPadding =
                    PaddingValues(start = AlisioTokens.Outset, end = AlisioTokens.Outset, top = 16.dp, bottom = 8.dp)
            ) {
                // Con reverseLayout el primer elemento es el más cercano a la barra.
                if (state.query.isBlank()) {
                    clip?.let { content ->
                        item(key = "clipboard") {
                            ItemRow(
                                // Un enlace dice a qué sitio lleva: Enter lo abre sin haberlo escrito.
                                title = AnnotatedString(clipTitle(content)),
                                leadingIcon = content.icon,
                                subtitle = content.text,
                                enterTarget = true,
                                onClickLabel = stringResource(R.string.search_do_open),
                                onClick = { runClip(content) }
                            )
                        }
                    }
                    recents(state, photos, onOpen = ::open, onAppMenu = onAppMenu) { person ->
                        PersonActions(person, ::channelsOf, whatsappIcon, ::contact)
                    }
                    if (!contactsGranted) {
                        item(key = "allow-contacts") {
                            ItemRow(
                                title = AnnotatedString(stringResource(R.string.people_allow)),
                                leadingIcon = R.drawable.ic_contacts,
                                subtitle = stringResource(R.string.people_allow_hint),
                                subtitleLines = 2,
                                onClick = onAskContacts
                            )
                        }
                    }
                } else {
                    results(
                        state,
                        photos,
                        assistant,
                        assistantIcon,
                        onOpen = ::open,
                        onAppMenu = onAppMenu,
                        onCopy = ::copyCalculation,
                        onQuick = ::runQuick,
                        onFallback = ::fallback
                    ) { person -> PersonActions(person, ::channelsOf, whatsappIcon, ::contact) }
                }
            }
        }
        // Las salidas: a dónde mandar lo escrito si no está en el teléfono. Si no caben en una línea
        // pasan a otra; nunca se cortan ni se desplazan.
        FlowRow(
            Modifier.padding(horizontal = AlisioTokens.Outset, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (assistant != null) {
                Chip(assistant, icon = R.drawable.ic_auto_awesome, appIcon = assistantIcon) {
                    finish(context.askAssistant(prefs.assistantPackage, state.query), assistant)
                }
            }
            Chip(stringResource(R.string.search_chip_web), icon = R.drawable.ic_search) {
                finish(context.searchWeb(prefs.webSearchPackage, state.query), webLabel)
            }
            Chip(stringResource(R.string.search_chip_play), icon = R.drawable.ic_open_in_new, appIcon = playIcon) {
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
                    .padding(start = AlisioTokens.Outset, end = AlisioTokens.Outset, bottom = 12.dp)
                    .focusRequester(focusRequester),
            // Zonas y los ajustes de Alisio, siempre a la vista con el campo vacío y junto al pulgar.
            // Al escribir ceden el sitio al botón de borrar: entonces salen como resultados.
            emptyActions = {
                RoundIconButton(
                    R.drawable.ic_zones,
                    stringResource(R.string.zones_title),
                    AlisioTokens.TextSecondary,
                    onOpenZones
                )
                RoundIconButton(
                    R.drawable.ic_settings,
                    stringResource(R.string.settings_title),
                    AlisioTokens.TextSecondary,
                    onClick = { onOpenSettings(SettingsDestination.COVER) }
                )
            }
        )
    }
}

private const val PLAY_STORE_PACKAGE = "com.android.vending"

/** El icono de una app instalada, tal como sale en los resultados; `null` si no está. */
private fun SearchSource.appIcon(packageName: String): Bitmap? = index.firstNotNullOfOrNull { item ->
    (item.payload as? Target.App)?.app?.takeIf { it.component.packageName == packageName }?.icon
}

private fun LazyListScope.recents(
    state: SearchState,
    photos: ContactPhotos?,
    onOpen: (Target) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    personActions: @Composable RowScope.(Person) -> Unit
) {
    items(state.recents.size, key = { state.recents[it].key.toString() }) { index ->
        val app = state.recents[index]
        ItemRow(
            title = AnnotatedString(app.label),
            icon = app.icon,
            onLongClick = { onAppMenu(app) },
            onLongClickLabel = stringResource(R.string.dock_app_menu),
            onClick = { onOpen(Target.App(app)) }
        )
    }
    if (state.recents.isNotEmpty()) item { SectionLabel(stringResource(R.string.search_section_recents)) }
    items(state.recentPeople.size, key = { personKey(state.recentPeople[it].lookupKey) }) { index ->
        val person = state.recentPeople[index]
        ItemRow(
            title = AnnotatedString(person.name),
            icon = rememberContactPhoto(person, photos),
            avatarName = person.name,
            actions = { personActions(person) },
            onClick = { onOpen(Target.Contact(person)) }
        )
    }
    if (state.recentPeople.isNotEmpty()) item { SectionLabel(stringResource(R.string.search_section_people)) }
}

private fun LazyListScope.results(
    state: SearchState,
    photos: ContactPhotos?,
    assistant: String?,
    assistantIcon: Bitmap?,
    onOpen: (Target) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onCopy: (String) -> Unit,
    onQuick: (Quick) -> Unit,
    onFallback: (String) -> Unit,
    personActions: @Composable RowScope.(Person) -> Unit
) {
    val quick = state.quick
    if (quick != null && state.quickFirst) quickRow(quick, onQuick)
    state.calculation?.let { result ->
        item(key = "calc") {
            ItemRow(
                title = AnnotatedString(result),
                leadingIcon = R.drawable.ic_calculate,
                // La cuenta escrita, para comprobar de un vistazo que el resultado es de lo que se quería.
                subtitle = state.query.trim(),
                enterTarget = true,
                numeral = true,
                onClickLabel = stringResource(R.string.search_do_copy),
                onClick = { onCopy(result) }
            )
        }
    }
    if (state.calculation == null && state.sections.isEmpty() && !state.quickFirst) {
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
                // El icono dice a dónde va la pregunta: a la app del asistente o a un buscador.
                icon = assistantIcon.takeIf { assistant != null },
                leadingIcon = if (assistant != null) R.drawable.ic_auto_awesome else R.drawable.ic_search,
                enterTarget = true,
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
                icon = if (target is Target.Contact) rememberContactPhoto(target.person, photos) else target.icon,
                avatarName = (target as? Target.Contact)?.person?.name,
                leadingIcon =
                    when (target) {
                        is Target.Zones -> R.drawable.ic_zones

                        is Target.Setting -> R.drawable.ic_settings

                        // Cada sección con su dibujo, para no confundirla con un ajuste del sistema.
                        is Target.AlisioSettings -> target.page.icon()

                        else -> null
                    },
                subtitle =
                    when (target) {
                        // Oculta: se dice, porque no sale en ningún otro sitio. Renombrada y encontrada por su
                        // nombre de siempre: se dice ese nombre, que es lo que se ha escrito.
                        is Target.App ->
                            when {
                                target.app.hidden -> stringResource(R.string.search_app_hidden)

                                target.app.renamed && result.positions.isEmpty() ->
                                    stringResource(R.string.name_original, target.app.originalLabel)

                                else -> null
                            }

                        is Target.Shortcut -> target.shortcut.app.label

                        // Su grupo ya se llama «Ajustes».
                        is Target.Setting -> null

                        // Una sección dice de qué ajustes es; la portada ya lo lleva en el nombre.
                        is Target.AlisioSettings -> target.section

                        is Target.Zones -> null

                        is Target.Contact -> null
                    },
                enterTarget = best,
                onClickLabel = stringResource(R.string.search_do_open),
                onLongClick = (target as? Target.App)?.let { { onAppMenu(it.app) } },
                onLongClickLabel = stringResource(R.string.dock_app_menu).takeIf { target is Target.App },
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

/** La fila de una alarma o un temporizador. Es un resultado único: no lleva etiqueta de grupo. */
private fun LazyListScope.quickRow(quick: Quick, onQuick: (Quick) -> Unit) {
    item(key = "quick") {
        val context = LocalContext.current
        when (quick) {
            is Quick.Clock -> {
                val alarm = quick.command is ClockCommand.Alarm
                ItemRow(
                    title =
                        AnnotatedString(
                            stringResource(
                                if (alarm) R.string.search_alarm_title else R.string.search_timer_title,
                                context.clockValue(quick.command)
                            )
                        ),
                    leadingIcon = if (alarm) R.drawable.ic_alarm else R.drawable.ic_timer,
                    enterTarget = true,
                    onClick = { onQuick(quick) }
                )
            }

            is Quick.Note ->
                ItemRow(
                    title = AnnotatedString(quick.text),
                    leadingIcon = R.drawable.ic_note,
                    // Lo que hará, porque el título es la nota misma.
                    subtitle = stringResource(R.string.note_add),
                    enterTarget = true,
                    onClick = { onQuick(quick) }
                )

            is Quick.Phone -> {
                val resources = LocalResources.current
                ItemRow(
                    title = AnnotatedString(resources.phoneTitle(quick.command)),
                    leadingIcon = phoneIcon(quick.command),
                    subtitle = resources.phoneHint(quick.command),
                    enterTarget = true,
                    onClick = { onQuick(quick) }
                )
            }
        }
    }
}

/**
 * La hora de una alarma o la duración de un temporizador («1 h 30 min»), para enseñarla. La hora va
 * como en el reloj del sistema, en 12 o 24 h: «19:00» o «7:00 p. m.». En 12 h lleva la marca de
 * mañana o tarde, porque de ella depende cuándo suena.
 */
private fun Context.clockValue(command: ClockCommand): String = when (command) {
    is ClockCommand.Alarm -> {
        val time =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, command.hour)
                set(Calendar.MINUTE, command.minute)
            }
        DateFormat.getTimeFormat(this).format(time.time)
    }

    is ClockCommand.Timer -> {
        val hours = command.seconds / 3600
        val minutes = command.seconds % 3600 / 60
        val seconds = command.seconds % 60
        listOfNotNull(
            hours.takeIf { it > 0 }?.let { getString(R.string.duration_hours, it) },
            minutes.takeIf { it > 0 }?.let { getString(R.string.duration_minutes, it) },
            seconds.takeIf { it > 0 }?.let { getString(R.string.duration_seconds, it) }
        ).joinToString(" ")
    }
}

/** Lo que hará la fila del portapapeles. Un enlace nombra su sitio, para verlo antes de abrirlo. */
@Composable
private fun clipTitle(content: ClipContent): String = when (content) {
    is ClipContent.Url -> stringResource(R.string.search_clip_url, content.host)
    is ClipContent.Phone -> stringResource(R.string.search_clip_phone)
    is ClipContent.Address -> stringResource(R.string.search_clip_address)
}

private val ClipContent.icon: Int
    @DrawableRes get() =
        when (this) {
            is ClipContent.Url -> R.drawable.ic_link
            is ClipContent.Phone -> R.drawable.ic_channel_call
            is ClipContent.Address -> R.drawable.ic_place
        }

private val Target.title: String
    get() =
        when (this) {
            is Target.App -> app.label
            is Target.Shortcut -> shortcut.label
            is Target.Setting -> label
            is Target.AlisioSettings -> label
            is Target.Zones -> label
            is Target.Contact -> person.name
        }

private val Target.icon
    get() =
        when (this) {
            is Target.App -> app.icon
            is Target.Shortcut -> shortcut.app.icon
            is Target.Setting -> null
            is Target.AlisioSettings -> null
            is Target.Zones -> null
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
            personName = person.name,
            onClick = { onContact(person, channel, true) },
            whatsappIcon = whatsappIcon,
            main = index == 0
        )
    }
}
