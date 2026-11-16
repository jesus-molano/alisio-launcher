package dev.jesusmolano.alisio.data.history

import dev.jesusmolano.alisio.core.now.BluetoothOpening
import dev.jesusmolano.alisio.core.now.ConnectedAudio
import dev.jesusmolano.alisio.core.now.deviceForOpening
import dev.jesusmolano.alisio.core.search.DAY_MILLIS
import dev.jesusmolano.alisio.core.search.HISTORY_DAYS
import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.data.db.LaunchEvent
import dev.jesusmolano.alisio.data.db.LaunchEventDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val HOUR_MILLIS = 60L * 60 * 1000

/**
 * Historial de aperturas de los últimos 90 días: alimenta el ranking, los recientes de la barra y Ahora.
 *
 * @param connectedAudio el dispositivo de audio Bluetooth conectado en este momento, si lo hay.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LaunchHistory(
    private val dao: LaunchEventDao,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val connectedAudio: () -> ConnectedAudio? = { null }
) {
    // Desde cuándo cuenta el historial. Se recalcula en cada [refresh] y no solo al crear el proceso,
    // que con el acceso a notificaciones vive semanas: si no, lo de hace 95 días seguiría contando.
    // Va redondeado a la hora para que volver al Inicio no relea la tabla cada vez.
    private val horizon = MutableStateFlow(horizonAt(clock.now()))

    // Día de la última poda: una vez al día basta, y al arrancar se hace siempre.
    private var prunedDay = -1L

    /** Horas de apertura por clave, de la más antigua a la más reciente. */
    val openings: StateFlow<Map<String, List<Long>>> =
        horizon.flatMapLatest { since -> dao.since(since) }
            .map { rows -> rows.groupBy({ it.key }, { it.at }) }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /**
     * Las aperturas de apps hechas al poco de conectar un dispositivo Bluetooth, tal cual: quien
     * cuenta los casos (`bluetoothCases`) puede dejar fuera antes lo que se ha pedido olvidar.
     */
    val bluetoothOpenings: StateFlow<List<BluetoothOpening>> =
        horizon.flatMapLatest { since -> dao.withBluetooth(TYPE_APP, since) }
            .map { rows -> rows.map { BluetoothOpening(it.device, it.key, it.at) } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
    }

    /** El Inicio vuelve a verse: el límite de 90 días se mueve con el reloj y se poda una vez al día. */
    fun refresh() {
        val now = clock.now()
        horizon.value = horizonAt(now)
        val day = now / DAY_MILLIS
        if (day != prunedDay) {
            prunedDay = day
            scope.launch { dao.deleteBefore(now - HISTORY_DAYS * DAY_MILLIS) }
        }
    }

    fun record(type: String, key: String) {
        val at = clock.now()
        // El dispositivo se decide al abrir, no al guardar: es el que estaba conectado en ese instante.
        // Solo en las aperturas de apps, las únicas que cuenta la regla de Bluetooth: junto a un
        // contacto o una conversación sería un dato guardado sin uso.
        val device = if (type == TYPE_APP) deviceForOpening(connectedAudio(), at) else null
        scope.launch { dao.insert(LaunchEvent(type = type, key = key, at = at, bluetoothDevice = device)) }
    }

    private fun horizonAt(now: Long): Long = (now - HISTORY_DAYS * DAY_MILLIS) / HOUR_MILLIS * HOUR_MILLIS

    companion object {
        const val TYPE_APP = "app"
        const val TYPE_SHORTCUT = "shortcut"
        const val TYPE_SETTING = "setting"
        const val TYPE_CONTACT = "contact"
    }
}
