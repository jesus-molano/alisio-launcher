package dev.jesusmolano.alisio.data.bluetooth

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothLeAudio
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dev.jesusmolano.alisio.core.now.ConnectedAudio
import dev.jesusmolano.alisio.core.now.currentAudio
import dev.jesusmolano.alisio.core.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * El dispositivo de audio Bluetooth conectado ahora, para la regla de Ahora (§5.5). Sin el permiso
 * `BLUETOOTH_CONNECT` no hay ninguno y la regla no aporta nada.
 *
 * No hay servicio propio: solo se entera de lo que pasa mientras el proceso vive. Un dispositivo que
 * ya estaba conectado al arrancar el proceso cuenta como conectado en ese momento, porque el sistema
 * no dice desde cuándo lo está. Todo ocurre en el hilo principal.
 */
class BluetoothAudio(context: Context, private val clock: Clock) {
    private val appContext = context.applicationContext

    private val _granted = MutableStateFlow(hasPermission())
    val granted: StateFlow<Boolean> = _granted.asStateFlow()

    private val _connected = MutableStateFlow<ConnectedAudio?>(null)

    /** Con varios conectados, el último en conectarse ([currentAudio]). */
    val connected: StateFlow<ConnectedAudio?> = _connected.asStateFlow()

    /** Un dispositivo y los perfiles de audio por los que está conectado. */
    private class Link(val audio: ConnectedAudio) {
        val profiles = HashSet<Int>()
    }

    // Por dirección: el mismo dispositivo conecta por varios perfiles y avisa una vez por cada uno.
    private val links = HashMap<String, Link>()
    private var watching = false

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    // Al apagar el Bluetooth no siempre llega la desconexión de cada dispositivo.
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF) links.clear()
                } else {
                    val profile = PROFILE_BY_ACTION[intent.action] ?: return
                    val device =
                        IntentCompat.getParcelableExtra(
                            intent,
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        )
                            ?: return
                    when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)) {
                        BluetoothProfile.STATE_CONNECTED -> add(device, profile)
                        BluetoothProfile.STATE_DISCONNECTED -> remove(device, profile)
                    }
                }
                publish()
            }
        }

    init {
        onPermissionChanged()
    }

    /** Tras conceder el permiso: lee lo que ya está conectado y empieza a escuchar los cambios. */
    fun onPermissionChanged() {
        val granted = hasPermission()
        _granted.value = granted
        if (!granted || watching) return
        // Sin radio Bluetooth (un emulador) no hay nada que mirar.
        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        watching = true

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        PROFILE_BY_ACTION.keys.forEach(filter::addAction)
        // Exportado porque estos avisos los manda la app de Bluetooth del sistema, que no corre con el
        // UID del sistema: a un receptor no exportado no le llegan. Son avisos protegidos, así que
        // ninguna otra app puede falsificarlos.
        ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        val listener =
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    try {
                        proxy.connectedDevices.forEach { add(it, profile) }
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Sin permiso para leer los dispositivos conectados", e)
                    }
                    // Solo hacía falta para la foto inicial; los cambios llegan por el receptor.
                    adapter.closeProfileProxy(profile, proxy)
                    publish()
                }

                override fun onServiceDisconnected(profile: Int) = Unit
            }
        PROFILE_BY_ACTION.values.forEach { profile ->
            try {
                adapter.getProfileProxy(appContext, listener, profile)
            } catch (e: SecurityException) {
                Log.w(TAG, "Sin permiso para consultar el perfil $profile", e)
            }
        }
    }

    private fun add(device: BluetoothDevice, profile: Int) {
        val address = device.address ?: return
        val link =
            links[address] ?: run {
                val name = audioName(device) ?: return
                Link(ConnectedAudio(name, clock.now())).also { links[address] = it }
            }
        link.profiles += profile
    }

    private fun remove(device: BluetoothDevice, profile: Int) {
        val address = device.address ?: return
        val link = links[address] ?: return
        link.profiles -= profile
        if (link.profiles.isEmpty()) links.remove(address)
    }

    private fun publish() {
        _connected.value = currentAudio(links.values.map { it.audio })
    }

    /**
     * El nombre que el usuario le ha puesto al dispositivo o, si no, el suyo. `null` si no cuenta
     * como audio: un reloj enlaza el perfil de manos libres y, al reconectarse a cada rato, taparía
     * al coche o a los cascos.
     */
    private fun audioName(device: BluetoothDevice): String? = try {
        if (device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.WEARABLE) {
            null
        } else {
            (device.alias ?: device.name)?.trim()?.ifEmpty { null }
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "Sin permiso para leer el nombre del dispositivo", e)
        null
    }

    private fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "BluetoothAudio"

        /** Los perfiles de audio y el aviso con el que cada uno anuncia conexiones y desconexiones. */
        val PROFILE_BY_ACTION: Map<String, Int> = buildMap {
            put(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED, BluetoothProfile.A2DP)
            put(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED, BluetoothProfile.HEADSET)
            // Con LE Audio activado, unos auriculares modernos no enlazan A2DP.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                put(BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED, BluetoothProfile.LE_AUDIO)
            }
        }
    }
}
