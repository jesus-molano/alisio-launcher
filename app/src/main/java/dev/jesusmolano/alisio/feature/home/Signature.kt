package dev.jesusmolano.alisio.feature.home

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.dock.DOCK_ITEM_SPARE_DP
import dev.jesusmolano.alisio.core.media.timerRemaining
import dev.jesusmolano.alisio.core.media.trackProgress
import dev.jesusmolano.alisio.core.notes.VISIBLE_NOTES
import dev.jesusmolano.alisio.core.notes.noteDisplay
import dev.jesusmolano.alisio.core.notes.noteLink
import dev.jesusmolano.alisio.core.notes.visibleNotes
import dev.jesusmolano.alisio.core.people.Channel
import dev.jesusmolano.alisio.core.people.personKey
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.contacts.ContactPhotos
import dev.jesusmolano.alisio.data.contacts.Person
import dev.jesusmolano.alisio.data.db.HomeNote
import dev.jesusmolano.alisio.data.media.NowPlaying
import dev.jesusmolano.alisio.feature.people.channelGlyph
import dev.jesusmolano.alisio.feature.people.channelLabel
import dev.jesusmolano.alisio.feature.people.rememberContactPhoto
import dev.jesusmolano.alisio.ui.components.AlisioSheet
import dev.jesusmolano.alisio.ui.components.AppIcon
import dev.jesusmolano.alisio.ui.components.Avatar
import dev.jesusmolano.alisio.ui.components.CalendarLiveIcon
import dev.jesusmolano.alisio.ui.components.ClockLiveIcon
import dev.jesusmolano.alisio.ui.components.DockIcon
import dev.jesusmolano.alisio.ui.components.ItemRow
import dev.jesusmolano.alisio.ui.components.LiveLook
import dev.jesusmolano.alisio.ui.components.ProgressOutline
import dev.jesusmolano.alisio.ui.components.SheetTitle
import dev.jesusmolano.alisio.ui.components.rememberNow
import dev.jesusmolano.alisio.ui.components.rememberTicker
import dev.jesusmolano.alisio.ui.theme.AlisioMotion
import dev.jesusmolano.alisio.ui.theme.AlisioTokens
import dev.jesusmolano.alisio.ui.theme.onWallpaper
import java.time.format.TextStyle as DateTextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TAG = "Signature"

// Lo que tarda en irse una nota tachada: lo justo para ver la raya antes de que desaparezca.
private const val STRIKE_MILLIS = 260L

// Con todas las notas abiertas, cuántas caben antes de que la lista compita con la hora.
private const val EXPANDED_NOTES = 6

// La elección de una inicial se va sola si no se toca nada; sin nada que elegir, antes.
private const val CHOOSER_MILLIS = 6_000L
private const val NOTHING_MILLIS = 1_800L

// El anillo de lo que suena avanza cada segundo; el del temporizador, cada diez.
private const val PROGRESS_TICK_MILLIS = 1_000L
private const val TIMER_TICK_MILLIS = 10_000L

private val NoteIconGap = 6.dp
private val FavoriteSize = 44.dp
private val ChooserLetterGap = 16.dp

/** El borde de la fila de caras: el mismo que el del dock, para que la primera caiga en el filo. */
private val FavoritesInset = AlisioTokens.Edge - (DockItemWidth - AlisioTokens.DockIconSize) / 2

