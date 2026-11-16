package dev.jesusmolano.alisio.feature.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jesusmolano.alisio.AppContainer
import dev.jesusmolano.alisio.R
import dev.jesusmolano.alisio.core.calc.calculate
import dev.jesusmolano.alisio.core.command.PhoneCommand
import dev.jesusmolano.alisio.core.command.parsePhoneCommand
import dev.jesusmolano.alisio.core.extras.ClockCommand
import dev.jesusmolano.alisio.core.extras.parseClockCommand
import dev.jesusmolano.alisio.core.notes.parseNoteCommand
import dev.jesusmolano.alisio.core.people.Channel
import dev.jesusmolano.alisio.core.people.MainChannel
import dev.jesusmolano.alisio.core.people.channelKey
import dev.jesusmolano.alisio.core.people.forgetChannels
import dev.jesusmolano.alisio.core.people.mainChannel
import dev.jesusmolano.alisio.core.people.openingsByPerson
import dev.jesusmolano.alisio.core.people.orderedChannels
import dev.jesusmolano.alisio.core.people.parseChannelPrefix
import dev.jesusmolano.alisio.core.people.personKey
import dev.jesusmolano.alisio.core.people.withoutContacts
import dev.jesusmolano.alisio.core.search.IndexItem
import dev.jesusmolano.alisio.core.search.Usage
import dev.jesusmolano.alisio.core.search.mostRecentKeys
import dev.jesusmolano.alisio.core.search.search
import dev.jesusmolano.alisio.core.search.toSections
import dev.jesusmolano.alisio.core.settings.SettingsDestination
import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.AppsRepository
import dev.jesusmolano.alisio.data.apps.ShortcutEntry
import dev.jesusmolano.alisio.data.contacts.ContactPhotos
import dev.jesusmolano.alisio.data.contacts.ContactsRepository
import dev.jesusmolano.alisio.data.contacts.Person
import dev.jesusmolano.alisio.data.db.Forgotten
import dev.jesusmolano.alisio.data.history.LaunchHistory
import dev.jesusmolano.alisio.data.notes.NotesRepository
import dev.jesusmolano.alisio.data.now.ForgottenRepository
import dev.jesusmolano.alisio.data.pause.LaunchPause
import dev.jesusmolano.alisio.data.settings.SYSTEM_SETTINGS
import dev.jesusmolano.alisio.data.settings.SystemSetting
import dev.jesusmolano.alisio.data.system.PhoneControls
import dev.jesusmolano.alisio.data.system.PhoneResult
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lo que un resultado abre. */
sealed interface Target {
    class App(val app: AppEntry) : Target

    class Shortcut(val shortcut: ShortcutEntry) : Target

    class Setting(val setting: SystemSetting, val label: String) : Target

    /** Una persona de la agenda: abre su tarjeta, donde se elige el canal. */
    class Contact(val person: Person) : Target

    /**
     * Los ajustes del propio Alisio: su portada o una de sus secciones.
     *
     * @param section nombre de los ajustes cuando [page] es una sección, para decir de dónde es.
     */
    class AlisioSettings(val label: String, val page: SettingsDestination, val section: String?) : Target

    /** Zonas, la vista de islas con todas las apps. */
    class Zones(val label: String) : Target
}

enum class Section(val limit: Int) {
    PEOPLE(4),
    APPS(6),
    SHORTCUTS(4),
    SETTINGS(3)
}

class Result(val target: Target, val positions: IntArray)

/** Lo que lo escrito pide hacer en vez de buscar (§5.8). */
sealed interface Quick {
    /** Poner una alarma o un temporizador. */
    class Clock(val command: ClockCommand) : Quick

    /** Guardar una nota en el Inicio («+ comprar pan»). */
    class Note(val text: String) : Quick

    /** Una orden para el teléfono («linterna», «brillo 40»). */
    class Phone(val command: PhoneCommand) : Quick
}

/**
 * Lo que pinta la barra.
 *
 * @param sections por orden de cercanía a la barra: la primera es la del mejor resultado, y dentro
 *   de cada una el primero es el mejor.
 * @param calculation resultado si lo escrito es una operación; entonces es lo que abre Enter.
 * @param quick alarma o temporizador que lo escrito pide, si no es una operación.
 * @param recents apps abiertas hace menos; solo se enseñan con la barra vacía.
 * @param recentPeople personas contactadas hace menos; también solo con la barra vacía.
 * @param channel el canal pedido delante del nombre («wa laura»): va primero en cada persona.
 */
