package dev.jesusmolano.alisio.feature.home

import android.graphics.Bitmap
import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jesusmolano.alisio.AppContainer
import dev.jesusmolano.alisio.core.dock.MAX_DOCK_SLOTS
import dev.jesusmolano.alisio.core.dock.mostUsed
import dev.jesusmolano.alisio.core.glance.Place
import dev.jesusmolano.alisio.core.icons.CustomIcon
import dev.jesusmolano.alisio.core.icons.IconStyle
import dev.jesusmolano.alisio.core.icons.customLabel
import dev.jesusmolano.alisio.core.media.PlayerSeen
import dev.jesusmolano.alisio.core.media.hiddenAfter
import dev.jesusmolano.alisio.core.media.musicKey
import dev.jesusmolano.alisio.core.now.ConnectedAudio
import dev.jesusmolano.alisio.core.now.DISMISSALS_TO_SILENCE
import dev.jesusmolano.alisio.core.now.NowApp
import dev.jesusmolano.alisio.core.now.NowFeedback
import dev.jesusmolano.alisio.core.now.NowInput
import dev.jesusmolano.alisio.core.now.NowMusic
import dev.jesusmolano.alisio.core.now.NowRule
import dev.jesusmolano.alisio.core.now.NowSuggestion
import dev.jesusmolano.alisio.core.now.bluetoothCases
import dev.jesusmolano.alisio.core.now.suggest
import dev.jesusmolano.alisio.core.people.Channel
import dev.jesusmolano.alisio.core.people.channelKey
import dev.jesusmolano.alisio.core.people.forgetChannels
import dev.jesusmolano.alisio.core.people.openingsByPerson
import dev.jesusmolano.alisio.core.people.orderedChannels
import dev.jesusmolano.alisio.core.search.Usage
import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.core.zones.ZoneId as IslandId
import dev.jesusmolano.alisio.data.apps.AppCustomRepository
import dev.jesusmolano.alisio.data.apps.AppEntry
import dev.jesusmolano.alisio.data.apps.AppsRepository
import dev.jesusmolano.alisio.data.apps.CustomImages
import dev.jesusmolano.alisio.data.apps.IconPackCatalog
import dev.jesusmolano.alisio.data.apps.IconPackInfo
import dev.jesusmolano.alisio.data.apps.PackIcon
import dev.jesusmolano.alisio.data.apps.ShortcutEntry
import dev.jesusmolano.alisio.data.bluetooth.BluetoothAudio
import dev.jesusmolano.alisio.data.contacts.ContactPhotos
import dev.jesusmolano.alisio.data.contacts.ContactsRepository
import dev.jesusmolano.alisio.data.contacts.Person
import dev.jesusmolano.alisio.data.db.Forgotten
import dev.jesusmolano.alisio.data.db.HomeNote
import dev.jesusmolano.alisio.data.glance.GlanceData
import dev.jesusmolano.alisio.data.glance.GlanceRepository
import dev.jesusmolano.alisio.data.history.LaunchHistory
import dev.jesusmolano.alisio.data.home.GestureTarget
import dev.jesusmolano.alisio.data.home.HomeConfigRepository
import dev.jesusmolano.alisio.data.home.IconGestureRepository
import dev.jesusmolano.alisio.data.home.SlotConfig
import dev.jesusmolano.alisio.data.home.StackIcon
import dev.jesusmolano.alisio.data.home.SwipeTarget
import dev.jesusmolano.alisio.data.media.LiveNow
import dev.jesusmolano.alisio.data.media.MediaSessions
import dev.jesusmolano.alisio.data.media.MusicStarter
import dev.jesusmolano.alisio.data.media.NotificationAccess
import dev.jesusmolano.alisio.data.media.NowPlaying
import dev.jesusmolano.alisio.data.notes.NotesRepository
import dev.jesusmolano.alisio.data.now.ForgottenRepository
import dev.jesusmolano.alisio.data.now.NowFeedbackRepository
import dev.jesusmolano.alisio.data.pause.LaunchPause
import dev.jesusmolano.alisio.data.usage.UsageAccess
import dev.jesusmolano.alisio.data.zones.ZonesRepository
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Hueco del dock listo para pintar: las claves ya son apps instaladas. */
sealed interface DockSlot {
    data object Empty : DockSlot

    class App(val app: AppEntry) : DockSlot

