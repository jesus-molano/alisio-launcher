package dev.jesusmolano.alisio.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.util.Log
import dev.jesusmolano.alisio.core.people.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Persona de la agenda con lo necesario para contactarla (§5.4).
 *
 * @param lookupKey clave estable del contacto (`LOOKUP_KEY`); con ella se lleva su historial.
 * @param phone su teléfono principal, o el primero que tenga.
 * @param whatsappRows fila de `ContactsContract.Data` que WhatsApp añade para cada canal suyo.
 */
class Person(val lookupKey: String, val name: String, val phone: String?, val whatsappRows: Map<Channel, Long>) {
    /**
     * Canales disponibles: los de WhatsApp que tengan fila en este contacto, más llamada y SMS si
     * tiene teléfono (§5.4).
     *
     * @param whatsappWithoutRows WhatsApp está instalado pero no ha dejado filas en ningún contacto.
     *   Solo entonces el chat se ofrece a quien tenga teléfono y se abre con `wa.me`; en lo normal,
     *   no tener fila significa que esa persona no usa WhatsApp.
     */
    fun channels(whatsappWithoutRows: Boolean): Set<Channel> = buildSet {
        addAll(whatsappRows.keys)
        if (phone != null) {
            if (whatsappWithoutRows) add(Channel.WHATSAPP_CHAT)
            add(Channel.CALL)
            add(Channel.SMS)
        }
    }
}

/** Índice de la agenda en memoria, al día con sus cambios. Sin permiso de contactos está vacío. */
class ContactsRepository(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext

    private val _people = MutableStateFlow<List<Person>>(emptyList())
    val people: StateFlow<List<Person>> = _people.asStateFlow()

    private val _granted = MutableStateFlow(hasPermission())
    val granted: StateFlow<Boolean> = _granted.asStateFlow()

    private var reloadJob: Job? = null
    private var observing = false

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = reload(debounce = true)
        }

    init {
        onPermissionChanged()
    }

    /** Tras conceder o retirar el permiso: empieza a observar la agenda y la lee, o la vacía. */
    fun onPermissionChanged() {
        val granted = hasPermission()
        _granted.value = granted
        if (!granted) {
            _people.value = emptyList()
            return
        }
        if (!observing) {
            appContext.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            observing = true
        }
        reload(debounce = false)
    }

    private fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Una sincronización de la agenda avisa muchas veces seguidas: con [debounce] se lee una sola vez. */
    private fun reload(debounce: Boolean) {
        reloadJob?.cancel()
        reloadJob =
            scope.launch {
                if (debounce) delay(RELOAD_DEBOUNCE_MILLIS)
                _people.value = withContext(Dispatchers.IO) { load() }
            }
    }

    private fun load(): List<Person> {
        class Draft(val name: String) {
            var phone: String? = null
            var primaryPhone = false
            val whatsapp = HashMap<Channel, Long>()
        }

        val drafts = LinkedHashMap<String, Draft>()
        val mimeTypes = WHATSAPP_MIME_TYPES.keys + Phone.CONTENT_ITEM_TYPE
        val cursor =
            try {
                appContext.contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    PROJECTION,
                    "${ContactsContract.Data.MIMETYPE} IN (${mimeTypes.joinToString { "?" }})",
                    mimeTypes.toTypedArray(),
                    null
                )
            } catch (e: SecurityException) {
                // El permiso se ha retirado entre la comprobación y la consulta.
                Log.w(TAG, "Sin permiso para leer la agenda", e)
                null
            }
        cursor?.use {
            while (it.moveToNext()) {
                val lookupKey = it.getString(COLUMN_LOOKUP) ?: continue
                val name = it.getString(COLUMN_NAME)?.takeIf(String::isNotBlank) ?: continue
                val draft = drafts.getOrPut(lookupKey) { Draft(name) }
                val mimeType = it.getString(COLUMN_MIME)
                val channel = WHATSAPP_MIME_TYPES[mimeType]
                if (channel != null) {
                    draft.whatsapp.putIfAbsent(channel, it.getLong(COLUMN_ID))
                } else {
                    val number = it.getString(COLUMN_DATA)?.takeIf(String::isNotBlank) ?: continue
                    val primary = it.getInt(COLUMN_PRIMARY) != 0
                    if (draft.phone == null || (primary && !draft.primaryPhone)) {
                        draft.phone = number
                        draft.primaryPhone = primary
                    }
                }
            }
        }
        val people =
            drafts
                .map { (lookupKey, draft) -> Person(lookupKey, draft.name, draft.phone, draft.whatsapp) }
                .filter { it.phone != null || it.whatsappRows.isNotEmpty() }
        // Solo recuentos: sirve para saber si WhatsApp sigue dejando sus filas (riesgo de §9).
        Log.i(
            TAG,
            "Agenda: ${people.size} personas, ${people.count {
                it.whatsappRows.isNotEmpty()
            }} con filas de WhatsApp"
        )
        return people
    }

    private companion object {
        const val TAG = "ContactsRepository"
        const val RELOAD_DEBOUNCE_MILLIS = 800L

        val PROJECTION =
            arrayOf(
                ContactsContract.Data._ID,
                ContactsContract.Data.LOOKUP_KEY,
                ContactsContract.Data.DISPLAY_NAME_PRIMARY,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.IS_SUPER_PRIMARY
            )
        const val COLUMN_ID = 0
        const val COLUMN_LOOKUP = 1
        const val COLUMN_NAME = 2
        const val COLUMN_MIME = 3
        const val COLUMN_DATA = 4
        const val COLUMN_PRIMARY = 5
    }
}

/** Tipo MIME de la fila de WhatsApp de un canal, para abrirla con `ACTION_VIEW`. */
fun whatsappMimeType(channel: Channel): String? = WHATSAPP_MIME_TYPES.entries.firstOrNull { it.value == channel }?.key

// Filas que WhatsApp añade a cada contacto: abrirlas con ACTION_VIEW lleva al chat o a la llamada.
private val WHATSAPP_MIME_TYPES =
    mapOf(
        "vnd.android.cursor.item/vnd.com.whatsapp.profile" to Channel.WHATSAPP_CHAT,
        "vnd.android.cursor.item/vnd.com.whatsapp.voip.call" to Channel.WHATSAPP_CALL,
        "vnd.android.cursor.item/vnd.com.whatsapp.video.call" to Channel.WHATSAPP_VIDEO
    )