/**
 * Las notas del Inicio bajo la hora (bloque «Seña propia»): las dos más recientes y, si hay más,
 * «+N», que las abre todas. Un toque tacha una nota (con deshacer); si lleva un enlace, el toque lo
 * abre y mantenerla la tacha.
 *
 * @param interactive en la miniatura de los ajustes solo se dibuja.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NotesLine(
    notes: List<HomeNote>,
    onStrike: (HomeNote) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var striking by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(striking) {
        val id = striking ?: return@LaunchedEffect
        delay(STRIKE_MILLIS)
        notes.firstOrNull { it.id == id }?.let(onStrike)
        striking = null
    }
    val visible = visibleNotes(notes, if (expanded) EXPANDED_NOTES else VISIBLE_NOTES)
    val style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium).onWallpaper()
    val strikeLabel = stringResource(R.string.note_strike)
    val openLabel = stringResource(R.string.note_open_link)
    val noRipple = remember { MutableInteractionSource() }
    Column(modifier) {
        visible.shown.forEach { note ->
            val link = remember(note.text) { noteLink(note.text) }
            val text = remember(note.text) { noteDisplay(note.text) }
            val struck = striking == note.id
            Row(
                Modifier
                    .heightIn(min = AlisioTokens.MinTouch)
                    .then(
                        if (!interactive) {
                            Modifier
                        } else if (link == null) {
                            Modifier.clickable(noRipple, null, onClickLabel = strikeLabel, role = Role.Button) {
                                striking = note.id
                            }
                        } else {
                            Modifier
                                .combinedClickable(
                                    interactionSource = noRipple,
                                    indication = null,
                                    onClickLabel = openLabel,
                                    onClick = { onOpenLink(link) },
                                    onLongClickLabel = strikeLabel,
                                    onLongClick = { striking = note.id }
                                )
                                .semantics {
                                    customActions = listOf(
                                        CustomAccessibilityAction(strikeLabel) {
                                            striking = note.id
                                            true
                                        }
                                    )
                                }
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(if (link == null) R.drawable.ic_note else R.drawable.ic_link),
                    contentDescription = null,
                    tint = AlisioTokens.Text,
                    modifier = Modifier.size(AlisioTokens.GlanceIconSize)
                )
                Spacer(Modifier.width(NoteIconGap))
                Text(
                    text = text,
                    style = style.copy(textDecoration = if (struck) TextDecoration.LineThrough else null),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (visible.more > 0) {
            Text(
                text = stringResource(R.string.note_more, visible.more),
                style = style,
                modifier =
                    Modifier
                        .heightIn(min = AlisioTokens.MinTouch)
                        .then(
                            if (interactive) {
                                Modifier.clickable(
                                    noRipple,
                                    null,
                                    onClickLabel = stringResource(R.string.note_show_all),
                                    role = Role.Button
                                ) { expanded = true }
                            } else {
                                Modifier
                            }
                        )
                        .padding(top = 12.dp)
            )
        }
    }
}

/** Abre el enlace de una nota en la app que lo atienda (el navegador, casi siempre). */
internal fun Context.openNoteLink(link: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, link.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: RuntimeException) {
    Log.w(TAG, "No se ha podido abrir el enlace de una nota", e)
    false
}

/**
 * Las caras de las personas favoritas, con su nombre de pila debajo. Salen en la barra con el campo
 * vacío (SearchScreen), que decide qué hacen un toque ([onOpen]) y mantener ([onChannels]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FavoritesRow(
    people: List<Person>,
    photos: ContactPhotos?,
    showLabels: Boolean,
    onOpen: (Person) -> Unit,
    onChannels: (Person) -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true
) {
    val openLabel = stringResource(R.string.favorite_open)
    val channelsLabel = stringResource(R.string.favorite_channels)
    Row(modifier.fillMaxWidth().padding(start = FavoritesInset), horizontalArrangement = Arrangement.Start) {
        people.forEach { person ->
            val photo = rememberContactPhoto(person, photos)
            DockIcon(
                label = person.name.substringBefore(' '),
                showLabel = showLabels,
                size = FavoriteSize,
                onWallpaper = true,
                modifier =
                    Modifier
                        .width(FavoriteSize + DOCK_ITEM_SPARE_DP.dp)
                        .then(
                            if (interactive) {
                                Modifier.combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClickLabel = openLabel,
                                    onClick = { onOpen(person) },
                                    onLongClickLabel = channelsLabel,
                                    onLongClick = { onChannels(person) }
                                )
                            } else {
                                Modifier
                            }
                        )
                        .semantics { contentDescription = person.name }
                        .padding(vertical = 4.dp)
            ) { Avatar(person.name, FavoriteSize, photo = photo, onWallpaper = true) }
        }
    }
}

/** Las personas favoritas tal como están guardadas, con las que ya no están en la agenda fuera. */
internal fun favoritePeople(keys: List<String>, people: List<Person>): List<Person> {
    if (keys.isEmpty()) return emptyList()
    val byKey = people.associateBy { personKey(it.lookupKey) }
    return keys.mapNotNull(byKey::get)
}

