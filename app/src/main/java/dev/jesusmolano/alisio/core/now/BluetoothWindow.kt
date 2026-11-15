package dev.jesusmolano.alisio.core.now

/** Cuánto dura «al poco de conectar»: las aperturas de esos minutos son las que cuentan (§5.5). */
const val BLUETOOTH_WINDOW_MILLIS = 5 * 60 * 1000L

/**
 * Dispositivo de audio Bluetooth conectado.
 *
 * @param since cuándo se conectó; si Alisio no estaba vivo en ese momento, cuándo lo vio por primera vez.
 */
data class ConnectedAudio(val name: String, val since: Long)

/** Una apertura hecha al poco de conectar [device], tal como queda en el historial. */
class BluetoothOpening(val device: String, val key: String, val at: Long)

/**
 * El dispositivo que hay que apuntar junto a una apertura hecha en [at]: el conectado, solo si se
 * conectó hace menos de cinco minutos. Pasada la ventana la apertura ya no dice nada del dispositivo.
 */
fun deviceForOpening(connected: ConnectedAudio?, at: Long): String? =
    connected?.name?.takeIf { at - connected.since in 0 until BLUETOOTH_WINDOW_MILLIS }

/**
 * Con varios dispositivos conectados cuenta el último en conectarse: es el que explica lo que se
 * está haciendo ahora (subir al coche con el reloj ya enlazado). En empate, el nombre, para que el
 * resultado no baile.
 */
fun currentAudio(connected: Collection<ConnectedAudio>): ConnectedAudio? =
    connected.maxWithOrNull(compareBy<ConnectedAudio> { it.since }.thenByDescending { it.name })

/**
 * Por dispositivo, en cuántas conexiones se abrió cada app. Abrir la misma app dos veces tras una
 * conexión es un solo caso: el mínimo de la regla pide tres conexiones distintas, no tres toques.
 * Como el historial no guarda cada conexión, dos aperturas de la misma app y dispositivo a menos de
 * una ventana de distancia se toman por la misma.
 */
fun bluetoothCases(openings: List<BluetoothOpening>): Map<String, Map<String, Int>> {
    val cases = HashMap<String, HashMap<String, Int>>()
    openings
        .groupBy { it.device to it.key }
        .forEach { (pair, group) ->
            var count = 0
            var caseStart = Long.MIN_VALUE
            group.map { it.at }.sorted().forEach { at ->
                if (caseStart == Long.MIN_VALUE || at - caseStart >= BLUETOOTH_WINDOW_MILLIS) {
                    count++
                    caseStart = at
                }
            }
            cases.getOrPut(pair.first) { HashMap() }[pair.second] = count
        }
    return cases
}
