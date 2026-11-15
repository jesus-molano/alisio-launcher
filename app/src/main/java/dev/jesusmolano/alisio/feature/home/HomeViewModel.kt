package dev.jesusmolano.alisio.feature.home

import android.graphics.Bitmap
import android.os.Process
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
import dev.jesusmolano.alisio.data.apps.ShortcutEntry
import dev.jesusmolano.alisio.data.bluetooth.BluetoothAudio
import dev.jesusmolano.alisio.data.history.LaunchHistory
import dev.jesusmolano.alisio.data.home.GestureTarget
import dev.jesusmolano.alisio.data.home.HomeConfigRepository
import dev.jesusmolano.alisio.data.home.IconGestureRepository
import dev.jesusmolano.alisio.data.home.SlotConfig
import dev.jesusmolano.alisio.data.home.StackIcon
import dev.jesusmolano.alisio.data.home.SwipeTarget
import dev.jesusmolano.alisio.data.media.MediaSessions
import dev.jesusmolano.alisio.data.media.NotificationAccess
import dev.jesusmolano.alisio.data.media.NowPlaying
import dev.jesusmolano.alisio.data.now.NowFeedbackRepository
import dev.jesusmolano.alisio.data.usage.UsageAccess
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

/** Las apps del dock, las de las pilas incluidas, en su orden y sin repetir paquete. */
fun List<DockSlot>.dockApps(): List<AppEntry> = flatMap { slot ->
    when (slot) {
        DockSlot.Empty -> emptyList()
        is DockSlot.App -> listOf(slot.app)
        is DockSlot.Stack -> slot.apps
    }
}.distinctBy { it.component.packageName }

class HomeViewModel(
    private val appsRepository: AppsRepository,
    private val homeConfig: HomeConfigRepository,
    private val iconGestures: IconGestureRepository,
    private val history: LaunchHistory,
    private val nowFeedback: NowFeedbackRepository,
    bluetoothAudio: BluetoothAudio,
    notificationAccess: NotificationAccess,
    private val mediaSessions: MediaSessions,
    private val usageAccess: UsageAccess,
    private val clock: Clock
) : ViewModel() {
    val shortcuts: StateFlow<List<ShortcutEntry>> = appsRepository.shortcuts

    /** Si hay acceso a uso; sin él, el Interior de app ofrece concederlo en lugar del uso de hoy. */
    val usageGranted: StateFlow<Boolean> = usageAccess.granted

    /**
     * Destino secundario por clave de app. Un destino que ya no existe (app desinstalada, acceso
     * directo que su app ha retirado) no sale aquí, pero sigue guardado por si vuelve.
     */
    val swipeTargets: StateFlow<Map<String, SwipeTarget>> =
        combine(appsRepository.apps, appsRepository.shortcuts, iconGestures.targets) { apps, shortcuts, targets ->
            if (targets.isEmpty()) return@combine emptyMap()
            val appByKey = apps.orEmpty().associateBy { it.key.toString() }
            val shortcutByKey = shortcuts.associateBy { it.key }
            buildMap {
                targets.forEach { (owner, target) ->
                    val resolved =
                        when (target) {
                            is GestureTarget.App -> appByKey[target.appKey]?.let(SwipeTarget::App)
                            is GestureTarget.Shortcut -> shortcutByKey[target.key]?.let(SwipeTarget::Shortcut)
                        }
                    if (resolved != null) put(owner, resolved)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Lo que suena o lo último que sonó; `null` sin sesiones o sin acceso a notificaciones. */
    val player: StateFlow<NowPlaying?> = mediaSessions.playing

    /** Notificaciones sin leer por paquete, para los puntos del dock. */
    val unread: StateFlow<Map<String, Int>> = notificationAccess.unread

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

    // Agrupado aparte porque `combine` solo admite cinco flujos con tipo. Lleva también el pulso de
    // `nowTick`: cada emisión, aunque repita valores, hace recalcular la sugerencia.
    private val bluetoothInput =
        combine(bluetoothAudio.connected, history.bluetoothOpenings, nowTick) { connected, openings, _ ->
            connected to openings
        }

    /**
     * La sugerencia de Ahora con su app, o `null` si ninguna regla tiene nada que proponer. Se
     * recalcula también al conectar o desconectar un dispositivo de audio Bluetooth. La de Bluetooth
     * no caduca sola con el Inicio a la vista: se retira en el siguiente recálculo.
     */
    val now: StateFlow<Pair<NowSuggestion, AppEntry>?> =
        combine(appsRepository.apps, history.openings, homeConfig.dock, nowFeedback.feedback, bluetoothInput) {
                apps,
                openings,
                slots,
                feedback,
                (bluetooth, bluetoothOpenings)
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
                    bluetooth = bluetooth,
                    bluetoothOpenings = bluetoothOpenings,
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

    /** Abre el acceso directo y lo apunta en el historial, igual que desde la barra. */
    fun launch(shortcut: ShortcutEntry): Boolean {
        val launched = appsRepository.launch(shortcut)
        if (launched) history.record(LaunchHistory.TYPE_SHORTCUT, shortcut.key)
        return launched
    }

    fun launch(target: SwipeTarget): Boolean = when (target) {
        is SwipeTarget.App -> launch(target.app)
        is SwipeTarget.Shortcut -> launch(target.shortcut)
    }

    /** Fija el destino secundario del icono de [app]; `null` lo quita. */
    fun setSwipeTarget(app: AppEntry, target: SwipeTarget?) {
        val owner = app.key.toString()
        when (target) {
            null -> iconGestures.clear(owner)

            is SwipeTarget.App -> iconGestures.set(owner, GestureTarget.App(target.app.key.toString()))

            is SwipeTarget.Shortcut -> {
                val shortcut = target.shortcut
                // La serie de usuario es la de la app dueña del acceso directo, no la del icono.
                val serial = shortcut.app.key.userSerial
                iconGestures.set(owner, GestureTarget.Shortcut(shortcut.info.`package`, shortcut.info.id, serial))
            }
        }
    }

    /** Tiempo de uso de hoy, o `null` sin acceso a uso o si la app es de otro perfil. */
    suspend fun usageToday(app: AppEntry): Long? = // El sistema solo da el uso del perfil en el que corre Alisio.
        if (app.user == Process.myUserHandle()) usageAccess.todayMillis(app.component.packageName) else null

    fun openAppInfo(app: AppEntry): Boolean = appsRepository.openDetails(app)

    suspend fun shortcutIcon(shortcut: ShortcutEntry): Bitmap? = appsRepository.shortcutIcon(shortcut)

    fun togglePlayback() = mediaSessions.toggle()

    fun skipToNext() = mediaSessions.skipToNext()

    /** Para una app que suena pero no tiene icono de inicio en la lista. */
    fun openPlayerApp(): Boolean = mediaSessions.openApp()

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
                    container.iconGestures,
                    container.launchHistory,
                    container.nowFeedback,
                    container.bluetoothAudio,
                    container.notificationAccess,
                    container.mediaSessions,
                    container.usageAccess,
                    container.clock
                )
            }
        }
    }
}