/** Los canales de una persona favorita, en una hoja: el principal primero. */
@Composable
internal fun FavoriteChannelsSheet(
    person: Person,
    channels: List<Channel>,
    onContact: (Channel) -> Unit,
    onOpenContact: () -> Unit,
    onDismiss: () -> Unit
) {
    AlisioSheet(onDismiss = onDismiss) {
        SheetTitle(person.name)
        // Las filas llevan su margen, como en las demás hojas: el dibujo cae en el filo.
        Column(Modifier.padding(horizontal = AlisioTokens.Outset)) {
            channels.forEach { channel ->
                ItemRow(
                    title = AnnotatedString(stringResource(channelLabel(channel))),
                    leadingIcon = channelGlyph(channel),
                    onClick = { onContact(channel) }
                )
            }
            ItemRow(
                title = AnnotatedString(stringResource(R.string.person_open_contact)),
                leadingIcon = R.drawable.ic_person,
                onClick = onOpenContact
            )
        }
        Spacer(Modifier.heightIn(min = 16.dp))
    }
}

/** Qué apps del dock pueden enseñar un dato vivo: las de calendario y las de reloj. */
internal class LiveApps(val calendar: Set<String>, val clock: Set<String>) {
    companion object {
        val NONE = LiveApps(emptySet(), emptySet())
    }
}

/** Las apps de calendario y de reloj instaladas; se buscan fuera del hilo principal al cambiar las apps. */
@Composable
internal fun rememberLiveApps(apps: List<AppEntry>?, enabled: Boolean): LiveApps {
    val context = LocalContext.current
    val found by produceState(LiveApps.NONE, apps, enabled) {
        value = if (!enabled) LiveApps.NONE else withContext(Dispatchers.IO) { context.liveApps() }
    }
    return found
}

private fun Context.liveApps(): LiveApps = try {
    val packageManager = packageManager
    val calendar =
        packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR),
            0
        ).mapTo(HashSet()) { it.activityInfo.packageName }
    val clock =
        packageManager.queryIntentActivities(Intent(AlarmClock.ACTION_SHOW_ALARMS), 0)
            .mapTo(HashSet()) { it.activityInfo.packageName }
    // El selector del sistema no es una app que esté en el dock.
    LiveApps(calendar - "android", clock - "android")
} catch (e: RuntimeException) {
    Log.w(TAG, "No se han podido buscar las apps de calendario y reloj", e)
    LiveApps.NONE
}

/**
 * El icono de una app del dock con su dato vivo, si lo tiene: el calendario con el día de hoy, el
 * reloj con la hora (y lo que le queda al temporizador, si se sabe por su notificación) y la app
 * que suena con un anillo de progreso. Un icono elegido a mano se respeta: lo eligió el dueño.
 */
@Composable
internal fun LiveAppIcon(
    app: AppEntry,
    size: Dp,
    live: LiveApps,
    look: LiveLook,
    player: NowPlaying?,
    timers: Map<String, Long>
) {
    val packageName = app.component.packageName
    when {
        app.customIcon != null -> AppIcon(app.icon, size)

        packageName in live.calendar -> {
            val now = rememberNow()
            val locale = LocalConfiguration.current.locales[0]
            val weekday =
                remember(now.dayOfWeek, locale) {
                    now.dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale).trimEnd('.').replaceFirstChar {
                        it.titlecase(locale)
                    }
                }
            CalendarLiveIcon(now.dayOfMonth, weekday, size, look)
        }

        packageName in live.clock -> {
            val now = rememberNow()
            val endsAt = timers[packageName]
            // El reloj de pared cuenta en milisegundos de la época; el pulso solo marca cuándo repintar.
            rememberTicker(TIMER_TICK_MILLIS, active = endsAt != null)
            val left = endsAt?.let { timerRemaining(it, System.currentTimeMillis()) }
            ClockLiveIcon(now.hour, now.minute, size, look, timer = left)
        }

        player != null && player.packageName == packageName && player.durationMillis != null -> {
            val tick = rememberTicker(PROGRESS_TICK_MILLIS, active = player.playing)
            val progress =
                trackProgress(
                    position = player.positionMillis,
                    positionAt = player.positionAt,
                    speed = player.speed,
                    duration = player.durationMillis,
                    playing = player.playing,
                    now = tick
                )
            Box(Modifier.size(size)) {
                AppIcon(app.icon, size)
                if (progress != null) ProgressOutline(progress, size)
            }
        }

        else -> AppIcon(app.icon, size)
    }
}

