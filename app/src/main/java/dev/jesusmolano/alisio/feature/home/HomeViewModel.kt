package dev.jesusmolano.alisio.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jesusmolano.alisio.AppContainer
import dev.jesusmolano.alisio.core.now.NowApp
import dev.jesusmolano.alisio.core.now.NowInput
import dev.jesusmolano.alisio.core.now.NowSuggestion
import dev.jesusmolano.alisio.core.now.suggest
import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.AppsRepository
import dev.jesusmolano.alisio.data.history.LaunchHistory
import dev.jesusmolano.alisio.data.home.HomeConfigRepository
import dev.jesusmolano.alisio.data.home.SlotConfig
import dev.jesusmolano.alisio.data.home.StackIcon
import dev.jesusmolano.alisio.data.now.NowFeedbackRepository
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Hueco del dock listo para pintar: las claves ya son apps instaladas. */
sealed interface DockSlot {
    data object Empty : DockSlot

    class App(val app: AppEntry) : DockSlot

    class Stack(val name: String, val apps: List<AppEntry>, val icon: StackIcon, val glyph: String) : DockSlot
}

class HomeViewModel(
    private val appsRepository: AppsRepository,
    private val homeConfig: HomeConfigRepository,
    private val history: LaunchHistory,
    private val nowFeedback: NowFeedbackRepository,
    private val clock: Clock
) : ViewModel() {
    /** `null` mientras se carga la primera vez. */
    val apps: StateFlow<List<AppEntry>?> = appsRepository.apps

    /**
     * `null` hasta tener las apps y la configuración: pintar antes enseñaría huecos vacíos un instante.
     * Una app desinstalada deja su hueco vacío y desaparece de su pila, sin tocar lo guardado.
     */
    val dock: StateFlow<List<DockSlot>?> =
        combine(appsRepository.apps, homeConfig.dock) { apps, slots ->
            if (apps == null || slots == null) return@combine null
            val byKey = apps.associateBy { it.key.toString() }
            slots.map { slot ->
                when (slot) {
                    SlotConfig.Empty -> DockSlot.Empty

                    is SlotConfig.App -> byKey[slot.appKey]?.let(DockSlot::App) ?: DockSlot.Empty

                    is SlotConfig.Stack ->
                        DockSlot.Stack(slot.name, slot.appKeys.mapNotNull(byKey::get), slot.icon, slot.glyph)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Sube cada vez que el Inicio vuelve a verse: la sugerencia depende de la hora y hay que recalcularla.
    private val nowTick = MutableStateFlow(0)

    /** La sugerencia de Ahora con su app, o `null` si ninguna regla tiene nada que proponer. */
    val now: StateFlow<Pair<NowSuggestion, AppEntry>?> =
        combine(appsRepository.apps, history.openings, homeConfig.dock, nowFeedback.feedback, nowTick) {
                apps,
                openings,
                slots,
                feedback,
                _
            ->
            if (apps == null || slots == null) return@combine null
            val byKey = apps.associateBy { it.key.toString() }
            val dockKeys =
                slots.flatMapTo(HashSet()) { slot ->
                    when (slot) {
                        SlotConfig.Empty -> emptyList()
                        is SlotConfig.App -> listOf(slot.appKey)
                        is SlotConfig.Stack -> slot.appKeys
                    }
                }
            val input =
                NowInput(
                    now = clock.now(),
                    zone = ZoneId.systemDefault(),
                    apps = apps.map { NowApp(it.key.toString(), it.updatedAt) },
                    openings = openings,
                    dockKeys = dockKeys,
                    feedback = feedback
                )
            suggest(input)?.let { suggestion -> byKey[suggestion.appKey]?.let { suggestion to it } }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** El Inicio vuelve a estar a la vista: se recalcula la sugerencia para la hora de ahora. */
    fun refreshNow() {
        nowTick.value++
    }

    fun dismissNow(suggestion: NowSuggestion) = nowFeedback.dismiss(suggestion.rule, suggestion.appKey)

    /** Abre la app y lo apunta en el historial. Devuelve `false` si no se ha podido abrir. */
    fun launch(app: AppEntry): Boolean {
        val launched = appsRepository.launch(app)
        if (launched) history.record(LaunchHistory.TYPE_APP, app.key.toString())
        return launched
    }

    fun setDockApp(position: Int, app: AppEntry) = homeConfig.setApp(position, app.key.toString())

    fun makeDockStack(position: Int, name: String) = homeConfig.makeStack(position, name)

    fun renameDockStack(position: Int, name: String) = homeConfig.renameStack(position, name)

    fun setStackIcon(position: Int, icon: StackIcon, glyph: String) = homeConfig.setStackIcon(position, icon, glyph)

    fun clearDockSlot(position: Int) = homeConfig.clear(position)

    fun reorderStack(position: Int, apps: List<AppEntry>) =
        homeConfig.reorderStack(position, apps.map { it.key.toString() })

    fun toggleInStack(position: Int, app: AppEntry) = homeConfig.toggleInStack(position, app.key.toString())

    /** El rol de inicio da acceso a los accesos directos: hay que volver a leerlos al obtenerlo. */
    fun onHomeRoleGranted() = appsRepository.reload()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    container.appsRepository,
                    container.homeConfig,
                    container.launchHistory,
                    container.nowFeedback,
                    container.clock
                )
            }
        }
    }
}
