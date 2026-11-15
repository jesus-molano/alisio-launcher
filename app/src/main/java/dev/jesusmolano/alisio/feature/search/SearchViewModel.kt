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
import dev.jesusmolano.alisio.core.extras.ClockCommand
import dev.jesusmolano.alisio.core.extras.Expense
import dev.jesusmolano.alisio.core.extras.expenseUrl
import dev.jesusmolano.alisio.core.extras.parseClockCommand
import dev.jesusmolano.alisio.core.extras.parseExpense
import dev.jesusmolano.alisio.core.people.Channel
import dev.jesusmolano.alisio.core.people.MainChannel
import dev.jesusmolano.alisio.core.people.channelKey
import dev.jesusmolano.alisio.core.people.mainChannel
import dev.jesusmolano.alisio.core.people.openingsByPerson
import dev.jesusmolano.alisio.core.people.orderedChannels
import dev.jesusmolano.alisio.core.people.personKey
import dev.jesusmolano.alisio.core.people.withoutContacts
import dev.jesusmolano.alisio.core.search.IndexItem
import dev.jesusmolano.alisio.core.search.Usage
import dev.jesusmolano.alisio.core.search.mostRecentKeys
import dev.jesusmolano.alisio.core.search.search
import dev.jesusmolano.alisio.core.search.toSections
import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.AppsRepository
import dev.jesusmolano.alisio.data.apps.ShortcutEntry
import dev.jesusmolano.alisio.data.contacts.ContactPhotos
import dev.jesusmolano.alisio.data.contacts.ContactsRepository
import dev.jesusmolano.alisio.data.contacts.Person
import dev.jesusmolano.alisio.data.history.LaunchHistory
import dev.jesusmolano.alisio.data.pause.LaunchPause
import dev.jesusmolano.alisio.data.settings.SYSTEM_SETTINGS
import dev.jesusmolano.alisio.data.settings.SystemSetting
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Lo que un resultado abre. */
sealed interface Target {
    class App(val app: AppEntry) : Target

    class Shortcut(val shortcut: ShortcutEntry) : Target

    class Setting(val setting: SystemSetting, val label: String) : Target

    /** Una persona de la agenda: abre su tarjeta, donde se elige el canal. */
    class Contact(val person: Person) : Target

    /** Los ajustes del propio Alisio. */
    class AlisioSettings(val label: String) : Target

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
    /** Apuntar un gasto: [url] es la plantilla de los ajustes ya rellena. */
    class ExpenseEntry(val expense: Expense, val url: String) : Quick

    /** Poner una alarma o un temporizador. */
    class Clock(val command: ClockCommand) : Quick
}

/**
 * Lo que pinta la barra.
 *
 * @param sections por orden de cercanía a la barra: la primera es la del mejor resultado, y dentro
 *   de cada una el primero es el mejor.
 * @param calculation resultado si lo escrito es una operación; entonces es lo que abre Enter.
 * @param quick gasto, alarma o temporizador que lo escrito pide, si no es una operación.
 * @param recents apps abiertas hace menos; solo se enseñan con la barra vacía.
 * @param recentPeople personas contactadas hace menos; también solo con la barra vacía.
 */
class SearchState(
    val query: String = "",
    val sections: List<Pair<Section, List<Result>>> = emptyList(),
    val calculation: String? = null,
    val quick: Quick? = null,
    val recents: List<AppEntry> = emptyList(),
    val recentPeople: List<Person> = emptyList()
) {
    /**
     * Si [quick] es lo que abre Enter. La alarma y el temporizador empiezan por su palabra y son
     * inequívocos; un gasto tiene la forma de muchas búsquedas («3 en raya»), así que cede ante
     * cualquier resultado y solo se queda Enter cuando no hay ninguno.
     */
    val quickFirst: Boolean get() = quick is Quick.Clock || (quick is Quick.ExpenseEntry && sections.isEmpty())

    /** El resultado que abre Enter, si no hay cálculo ni manda [quick]. */
    val best: Result?
        get() = if (calculation == null && !quickFirst) sections.firstOrNull()?.second?.firstOrNull() else null
}

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
            IndexItem<Target>(
                key = ALISIO_SETTINGS_KEY,
                label = alisioSettingsLabel,
                payload = Target.AlisioSettings(alisioSettingsLabel),
                synonyms = listOf("ajustes de alisio", "alisio settings", "launcher", "iconos", "icons", "dock"),
                weight = SETTING_WEIGHT
            ) +
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
                apps.orEmpty().map { IndexItem<Target>(it.key.toString(), it.label, Target.App(it)) } +
                actions.map { IndexItem<Target>(it.key, it.label, Target.Shortcut(it), weight = SHORTCUT_WEIGHT) } +
                settingItems
        }

    private val recents =
        combine(appsRepository.apps, history.openings) { apps, openings ->
            val byKey = apps.orEmpty().associateBy { it.key.toString() }
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
     *
     * @param expenseTemplate plantilla del gasto rápido de los ajustes; vacía o inválida, no hay gasto.
     */
    fun search(query: String, source: SearchSource, expenseTemplate: String): SearchState {
        val sections =
            search(source.index, query, source.usage)
                .toSections(sectionOf = { sectionOf(it.item.payload) }, limitOf = Section::limit)
                .map { (section, hits) -> section to hits.map { Result(it.item.payload, it.positions) } }
        val calculation = calculate(query)
        // Una operación gana a todo: «12 x 3» es una cuenta, no un gasto de 12 € en «x 3».
        val quick = if (calculation == null) quickOf(query, expenseTemplate) else null
        return SearchState(query, sections, calculation, quick, source.recents, source.recentPeople)
    }

    private fun quickOf(query: String, expenseTemplate: String): Quick? {
        parseClockCommand(query)?.let { return Quick.Clock(it) }
        val expense = parseExpense(query) ?: return null
        return expenseUrl(expenseTemplate, expense)?.let { Quick.ExpenseEntry(expense, it) }
    }

    /**
     * Al abrir la barra: la frecencia se calcula para este momento. Cada persona lleva además la
     * suma de sus canales, que es lo que la ordena entre los resultados.
     */
    fun onOpened() {
        val openings = history.openings.value
        usage.value = Usage.from(openings + openingsByPerson(openings), clock.now(), ZoneId.systemDefault())
    }

    /** Ningún contacto tiene filas de WhatsApp: señal de que WhatsApp ya no las escribe (riesgo de §9). */
    fun noWhatsappRows(): Boolean = contacts.people.value.none { it.whatsappRows.isNotEmpty() }

    /** Tras responder al diálogo de permisos: la agenda se lee o se vacía según lo concedido. */
    fun onContactsPermissionResult() = contacts.onPermissionChanged()

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

        is Target.AlisioSettings -> history.record(LaunchHistory.TYPE_SETTING, ALISIO_SETTINGS_KEY)

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
                    // El contexto de la aplicación: sus recursos siguen el idioma actual del sistema.
                    context::getString
                )
            }
        }
    }
}