class SearchState(
    val query: String = "",
    val sections: List<Pair<Section, List<Result>>> = emptyList(),
    val calculation: String? = null,
    val quick: Quick? = null,
    val recents: List<AppEntry> = emptyList(),
    val recentPeople: List<Person> = emptyList(),
    val channel: Channel? = null
) {
    /**
     * Si [quick] es lo que abre Enter: la alarma y el temporizador empiezan por su palabra, la nota
     * por «+» y las órdenes del teléfono son la frase entera; las tres son inequívocas.
     */
    val quickFirst: Boolean get() = quick != null

    /** El resultado que abre Enter, si no hay cálculo ni manda [quick]. */
    val best: Result?
        get() = if (calculation == null && !quickFirst) sections.firstOrNull()?.second?.firstOrNull() else null
}

/** Qué entiende la barra además de buscar, según los ajustes: notas y órdenes para el teléfono. */
class QuickOptions(val notes: Boolean, val phone: Boolean)

class SearchSource(
    val index: List<IndexItem<Target>>,
    val usage: Usage,
    val recents: List<AppEntry>,
    val recentPeople: List<Person>
)

class SearchViewModel(
    private val appsRepository: AppsRepository,
    private val history: LaunchHistory,
    private val contacts: ContactsRepository,
    private val pause: LaunchPause,
    private val clock: Clock,
    locales: StateFlow<String>,
    private val forgotten: ForgottenRepository,
    private val notes: NotesRepository,
    private val phone: PhoneControls,
    private val text: (Int) -> String
) : ViewModel() {
    private val usage = MutableStateFlow(Usage.NONE)

    // Los nombres van resueltos dentro del índice y este ViewModel sobrevive a un cambio de idioma
    // del sistema: se vuelven a pedir cada vez que cambia `AppContainer.locales`.
    private val settingItems = locales.map { settingItems() }

    private fun settingItems(): List<IndexItem<Target>> {
        val alisioSettingsLabel = text(R.string.settings_title)
        val zonesLabel = text(R.string.zones_title)
        return SYSTEM_SETTINGS.map { setting ->
            val label = text(setting.label)
            IndexItem<Target>(setting.key, label, Target.Setting(setting, label), setting.synonyms, SETTING_WEIGHT)
        } +
            // La portada y cada sección: quien escribe «acento» o «pausa» llega a su página sin recorrerlos.
            ALISIO_PAGES.map { page ->
                val label = text(page.label)
                val cover = page.destination == SettingsDestination.COVER
                IndexItem<Target>(
                    key = alisioSettingsKey(page.destination),
                    label = label,
                    payload = Target.AlisioSettings(label, page.destination, alisioSettingsLabel.takeIf { !cover }),
                    synonyms = page.synonyms,
                    weight = SETTING_WEIGHT
                )
            } +
            // Zonas hace de cajón de apps: se encuentra también por los nombres de siempre.
            IndexItem<Target>(
                key = ZONES_KEY,
                label = zonesLabel,
                payload = Target.Zones(zonesLabel),
                synonyms =
                    listOf("zonas", "zones", "islas", "islands", "todas las apps", "all apps", "cajón", "drawer"),
                weight = SETTING_WEIGHT
            )
    }

    private val index =
        combine(
            appsRepository.apps,
            appsRepository.shortcuts,
            contacts.people,
            settingItems
        ) { apps, shortcuts, people, settingItems ->
            // Una conversación con alguien de la agenda es esa persona: solo sale su tarjeta. Las demás
            // conversaciones (un grupo, alguien sin guardar) van con las personas, no con las acciones.
            val (conversations, actions) = shortcuts.partition { it.isConversation }
            people.map { IndexItem<Target>(personKey(it.lookupKey), it.name, Target.Contact(it)) } +
                conversations
                    .withoutContacts(people.map { it.name }) { it.label }
                    .map { IndexItem<Target>(it.key, it.label, Target.Shortcut(it), weight = CONVERSATION_WEIGHT) } +
                apps.orEmpty().map { app ->
                    // El nombre de la app sigue encontrándola aunque se vea con otro. Una oculta solo sale con
                    // uno de los dos nombres entero.
                    IndexItem<Target>(
                        key = app.key.toString(),
                        label = app.label,
                        payload = Target.App(app),
                        synonyms = if (app.renamed) listOf(app.originalLabel) else emptyList(),
                        exactOnly = app.hidden
                    )
                } +
                actions.map { IndexItem<Target>(it.key, it.label, Target.Shortcut(it), weight = SHORTCUT_WEIGHT) } +
                settingItems
        }

    private val recents =
        combine(appsRepository.apps, history.openings) { apps, openings ->
            // Una app oculta no sale en Recientes aunque se haya abierto, ni una que el sistema pide no
            // sugerir (docs/IDEAS.md §2.8).
            val byKey =
                apps.orEmpty().filterNot { it.hidden || it.hideFromSuggestions }.associateBy { it.key.toString() }
            openings.mostRecentKeys().mapNotNull(byKey::get).take(RECENT_APPS)
        }

    private val recentPeople =
        combine(contacts.people, history.openings) { people, openings ->
            val byKey = people.associateBy { personKey(it.lookupKey) }
            openingsByPerson(openings).mostRecentKeys().mapNotNull(byKey::get).take(RECENT_PEOPLE)
        }

    /** Si hay permiso para leer la agenda; sin él, la barra ofrece concederlo. */
    val contactsGranted: StateFlow<Boolean> = contacts.granted

    /** Las fotos de las personas, que la pantalla pide una a una según las pinta. */
    val photos: ContactPhotos = contacts.photos

    /** Todo lo que hace falta para buscar; cambia al instalar apps o al abrir la barra, no al teclear. */
    val source: StateFlow<SearchSource> =
        combine(index, usage, recents, recentPeople, ::SearchSource)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                SearchSource(emptyList(), Usage.NONE, emptyList(), emptyList())
            )

    /**
     * Busca en el momento, sin pasar por un flujo: la pantalla lo llama en cada tecla con el texto
     * que tiene el campo, así Enter y los resultados nunca van una letra por detrás (§5.3: cada
     * tecla recorre el índice entero).
     */
    fun search(
        query: String,
        source: SearchSource,
        options: QuickOptions,
        hasChannel: (Person, Channel) -> Boolean
    ): SearchState {
        // «+ texto» es una nota antes que cualquier otra cosa: el «+» del principio no busca nada.
        if (options.notes) {
            parseNoteCommand(query)?.let { note ->
                return SearchState(query, quick = Quick.Note(note), recents = source.recents)
            }
        }
        // «wa laura»: solo las personas que tienen ese canal, buscadas por lo que va detrás del verbo.
        // Si nadie lo tiene, lo escrito se busca tal cual (puede ser una app que empieza así).
        parseChannelPrefix(query)?.let { prefix ->
            val people =
                search(source.index, prefix.name, source.usage)
                    .filter { hit ->
                        val person = (hit.item.payload as? Target.Contact)?.person
                        person != null && hasChannel(person, prefix.channel)
                    }
                    .take(Section.PEOPLE.limit)
                    .map { Result(it.item.payload, it.positions) }
            if (people.isNotEmpty()) {
                return SearchState(
                    query,
                    listOf(Section.PEOPLE to people),
                    recents = source.recents,
                    channel = prefix.channel
                )
            }
        }
        val sections =
            search(source.index, query, source.usage)
                .toSections(sectionOf = { sectionOf(it.item.payload) }, limitOf = Section::limit)
                .map { (section, hits) -> section to hits.map { Result(it.item.payload, it.positions) } }
        val calculation = calculate(query)
        // Una operación gana a todo: lo que se lee como cuenta no se interpreta además como orden.
        val quick =
            if (calculation == null) {
                parseClockCommand(query)?.let(Quick::Clock)
                    ?: parsePhoneCommand(query)?.takeIf { options.phone }?.let(Quick::Phone)
            } else {
                null
            }
        return SearchState(query, sections, calculation, quick, source.recents, source.recentPeople)
    }

    /** Guarda una nota para el Inicio. */
    fun addNote(text: String) = notes.add(text)

    /** Da una orden al teléfono y dice cómo ha ido. */
    suspend fun runPhone(command: PhoneCommand): PhoneResult = phone.run(command)

    /**
     * Al abrir la barra: la frecencia se calcula para este momento. Cada persona lleva además la
     * suma de sus canales, que es lo que la ordena entre los resultados. Del canal aprendido de cada
     * persona se deja fuera lo olvidado ([forgetChannels]); su puesto en la barra no cambia.
     */
    fun onOpened() {
        opened = true
        val openings = history.openings.value
        val channels = forgetChannels(openings, forgotten.marksOf(Forgotten.KIND_CHANNEL))
        usage.value = Usage.from(channels + openingsByPerson(openings), clock.now(), ZoneId.systemDefault())
    }

    // Olvidar un canal con la barra abierta tiene que verse en el acto: se recalcula al guardarse.
    private var opened = false

    init {
        viewModelScope.launch { forgotten.marks.drop(1).collect { if (opened) onOpened() } }
    }

    /**
     * Olvida el canal aprendido de [person] (docs/IDEAS.md §2.6): el botón principal vuelve al de por
     * defecto hasta que se aprenda otro. Devuelve cómo deshacerlo.
     */
    fun forgetChannel(person: Person): () -> Unit =
        forgotten.forget(Forgotten.KIND_CHANNEL, personKey(person.lookupKey))

    /** Ningún contacto tiene filas de WhatsApp: señal de que WhatsApp ya no las escribe (riesgo de §9). */
    fun noWhatsappRows(): Boolean = contacts.people.value.none { it.whatsappRows.isNotEmpty() }

    /** Canal principal de [person], según lo aprendido. */
    fun mainChannel(person: Person, channels: Set<Channel>, source: SearchSource): MainChannel? =
        mainChannel(person.lookupKey, channels, source.usage.frecency)

    /** Sus canales en el orden en que se enseñan: el principal primero. */
    fun orderedChannels(person: Person, channels: Set<Channel>, source: SearchSource): List<Channel> =
        orderedChannels(person.lookupKey, channels, source.usage.frecency)

    /** Apunta el canal usado: de ahí sale el canal aprendido y el orden de las personas. */
    fun recordContact(person: Person, channel: Channel) =
        history.record(LaunchHistory.TYPE_CONTACT, channelKey(person.lookupKey, channel))

    /** Abre una app o un acceso directo y lo apunta. Devuelve `false` si no se ha podido abrir. */
    fun launch(target: Target): Boolean = when (target) {
        // Con pausa ([LaunchPause.open]), abrir y apuntar esperan a que se confirme.
        is Target.App -> pause.open(target.app) { appsRepository.launch(target.app).also { if (it) record(target) } }

        is Target.Shortcut ->
            pause.open(target.shortcut) { appsRepository.launch(target.shortcut).also { if (it) record(target) } }

        // Los ajustes los abre la pantalla, que es quien tiene el contexto de la actividad.
        is Target.Setting -> false

        is Target.AlisioSettings -> false

        is Target.Zones -> false

        // A una persona se la contacta desde su tarjeta, por un canal.
        is Target.Contact -> false
    }

    fun record(target: Target) = when (target) {
        is Target.App -> history.record(LaunchHistory.TYPE_APP, target.app.key.toString())

        is Target.Shortcut -> history.record(LaunchHistory.TYPE_SHORTCUT, target.shortcut.key)

        is Target.Setting -> history.record(LaunchHistory.TYPE_SETTING, target.setting.key)

        is Target.AlisioSettings -> history.record(LaunchHistory.TYPE_SETTING, alisioSettingsKey(target.page))

        is Target.Zones -> history.record(LaunchHistory.TYPE_SETTING, ZONES_KEY)

        // Se apunta con su canal, en `recordContact`.
        is Target.Contact -> Unit
    }

    private fun sectionOf(target: Target) = when (target) {
        is Target.App -> Section.APPS

        is Target.Shortcut -> if (target.shortcut.isConversation) Section.PEOPLE else Section.SHORTCUTS

        is Target.Setting -> Section.SETTINGS

        is Target.AlisioSettings -> Section.SETTINGS

        // Es donde están las apps, no un ajuste: sale con ellas.
        is Target.Zones -> Section.APPS

        is Target.Contact -> Section.PEOPLE
    }

    companion object {
        private const val RECENT_APPS = 4
        private const val RECENT_PEOPLE = 3
        private const val ALISIO_SETTINGS_KEY = "setting:alisio"
        private const val ZONES_KEY = "alisio:zones"

        /** La portada conserva la clave de siempre, para no perder su historial; cada sección añade la suya. */
        private fun alisioSettingsKey(page: SettingsDestination): String =
            if (page == SettingsDestination.COVER) ALISIO_SETTINGS_KEY else ALISIO_SETTINGS_KEY + ":" + page.id

        /**
         * Una página de los ajustes de Alisio como resultado de la barra.
         *
         * @param synonyms las opciones que hay dentro, en español y en inglés: se encuentra por ellas.
         */
        private class AlisioPage(val destination: SettingsDestination, val label: Int, val synonyms: List<String>)

        private val ALISIO_PAGES =
            listOf(
                AlisioPage(
                    SettingsDestination.COVER,
                    R.string.settings_title,
                    listOf(
                        "ajustes de alisio", "alisio settings", "launcher", "configuración", "preferences",
                        "copia", "backup", "exportar", "importar", "export", "import"
                    )
                ),
                AlisioPage(
                    SettingsDestination.LOOK,
                    R.string.settings_appearance,
                    listOf(
                        "aspecto", "apariencia", "look", "appearance", "acento", "accent", "color propio",
                        "custom color", "color del día", "day color", "velo", "veil", "fondo de pantalla",
                        "wallpaper", "fondos que rotan", "rotating wallpapers", "tipografía", "letra", "fuente", "font"
                    )
                ),
                AlisioPage(
                    SettingsDestination.ICONS,
                    R.string.settings_icons,
                    listOf(
                        "iconos", "icons", "estilo de iconos", "icon style", "monocromo", "monochrome",
                        "paquete de iconos", "icon pack", "iconos vivos", "live icons", "icono propio", "custom icon",
                        "cambiar icono", "change icon", "nombres en el dock", "dock labels", "nombres en zonas",
                        "zones labels", "nombres", "labels"
                    )
                ),
                AlisioPage(
                    SettingsDestination.HOME,
                    R.string.settings_home,
                    listOf(
                        "inicio", "home screen", "dock", "huecos del dock", "dock slots", "hora", "reloj",
                        "clock", "formato de la hora", "time format", "12 h", "24 h", "junto a la hora",
                        "next to the time", "alarma", "batería", "battery", "tiempo", "weather", "salida del sol",
                        "puesta del sol", "sunrise", "sunset", "evento", "event", "uso del teléfono", "screen time",
                        "notas", "notes", "sugerencia", "ahora", "suggestion", "en curso", "reproductor", "player",
                        "puntos", "no leído", "unread", "dots", "personas sobre el dock", "favoritos", "favorites",
                        "bluetooth"
                    )
                ),
                AlisioPage(
                    SettingsDestination.GESTURES,
                    R.string.settings_gestures,
                    listOf(
                        "gestos", "gestures", "índice de letras", "letter index", "trazo", "trace",
                        "dibujar la inicial", "draw the initial", "deslizar sobre el dock", "swipe on the dock",
                        "deslizar", "swipe", "pellizcar", "pinch", "dos toques", "double tap", "bloquear", "lock",
                        "bajar las notificaciones", "pull down notifications"
                    )
                ),
                AlisioPage(
                    SettingsDestination.SEARCH,
                    R.string.settings_search,
                    listOf(
                        "búsqueda", "barra", "search", "asistente", "assistant", "búsqueda web", "web search",
                        "portapapeles", "clipboard", "personas", "people", "fotos de contacto", "contact photos",
                        "órdenes", "commands", "órdenes del teléfono", "phone commands"
                    )
                ),
                AlisioPage(
                    SettingsDestination.ZONES,
                    R.string.settings_islands,
                    listOf(
                        "islas propias", "nueva isla", "your islands", "new island", "ajustes de zonas",
                        "apps ocultas", "hidden apps", "ocultar apps", "hide apps"
                    )
                ),
                AlisioPage(
                    SettingsDestination.PAUSE,
                    R.string.settings_pause,
                    listOf("pausa", "pause", "esperar", "delay", "apps con pausa", "veces hoy", "times today")
                ),
                AlisioPage(
                    SettingsDestination.PERMISSIONS,
                    R.string.settings_permissions,
                    listOf(
                        "permisos", "permissions", "accesos", "access", "accesibilidad", "accessibility",
                        "notificaciones", "notifications", "acceso a uso", "usage access", "app de inicio",
                        "default home", "permiso de calendario", "calendar permission", "ubicación aproximada",
                        "approximate location", "ubicación", "location", "modificar ajustes del sistema",
                        "modify system settings"
                    )
                )
            )

        // Por debajo de 1 para que, a igual coincidencia, ganen la app y la persona de la agenda.
        private const val CONVERSATION_WEIGHT = 0.95
        private const val SHORTCUT_WEIGHT = 0.85
        private const val SETTING_WEIGHT = 0.8

        fun factory(container: AppContainer, context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    container.appsRepository,
                    container.launchHistory,
                    container.contacts,
                    container.launchPause,
                    container.clock,
                    container.locales,
                    container.forgotten,
                    container.notes,
                    container.phoneControls,
                    // El contexto de la aplicación: sus recursos siguen el idioma actual del sistema.
                    context::getString
                )
            }
        }
    }
}
