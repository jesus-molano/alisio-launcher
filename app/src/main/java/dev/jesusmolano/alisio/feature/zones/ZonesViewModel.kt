package dev.jesusmolano.alisio.feature.zones

import android.graphics.Bitmap
import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jesusmolano.alisio.AppContainer
import dev.jesusmolano.alisio.core.search.frecency
import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.core.zones.CustomZone
import dev.jesusmolano.alisio.core.zones.ZoneApp
import dev.jesusmolano.alisio.core.zones.ZoneId
import dev.jesusmolano.alisio.core.zones.ZoneInput
import dev.jesusmolano.alisio.core.zones.ZoneResolver
import dev.jesusmolano.alisio.core.zones.ZoneStack
import dev.jesusmolano.alisio.core.zones.buildZones
import dev.jesusmolano.alisio.core.zones.moveTargets
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.AppsRepository
import dev.jesusmolano.alisio.data.apps.ShortcutEntry
import dev.jesusmolano.alisio.data.apps.SystemApps
import dev.jesusmolano.alisio.data.history.LaunchHistory
import dev.jesusmolano.alisio.data.home.HomeConfigRepository
import dev.jesusmolano.alisio.data.home.SlotConfig
import dev.jesusmolano.alisio.data.pause.LaunchPause
import dev.jesusmolano.alisio.data.usage.UsageAccess
import dev.jesusmolano.alisio.data.zones.ZonesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Una isla lista para pintar: sus claves ya son apps instaladas. [name] solo en pilas e islas propias. */
class ZoneView(val id: ZoneId, val name: String?, val apps: List<AppEntry>)

/** Las islas y en cuál está cada app. */
class ZonesState(val zones: List<ZoneView>, val custom: List<CustomZone>, private val resolver: ZoneResolver) {
    fun zoneOf(app: AppEntry): ZoneId = resolver.zoneOf(app.toZoneApp())

    /** La isla que le toca a la app sin asignación: llevarla ahí es olvidar la que tuviera. */
    fun defaultZone(app: AppEntry): ZoneId = resolver.defaultZone(app.toZoneApp())

    /** Las islas a las que se puede llevar [app], en el orden en que se ven. */
    fun targetsFor(app: AppEntry): List<ZoneView> {
        val ids = moveTargets(zones.map { it.id }, current = zoneOf(app), default = defaultZone(app)).toSet()
        return zones.filter { it.id in ids }
    }
}

private fun AppEntry.toZoneApp() = ZoneApp(key.toString(), label, component.packageName)

/**
 * Zonas (§5.7): reparte las apps en islas a partir del dock, de lo asignado a mano y de las apps del
 * sistema. También lo usa el Interior de app del Inicio, para «Mover a otra zona».
 */
class ZonesViewModel(
    private val appsRepository: AppsRepository,
    homeConfig: HomeConfigRepository,
    private val zones: ZonesRepository,
    private val history: LaunchHistory,
    private val systemApps: SystemApps,
    private val usageAccess: UsageAccess,
    private val pause: LaunchPause,
    private val clock: Clock
) : ViewModel() {
    val shortcuts: StateFlow<List<ShortcutEntry>> = appsRepository.shortcuts
    val usageGranted: StateFlow<Boolean> = usageAccess.granted

    // Sube cada vez que Zonas se abre.
    private val opened = MutableStateFlow(0)

    // Se recalcula al abrir Zonas y no con cada apertura: las apps no deben recolocarse a la vista.
    private val frecencies =
        opened.map {
            val now = clock.now()
            history.openings.value.mapValues { frecency(it.value, now) }
        }

    // Las apps predeterminadas pueden cambiar en los ajustes del sistema sin que cambie la lista de
    // apps: se vuelven a resolver también en cada apertura.
    private val systemPackages =
        combine(appsRepository.apps, opened) { _, _ -> systemApps.packages() }.flowOn(Dispatchers.IO)

    private val stored =
        combine(zones.custom, zones.assignments) { custom, assignments ->
            if (custom == null || assignments == null) null else custom to assignments
        }

    /** `null` hasta tener las apps, el dock y lo guardado: pintar antes enseñaría todo en Resto un instante. */
    val state: StateFlow<ZonesState?> =
        combine(appsRepository.apps, homeConfig.dock, stored, systemPackages, frecencies) {
                apps,
                dock,
                stored,
                system,
                frecency
            ->
            if (apps == null || dock == null || stored == null) return@combine null
            val (custom, assignments) = stored
            val input =
                ZoneInput(
                    apps = apps.map { it.toZoneApp() },
                    dockApps = dock.filterIsInstance<SlotConfig.App>().map { it.appKey },
                    stacks =
                        dock.mapIndexedNotNull { slot, config ->
                            (config as? SlotConfig.Stack)?.let { ZoneStack(slot, it.name, it.appKeys) }
                        },
                    custom = custom,
                    assignments = assignments,
                    systemPackages = system,
                    frecency = frecency
                )
            val resolver = ZoneResolver(input)
            val byKey = apps.associateBy { it.key.toString() }
            val views = buildZones(input, resolver).map { zone ->
                ZoneView(zone.id, zone.name, zone.apps.mapNotNull { byKey[it.key] })
            }
            ZonesState(views, custom, resolver)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Zonas se abre: el orden dentro de cada isla se calcula para este momento. */
    fun onOpened() {
        opened.value++
    }

    /** Abre la app y lo apunta en el historial. Devuelve `false` si no se ha podido abrir. */
    fun launch(app: AppEntry): Boolean = pause.open(app) {
        val launched = appsRepository.launch(app)
        if (launched) history.record(LaunchHistory.TYPE_APP, app.key.toString())
        launched
    }

    fun launch(shortcut: ShortcutEntry): Boolean = pause.open(shortcut) {
        val launched = appsRepository.launch(shortcut)
        if (launched) history.record(LaunchHistory.TYPE_SHORTCUT, shortcut.key)
        launched
    }

    /** Lleva [app] a la isla [target]; a una isla del dock que no es la suya no hace nada. */
    fun move(app: AppEntry, target: ZoneId) {
        val default = state.value?.defaultZone(app) ?: return
        zones.move(app.key.toString(), target, default)
    }

    fun createZone(name: String) = zones.create(name)

    fun renameZone(id: Long, name: String) = zones.rename(id, name)

    fun deleteZone(id: Long) = zones.delete(id)

    /** Tiempo de uso de hoy, o `null` sin acceso a uso o si la app es de otro perfil. */
    suspend fun usageToday(app: AppEntry): Long? =
        if (app.user == Process.myUserHandle()) usageAccess.todayMillis(app.component.packageName) else null

    fun openAppInfo(app: AppEntry): Boolean = appsRepository.openDetails(app)

    suspend fun shortcutIcon(shortcut: ShortcutEntry): Bitmap? = appsRepository.shortcutIcon(shortcut)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ZonesViewModel(
                    container.appsRepository,
                    container.homeConfig,
                    container.zones,
                    container.launchHistory,
                    container.systemApps,
                    container.usageAccess,
                    container.launchPause,
                    container.clock
                )
            }
        }
    }
}