    /**
     * @param image la imagen elegida para la pila, ya leída; `null` si no tiene o ya no está.
     * @param shortcuts los accesos directos fijados en ella desde otra app («Añadir a inicio»).
     */
    class Stack(
        val name: String,
        val apps: List<AppEntry>,
        val icon: StackIcon,
        val glyph: String,
        val image: Bitmap? = null,
        val shortcuts: List<ShortcutEntry> = emptyList()
    ) : DockSlot {
        /** Sin nada que abrir: tocarla abre su hoja de edición. */
        val isEmpty: Boolean get() = apps.isEmpty() && shortcuts.isEmpty()
    }
}

/**
 * Lo que ha pasado al descartar una sugerencia de Ahora.
 *
 * @param silenced con este descarte la sugerencia no vuelve a salir; si no, solo se oculta unas horas.
 */
class NowDismissal(val silenced: Boolean, val undo: () -> Unit)

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
    private val notificationAccess: NotificationAccess,
    private val mediaSessions: MediaSessions,
    private val usageAccess: UsageAccess,
    private val pause: LaunchPause,
    private val clock: Clock,
    private val glanceSource: GlanceRepository,
    private val appCustom: AppCustomRepository,
    private val images: CustomImages,
    private val iconPacks: IconPackCatalog,
    zones: ZonesRepository,
    private val forgotten: ForgottenRepository,
    private val contacts: ContactsRepository,
    private val notesSource: NotesRepository,
    private val music: MusicStarter
) : ViewModel() {
    /** La app de música por defecto; la usan la pieza «Música» y la sugerencia «Reanudar». */
    val musicApp: StateFlow<AppEntry?> = music.app

    /** La última carátula guardada de cada app de música: la lleva la sugerencia «Reanudar». */
    val musicArt: StateFlow<Map<String, Bitmap>> = music.lastArt

    /** Si suena algo, de la app que sea: la pieza «Música» solo sale cuando no. */
    val somethingPlaying: StateFlow<Boolean> =
        mediaSessions.playingApps.map { it.isNotEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Pone la app de música sin abrirla ([MusicStarter.resume]); si en 1,5 s no suena, la abre como
     * cualquier otra app (con su pausa y su apertura en el historial). [onFailed] avisa si ni eso.
     *
     * Mientras espera, otro toque no hace nada: si no, saldrían dos órdenes y dos aperturas. La app
     * solo se abre si el Inicio sigue a la vista ([stillShown]): si el dueño se ha ido a otra app en
     * ese tiempo, la de música no debe ponerse delante.
     */
    fun playMusic(app: AppEntry, stillShown: () -> Boolean, onFailed: () -> Unit) {
        if (musicJob?.isActive == true) return
        musicJob =
            viewModelScope.launch {
                if (!music.resume(app.component.packageName) && stillShown() && !launch(app)) onFailed()
            }
    }

    private var musicJob: Job? = null

    /** Las notas del Inicio, la más reciente primero. */
    val notes: StateFlow<List<HomeNote>> = notesSource.notes

    /** Tacha una nota. Devuelve cómo deshacerlo. */
    fun removeNote(note: HomeNote): () -> Unit = notesSource.remove(note)

    /** La agenda: las iniciales dibujadas y la elección de favoritas en los ajustes las encuentran. */
    val people: StateFlow<List<Person>> = contacts.people

    /** Si hay permiso para leer la agenda; sin él no hay caras favoritas ni iniciales de personas. */
    val contactsGranted: StateFlow<Boolean> = contacts.granted

    val photos: ContactPhotos = contacts.photos

    /** Cuándo acaba cada cuenta atrás en curso, por paquete: el reloj del dock la enseña. */
    val timers: StateFlow<Map<String, Long>> = notificationAccess.timers

    /**
     * Cuánto se usa cada app y cada persona ahora mismo (frecencia, §5.3), para elegir qué abre una
     * inicial dibujada. Se calcula al pedirlo: solo hace falta al acabar un trazo.
     */
    fun usageNow(): Map<String, Double> {
        val openings = history.openings.value
        val channels = forgetChannels(openings, forgotten.marksOf(Forgotten.KIND_CHANNEL))
        return Usage.from(channels + openingsByPerson(openings), clock.now(), ZoneId.systemDefault()).frecency
    }

    /** Los canales de [person] que se pueden usar, el principal primero, según lo aprendido. */
    fun channelsOf(person: Person): List<Channel> {
        // Sin ninguna fila de WhatsApp en la agenda es que WhatsApp ya no las escribe (§9): se ofrece igual.
        val noRows = contacts.people.value.none { it.whatsappRows.isNotEmpty() }
        return orderedChannels(person.lookupKey, person.channels(noRows), usageNow())
    }

    /** Apunta el canal usado, igual que desde la barra: de ahí sale el canal aprendido. */
    fun recordContact(person: Person, channel: Channel) =
        history.record(LaunchHistory.TYPE_CONTACT, channelKey(person.lookupKey, channel))

    /** Lo que está en curso (docs/IDEAS.md §2.7), si «En curso» está encendido y hay algo. */
    val live: StateFlow<LiveNow?> = notificationAccess.live

    // Por app, la última vez que el sistema la vio en primer plano: se lee al volver al Inicio y
    // solo vive en memoria (docs/IDEAS.md §3, P4).
    private val seenAt = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** Lo que puede decir la línea bajo la hora; qué se enseña lo decide el Inicio con la hora de cada minuto. */
    val glance: StateFlow<GlanceData> = glanceSource.data

    /** Ciudades que se llaman así, para fijar el tiempo; `null` sin red. */
    suspend fun searchPlaces(name: String, language: String): List<Place>? = glanceSource.searchPlaces(name, language)

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

    // Lo último visto con el reproductor apartado a mano; `null` si está a la vista. Solo en memoria:
    // apartarlo es cosa del momento, no una preferencia.
    private val playerHidden = MutableStateFlow<PlayerSeen?>(null)

    /**
     * Lo que suena o lo último que sonó; `null` sin sesiones, sin acceso a notificaciones o si se ha
     * apartado con [hidePlayer] y nada ha vuelto a sonar ([hiddenAfter]).
     */
    val player: StateFlow<NowPlaying?> =
        combine(mediaSessions.playing, playerHidden) { playing, hidden ->
            playing.takeIf { hiddenAfter(hidden, it?.seen()) == null }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Las apps con alguna sugerencia de Ahora silenciada para siempre, para poder devolverlas. */
    val silencedSuggestions: StateFlow<List<AppEntry>> =
        combine(appsRepository.apps, nowFeedback.feedback) { apps, feedback ->
            val silenced =
                feedback.filterValues { it.dismissals >= DISMISSALS_TO_SILENCE }.keys.mapTo(HashSet()) { it.second }
            apps.orEmpty().filter { it.key.toString() in silenced }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Las apps más abiertas, para rellenar de un toque un dock vacío. Vacía sin historial: recién
     * instalado no hay nada que proponer. Una app oculta no se propone.
     */
    val dockSuggestions: StateFlow<List<AppEntry>> =
        combine(appsRepository.apps, history.openings) { apps, openings ->
            val byKey = apps.orEmpty().filterNot {
                it.hidden || it.hideFromSuggestions
            }.associateBy { it.key.toString() }
            mostUsed(openings, byKey.keys, MAX_DOCK_SLOTS).mapNotNull(byKey::get)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // El recuerdo de lo oculto avanza con cada cambio de sesión: así «pausa y vuelve a sonar» se
        // distingue de «sigue sonando».
        viewModelScope.launch {
            mediaSessions.playing.collect { playing -> playerHidden.update { hiddenAfter(it, playing?.seen()) } }
        }
    }

    /** Notificaciones sin leer por paquete, para los puntos del dock. */
    val unread: StateFlow<Map<String, Int>> = notificationAccess.unread

    /** `null` mientras se carga la primera vez. */
    val apps: StateFlow<List<AppEntry>?> = appsRepository.apps

    /**
     * `null` hasta tener las apps y la configuración: pintar antes enseñaría huecos vacíos un instante.
     * Una app desinstalada deja su hueco vacío y desaparece de su pila, sin tocar lo guardado.
     */
    // Solo los fijados, que son los que puede haber en una pila, y solo cuando cambian: los demás
    // accesos directos cambian con cada mensaje de una app de chat y no deben repintar el dock.
    private val pinnedShortcuts =
        appsRepository.shortcuts
            .map { all -> all.filter { it.info.isPinned } }
            .distinctUntilChanged { old, new -> old.map { it.key to it.label } == new.map { it.key to it.label } }

    val dock: StateFlow<List<DockSlot>?> =
        combine(
            appsRepository.apps,
            pinnedShortcuts,
            homeConfig.dock,
            ::resolveSlots
        ).flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Los ocho huecos, también los que el número elegido oculta: para la vista previa de cada número. */
    val allSlots: StateFlow<List<DockSlot>?> =
        combine(appsRepository.apps, pinnedShortcuts, homeConfig.allSlots, ::resolveSlots)
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Fuera del hilo principal: la imagen de una pila se lee del disco la primera vez. */
    private fun resolveSlots(
        apps: List<AppEntry>?,
        shortcuts: List<ShortcutEntry>,
        slots: List<SlotConfig>?
    ): List<DockSlot>? {
        if (apps == null || slots == null) return null
        val byKey = apps.associateBy { it.key.toString() }
        val shortcutByKey = shortcuts.associateBy { it.key }
        return slots.map { slot ->
            when (slot) {
                SlotConfig.Empty -> DockSlot.Empty

                is SlotConfig.App -> byKey[slot.appKey]?.let(DockSlot::App) ?: DockSlot.Empty

                is SlotConfig.Stack ->
                    DockSlot.Stack(
                        name = slot.name,
                        apps = slot.appKeys.mapNotNull(byKey::get),
                        icon = slot.icon,
                        glyph = slot.glyph,
                        image = slot.image?.let(images::load),
                        // Uno que su app ya no publica no sale, pero sigue guardado por si vuelve.
                        shortcuts = slot.appKeys.mapNotNull(shortcutByKey::get)
                    )
            }
        }
    }

    // Sube cada vez que el Inicio vuelve a verse: la sugerencia depende de la hora y hay que recalcularla.
    private val nowTick = MutableStateFlow(0)

    // Agrupado aparte porque `combine` solo admite cinco flujos con tipo. Lleva también el pulso de
    // `nowTick`: cada emisión, aunque repita valores, hace recalcular la sugerencia. Los casos de
    // Bluetooth se cuentan aquí para dejar fuera antes lo que se haya olvidado de cada app.
    private val bluetoothInput =
        combine(bluetoothAudio.connected, history.bluetoothOpenings, forgotten.marks, nowTick) {
                connected,
                openings,
                marks,
                _
            ->
            val kept =
                openings.filter { opening ->
                    val since = marks[NowRule.BLUETOOTH.id to opening.key]
                    since == null || opening.at >= since
                }
            connected to bluetoothCases(kept)
        }

    // Lo que no es el historial ni el dock: las apps de Dev, lo olvidado y lo visto fuera de Alisio.
    private val learnedInput =
        combine(zones.assignments, forgotten.marks, seenAt) { assignments, marks, seen ->
            NowExtras(
                devKeys = assignments.orEmpty().filterValues { it == IslandId.Dev }.keys,
                forgotten =
                    marks.mapNotNull { (id, since) -> NowRule.parse(id.first)?.let { (it to id.second) to since } }
                        .toMap(),
                seenAt = seen
            )
        }

    private class NowExtras(
        val devKeys: Set<String>,
        val forgotten: Map<Pair<NowRule, String>, Long>,
        val seenAt: Map<String, Long>
    )

    // La app de música y lo que suena, para la regla de música; sus horas de empezar a sonar están
    // en el historial y se leen con él.
    private val musicInput =
        combine(
            musicApp,
            mediaSessions.playingApps,
            music.lastPlayedAt,
            mediaSessions.watching
        ) { app, playing, lastPlayed, watching ->
            app?.let { MusicNow(it, playing.isNotEmpty(), lastPlayed, watching) }
        }

    private class MusicNow(val app: AppEntry, val playing: Boolean, val lastPlayedAt: Long?, val watching: Boolean)

    private val nowInputs =
        combine(
            homeConfig.dock,
            nowFeedback.feedback,
            bluetoothInput,
            learnedInput,
            musicInput
        ) { slots, feedback, bt, extras, musicNow ->
            NowInputs(slots, feedback, bt.first, bt.second, extras, musicNow)
        }

    private class NowInputs(
        val slots: List<SlotConfig>?,
        val feedback: Map<Pair<NowRule, String>, NowFeedback>,
        val bluetooth: ConnectedAudio?,
        val bluetoothOpenings: Map<String, Map<String, Int>>,
        val extras: NowExtras,
        val music: MusicNow?
    )

    /**
     * La sugerencia de Ahora con su app, o `null` si ninguna regla tiene nada que proponer. Se
     * recalcula también al conectar o desconectar un dispositivo de audio Bluetooth. La de Bluetooth
     * no caduca sola con el Inicio a la vista: se retira en el siguiente recálculo.
     */
    val now: StateFlow<Pair<NowSuggestion, AppEntry>?> =
        combine(appsRepository.apps, history.openings, nowInputs) { apps, openings, inputs ->
            val slots = inputs.slots
            if (apps == null || slots == null) return@combine null
            // Una app oculta no se sugiere: Ahora la sacaría a la vista sin que nadie la busque. Tampoco
            // una en pausa por Bienestar digital ni una que el sistema pide no sugerir (docs/IDEAS.md §2.8).
            val shown = apps.filterNot { it.hidden || it.suspended || it.hideFromSuggestions }
            val byKey = shown.associateBy { it.key.toString() }
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
                    apps = shown.map { NowApp(it.key.toString(), it.updatedAt) },
                    openings = openings,
                    dockKeys = dockKeys,
                    bluetooth = inputs.bluetooth,
                    bluetoothOpenings = inputs.bluetoothOpenings,
                    feedback = inputs.feedback,
                    newVersionKeys = inputs.extras.devKeys,
                    seenAt = inputs.extras.seenAt,
                    forgotten = inputs.extras.forgotten,
                    music =
                        inputs.music?.let {
                            NowMusic(
                                appKey = it.app.key.toString(),
                                starts = openings[musicKey(it.app.component.packageName)].orEmpty(),
                                playing = it.playing,
                                lastPlayedAt = it.lastPlayedAt,
                                watching = it.watching
                            )
                        }
                )
            suggest(input)?.let { suggestion -> byKey[suggestion.appKey]?.let { suggestion to it } }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * El Inicio vuelve a estar a la vista: se recalcula la sugerencia para la hora de ahora, el
     * historial mueve su límite de 90 días y se mira qué se ha abierto fuera de Alisio mientras tanto.
     */
    fun refreshNow() {
        history.refresh()
        nowTick.value++
        viewModelScope.launch {
            val byPackage = usageAccess.lastResumed(clock.now() - SEEN_WINDOW_MILLIS)
            val apps = appsRepository.apps.value.orEmpty()
            seenAt.value =
                apps.mapNotNull { app ->
                    byPackage[app.component.packageName]?.let { app.key.toString() to it }
                }.toMap()
        }
    }

    /**
     * Olvida lo que ha llevado a sugerir [suggestion] (docs/IDEAS.md §2.6): la costumbre o el
     * dispositivo dejan de contar hasta que se vuelvan a aprender. Devuelve cómo deshacerlo.
     */
    fun forgetNow(suggestion: NowSuggestion): () -> Unit = forgotten.forget(suggestion.rule.id, suggestion.appKey)

    /** Abre lo que está en curso por su propia pantalla, con la pausa de su app si la lleva. */
    fun openLive(live: LiveNow, label: String, icon: Bitmap?, send: () -> Boolean): Boolean =
        pause.open(live.packageName, label, icon, send)

    /** Descarta la sugerencia y dice qué pasa con ella, para contarlo junto a «Deshacer». */
    fun dismissNow(suggestion: NowSuggestion): NowDismissal {
        val before = nowFeedback.feedback.value[suggestion.rule to suggestion.appKey]?.dismissals ?: 0
        val undo = nowFeedback.dismiss(suggestion.rule, suggestion.appKey)
        return NowDismissal(silenced = before + 1 >= DISMISSALS_TO_SILENCE, undo = undo)
    }

    /**
     * Abre la app y lo apunta en el historial. Devuelve `false` si no se ha podido abrir. Si la app
     * lleva pausa ([LaunchPause.open]), abrir y apuntar esperan a que la pausa se confirme.
     */
    fun launch(app: AppEntry): Boolean = pause.open(app) {
        val launched = appsRepository.launch(app)
        if (launched) history.record(LaunchHistory.TYPE_APP, app.key.toString())
        launched
    }

    /** Abre el acceso directo y lo apunta en el historial, igual que desde la barra. */
    fun launch(shortcut: ShortcutEntry): Boolean = pause.open(shortcut) {
        val launched = appsRepository.launch(shortcut)
        if (launched) history.record(LaunchHistory.TYPE_SHORTCUT, shortcut.key)
        launched
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

    /**
     * Quita un acceso fijado a Alisio. Devuelve cómo deshacerlo: volver a fijarlo, que el sistema
     * solo admite mientras la app lo siga publicando.
     */
    fun unpinShortcut(shortcut: ShortcutEntry): () -> Unit {
        appsRepository.unpin(shortcut)
        val pin = Triple(shortcut.info.`package`, shortcut.info.id, shortcut.app.key.userSerial)
        return { appsRepository.repin(listOf(pin)) }
    }

    suspend fun shortcutIcon(shortcut: ShortcutEntry): Bitmap? = appsRepository.shortcutIcon(shortcut)

    /** Los iconos de [apps] en otro estilo, para la vista previa de cada estilo en los ajustes. */
    suspend fun previewIcons(style: IconStyle, apps: List<AppEntry>): List<Bitmap> =
        appsRepository.previewIcons(style, apps)

    fun togglePlayback() = mediaSessions.toggle()

    fun skipToNext() = mediaSessions.skipToNext()

    /** Aparta el reproductor del Inicio hasta que algo vuelva a sonar. */
    fun hidePlayer() {
        playerHidden.value = mediaSessions.playing.value?.seen()
    }

    private fun NowPlaying.seen() = PlayerSeen(packageName, playing)

    /** Devuelve al motor de Ahora una app silenciada. */
    fun restoreSuggestion(app: AppEntry) = nowFeedback.restore(app.key.toString())

    /** Llena los huecos vacíos del dock con las apps más abiertas. Devuelve cómo deshacerlo. */
    fun fillDock(): () -> Unit = homeConfig.fill(dockSuggestions.value.map { it.key.toString() })

    /** Para una app que suena pero no tiene icono de inicio en la lista. */
    fun openPlayerApp(): Boolean {
        val playing = mediaSessions.playing.value ?: return false
        return pause.open(playing.packageName, playing.appLabel, icon = null) { mediaSessions.openApp() }
    }

    fun setDockApp(position: Int, app: AppEntry) = homeConfig.setApp(position, app.key.toString())

    fun makeDockStack(position: Int, name: String) = homeConfig.makeStack(position, name)

    fun renameDockStack(position: Int, name: String) = homeConfig.renameStack(position, name)

    fun setStackIcon(position: Int, icon: StackIcon, glyph: String) = homeConfig.setStackIcon(position, icon, glyph)

    /** Cambia el icono de [app]; `null` vuelve al suyo. Devuelve cómo deshacerlo. */
    fun setAppIcon(app: AppEntry, icon: CustomIcon?): () -> Unit = appCustom.setIcon(app.key.toString(), icon)

    /** Cambia el nombre de [app] (ver [customLabel]); vacío o el original vuelven al suyo. Devuelve cómo deshacerlo. */
    fun setAppLabel(app: AppEntry, label: String): () -> Unit =
        appCustom.setLabel(app.key.toString(), customLabel(label, app.originalLabel))

    /** Oculta o vuelve a enseñar [app] en Zonas, Trazo, Recientes y Ahora. Devuelve cómo deshacerlo. */
    fun setAppHidden(app: AppEntry, hidden: Boolean): () -> Unit = appCustom.setHidden(app.key.toString(), hidden)

    /** El dibujo de cada paquete de iconos instalado que tiene uno para [app]. */
    suspend fun packIconsFor(app: AppEntry): List<PackIcon> = iconPacks.iconsFor(app.component)

    suspend fun iconPacks(): List<IconPackInfo> = iconPacks.installed()

    /** Todos los dibujos de un paquete, por su nombre en él. */
    suspend fun packDrawables(packageName: String): List<String> = iconPacks.names(packageName)

    suspend fun packIcon(packageName: String, drawable: String): Bitmap? = iconPacks.iconAsync(packageName, drawable)

    fun clearDockSlot(position: Int) = homeConfig.clear(position)

    fun reorderStack(position: Int, apps: List<AppEntry>) =
        homeConfig.reorderStack(position, apps.map { it.key.toString() })

    fun toggleInStack(position: Int, app: AppEntry) = homeConfig.toggleInStack(position, app.key.toString())

    /** Quita de la pila un acceso directo fijado en ella. Sigue fijado: sigue saliendo en la barra. */
    fun removeFromStack(position: Int, shortcut: ShortcutEntry) = homeConfig.toggleInStack(position, shortcut.key)

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
                    container.launchPause,
                    container.clock,
                    container.glance,
                    container.appCustom,
                    container.customImages,
                    container.iconPacks,
                    container.zones,
                    container.forgotten,
                    container.contacts,
                    container.notes,
                    container.music
                )
            }
        }

        // Hacia atrás desde ahora, lo que se mira de fuera de Alisio: basta un día para «hoy», para
        // la versión nueva (24 h) y para la conexión Bluetooth en curso.
        private const val SEEN_WINDOW_MILLIS = 24L * 60 * 60 * 1000
    }
}
