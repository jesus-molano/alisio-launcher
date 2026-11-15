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
import dev.jesusmolano.alisio.core.people.Channel
import dev.jesusmolano.alisio.core.people.MainChannel
import dev.jesusmolano.alisio.core.people.channelKey
import dev.jesusmolano.alisio.core.people.mainChannel
import dev.jesusmolano.alisio.core.people.openingsByPerson
import dev.jesusmolano.alisio.core.people.personKey
import dev.jesusmolano.alisio.core.search.IndexItem
import dev.jesusmolano.alisio.core.search.Usage
import dev.jesusmolano.alisio.core.search.mostRecentKeys
import dev.jesusmolano.alisio.core.search.search
import dev.jesusmolano.alisio.core.search.toSections
import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.AppsRepository
import dev.jesusmolano.alisio.data.apps.ShortcutEntry
import dev.jesusmolano.alisio.data.contacts.ContactsRepository
import dev.jesusmolano.alisio.data.contacts.Person
import dev.jesusmolano.alisio.data.history.LaunchHistory
import dev.jesusmolano.alisio.data.settings.SYSTEM_SETTINGS
import dev.jesusmolano.alisio.data.settings.SystemSetting
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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
}

enum class Section(val limit: Int) {
    PEOPLE(3),
    APPS(6),
    SHORTCUTS(4),
    SETTINGS(3)
}

class Result(val target: Target, val positions: IntArray)

/**
 * Lo que pinta la barra.
 *
 * @param sections por orden de cercanía a la barra: la primera es la del mejor resultado, y dentro
 *   de cada una el primero es el mejor.
 * @param calculation resultado si lo escrito es una operación; entonces es lo que abre Enter.
 * @param recents apps abiertas hace menos; solo se enseñan con la barra vacía.
 * @param recentPeople personas contactadas hace menos; también solo con la barra vacía.
 */
class SearchState(
    val query: String = "",
    val sections: List<Pair<Section, List<Result>>> = emptyList(),
    val calculation: String? = null,
    val recents: List<AppEntry> = emptyList(),
    val recentPeople: List<Person> = emptyList()
) {
    /** El resultado que abre Enter, si no hay cálculo. */
    val best: Result? get() = if (calculation == null) sections.firstOrNull()?.second?.firstOrNull() else null
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
    private val clock: Clock,
    settingLabels: Map<SystemSetting, String>,
    alisioSettingsLabel: String
) : ViewModel() {
    private val usage = MutableStateFlow(Usage.NONE)

    private val settingItems =
        settingLabels.map { (setting, label) ->
            IndexItem<Target>(setting.key, label, Target.Setting(setting, label), setting.synonyms, SETTING_WEIGHT)
        } +
            IndexItem<Target>(
                key = ALISIO_SETTINGS_KEY,
                label = alisioSettingsLabel,
                payload = Target.AlisioSettings(alisioSettingsLabel),
                synonyms = listOf("ajustes de alisio", "alisio settings", "launcher", "iconos", "icons", "dock"),
                weight = SETTING_WEIGHT
            )

    private val index =
        combine(appsRepository.apps, appsRepository.shortcuts, contacts.people) { apps, shortcuts, people ->
            people.map { IndexItem<Target>(personKey(it.lookupKey), it.name, Target.Contact(it)) } +
                apps.orEmpty().map { IndexItem<Target>(it.key.toString(), it.label, Target.App(it)) } +
                shortcuts.map { IndexItem<Target>(it.key, it.label, Target.Shortcut(it), weight = SHORTCUT_WEIGHT) } +
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
    fun search(query: String, source: SearchSource): SearchState {
        val sections =
            search(source.index, query, source.usage)
                .toSections(sectionOf = { sectionOf(it.item.payload) }, limitOf = Section::limit)
                .map { (section, hits) -> section to hits.map { Result(it.item.payload, it.positions) } }
        return SearchState(query, sections, calculate(query), source.recents, source.recentPeople)
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

    /** Canal del botón principal de la tarjeta de [person], según lo aprendido. */
    fun mainChannel(person: Person, channels: Set<Channel>, source: SearchSource): MainChannel? =
        mainChannel(person.lookupKey, channels, source.usage.frecency)

    /** Apunta el canal usado: de ahí sale el canal aprendido y el orden de las personas. */
    fun recordContact(person: Person, channel: Channel) =
        history.record(LaunchHistory.TYPE_CONTACT, channelKey(person.lookupKey, channel))

    /** Abre una app o un acceso directo y lo apunta. Devuelve `false` si no se ha podido abrir. */
    fun launch(target: Target): Boolean = when (target) {
        is Target.App -> appsRepository.launch(target.app).also { if (it) record(target) }

        is Target.Shortcut -> appsRepository.launch(target.shortcut).also { if (it) record(target) }

        // Los ajustes los abre la pantalla, que es quien tiene el contexto de la actividad.
        is Target.Setting -> false

        is Target.AlisioSettings -> false

        // A una persona se la contacta desde su tarjeta, por un canal.
        is Target.Contact -> false
    }

    fun record(target: Target) = when (target) {
        is Target.App -> history.record(LaunchHistory.TYPE_APP, target.app.key.toString())

        is Target.Shortcut -> history.record(LaunchHistory.TYPE_SHORTCUT, target.shortcut.key)

        is Target.Setting -> history.record(LaunchHistory.TYPE_SETTING, target.setting.key)

        is Target.AlisioSettings -> history.record(LaunchHistory.TYPE_SETTING, ALISIO_SETTINGS_KEY)

        // Se apunta con su canal, en `recordContact`.
        is Target.Contact -> Unit
    }

    private fun sectionOf(target: Target) = when (target) {
        is Target.App -> Section.APPS
        is Target.Shortcut -> Section.SHORTCUTS
        is Target.Setting -> Section.SETTINGS
        is Target.AlisioSettings -> Section.SETTINGS
        is Target.Contact -> Section.PEOPLE
    }

    companion object {
        private const val RECENT_APPS = 4
        private const val RECENT_PEOPLE = 3
        private const val ALISIO_SETTINGS_KEY = "setting:alisio"

        // Por debajo de 1 para que, a igual coincidencia, gane la app.
        private const val SHORTCUT_WEIGHT = 0.9
        private const val SETTING_WEIGHT = 0.85

        fun factory(container: AppContainer, context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    container.appsRepository,
                    container.launchHistory,
                    container.contacts,
                    container.clock,
                    SYSTEM_SETTINGS.associateWith { context.getString(it.label) },
                    context.getString(R.string.settings_title)
                )
            }
        }
    }
}
