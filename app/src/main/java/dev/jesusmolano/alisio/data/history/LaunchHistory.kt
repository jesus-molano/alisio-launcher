package dev.jesusmolano.alisio.data.history

import dev.jesusmolano.alisio.core.now.BluetoothOpening
import dev.jesusmolano.alisio.core.now.ConnectedAudio
import dev.jesusmolano.alisio.core.now.bluetoothCases
import dev.jesusmolano.alisio.core.now.deviceForOpening
import dev.jesusmolano.alisio.core.search.DAY_MILLIS
import dev.jesusmolano.alisio.core.search.HISTORY_DAYS
import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.data.db.LaunchEvent
import dev.jesusmolano.alisio.data.db.LaunchEventDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Historial de aperturas de los últimos 90 días: alimenta el ranking, los recientes de la barra y Ahora.
 *
 * @param connectedAudio el dispositivo de audio Bluetooth conectado en este momento, si lo hay.
 */
class LaunchHistory(
    private val dao: LaunchEventDao,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val connectedAudio: () -> ConnectedAudio? = { null }
) {
    /** Horas de apertura por clave, de la más antigua a la más reciente. */
    val openings: StateFlow<Map<String, List<Long>>> =
        dao.since(clock.now() - HISTORY_DAYS * DAY_MILLIS)
            .map { rows -> rows.groupBy({ it.key }, { it.at }) }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Por dispositivo Bluetooth, en cuántas conexiones se abrió cada app al poco de conectarlo. */
    val bluetoothOpenings: StateFlow<Map<String, Map<String, Int>>> =
        dao.withBluetooth(TYPE_APP, clock.now() - HISTORY_DAYS * DAY_MILLIS)
            .map { rows -> bluetoothCases(rows.map { BluetoothOpening(it.device, it.key, it.at) }) }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    init {
        scope.launch { dao.deleteBefore(clock.now() - HISTORY_DAYS * DAY_MILLIS) }
    }

    fun record(type: String, key: String) {
        val at = clock.now()
        // El dispositivo se decide al abrir, no al guardar: es el que estaba conectado en ese instante.
        // Solo en las aperturas de apps, las únicas que cuenta la regla de Bluetooth: junto a un
        // contacto o una conversación sería un dato guardado sin uso.
        val device = if (type == TYPE_APP) deviceForOpening(connectedAudio(), at) else null
        scope.launch { dao.insert(LaunchEvent(type = type, key = key, at = at, bluetoothDevice = device)) }
    }

    companion object {
        const val TYPE_APP = "app"
        const val TYPE_SHORTCUT = "shortcut"
        const val TYPE_SETTING = "setting"
        const val TYPE_CONTACT = "contact"
    }
}