/** Lo que se puede abrir dibujando su inicial: una app o una persona. */
internal sealed interface InitialItem {
    val key: String
    val label: String

    class App(val app: AppEntry) : InitialItem {
        override val key: String = app.key.toString()
        override val label: String = app.label
    }

    class Contact(val person: Person) : InitialItem {
        override val key: String = personKey(person.lookupKey)
        override val label: String = person.name
    }
}

/** La letra dibujada y lo que hay para elegir con ella; vacío si nada empieza así. */
internal class InitialChoice(val letter: Char, val items: List<InitialItem>, val at: Offset)

/**
 * La elección tras dibujar una inicial: la letra reconocida, grande, y las dos o tres apps o
 * personas que empatan, cerca de donde se dibujó. Sin nada que elegir dice que no hay nada con esa
 * letra. Se va sola, o al tocar fuera.
 */
@Composable
internal fun InitialChooser(
    choice: InitialChoice?,
    photos: ContactPhotos?,
    onPick: (InitialItem) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(choice) {
        val shown = choice ?: return@LaunchedEffect
        delay(if (shown.items.isEmpty()) NOTHING_MILLIS else CHOOSER_MILLIS)
        onDismiss()
    }
    // La última elección se guarda para que su salida tenga qué pintar.
    var last by remember { mutableStateOf(choice) }
    if (choice != null) last = choice
    val density = LocalDensity.current
    val rise = with(density) { AlisioMotion.Rise.roundToPx() }
    AnimatedVisibility(
        visible = choice != null,
        enter = fadeIn(AlisioMotion.fast()) + slideInVertically(AlisioMotion.standard()) { rise },
        exit = fadeOut(AlisioMotion.exit())
    ) {
        val shown = last ?: return@AnimatedVisibility
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
            Row(
                Modifier
                    .offset { IntOffset(0, (shown.at.y - with(density) { 60.dp.toPx() }).toInt().coerceAtLeast(0)) }
                    .padding(horizontal = AlisioTokens.Outset)
                    .background(AlisioTokens.Plate, AlisioTokens.CardShape)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .widthIn(min = AlisioTokens.MinTouch),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = shown.letter.toString(),
                    style = AlisioTokens.Numeral,
                    color = AlisioTokens.Text,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.width(ChooserLetterGap))
                if (shown.items.isEmpty()) {
                    Text(
                        text = stringResource(R.string.draw_nothing, shown.letter.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlisioTokens.TextSecondary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                } else {
                    shown.items.forEach { item -> ChooserItem(item, photos) { onPick(item) } }
                }
            }
        }
    }
}

@Composable
private fun ChooserItem(item: InitialItem, photos: ContactPhotos?, onClick: () -> Unit) {
    val size = AlisioTokens.DockIconSize
    DockIcon(
        label = item.label,
        showLabel = true,
        size = size,
        modifier =
            Modifier
                .width(DockItemWidth)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.search_do_open),
                    onClick = onClick
                )
                .semantics { contentDescription = item.label }
                .padding(vertical = 6.dp)
    ) {
        when (item) {
            is InitialItem.App -> AppIcon(item.app.icon, size)
            is InitialItem.Contact -> Avatar(item.person.name, size, photo = rememberContactPhoto(item.person, photos))
        }
    }
}

/** Mínimo de lado de la caja del trazo para que cuente como letra: menos es un toque que se movió. */
internal val MinLetterSize = 72.dp
