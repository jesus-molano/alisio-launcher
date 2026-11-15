package dev.jesusmolano.alisio.feature.people

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.net.toUri
import dev.jesusmolano.alisio.core.people.Channel
import dev.jesusmolano.alisio.data.contacts.Person
import dev.jesusmolano.alisio.data.contacts.whatsappMimeType

private const val TAG = "Contact"
const val WHATSAPP_PACKAGE = "com.whatsapp"

/**
 * Contacta con la persona por ese canal (§5.4). Devuelve `false` si no hay quien atienda el intent.
 *
 * - Llamada: marca directamente con el permiso `CALL_PHONE`; sin él, o si [direct] es `false`,
 *   abre el marcador con el número puesto.
 * - WhatsApp: abre la fila que WhatsApp dejó en el contacto. Si no hay fila de chat, usa `wa.me`.
 */
internal fun Context.contact(person: Person, channel: Channel, direct: Boolean = true): Boolean {
    val phone = person.phone
    return when (channel) {
        Channel.CALL -> {
            if (phone == null) return false
            val canCall =
                direct && checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
            start(Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)))
        }

        Channel.SMS -> phone != null && start(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null)))

        Channel.WHATSAPP_CHAT, Channel.WHATSAPP_CALL, Channel.WHATSAPP_VIDEO -> {
            val row = person.whatsappRows[channel]
            val opened =
                row != null &&
                    start(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(
                                Uri.withAppendedPath(ContactsContract.Data.CONTENT_URI, row.toString()),
                                whatsappMimeType(channel)
                            )
                            .setPackage(WHATSAPP_PACKAGE)
                    )
            opened || (channel == Channel.WHATSAPP_CHAT && phone != null && start(waMe(phone)))
        }
    }
}

/** `wa.me` solo admite cifras; un número guardado sin prefijo de país puede no encontrar el chat. */
private fun waMe(phone: String): Intent =
    Intent(Intent.ACTION_VIEW, "https://wa.me/${phone.filter(Char::isDigit)}".toUri()).setPackage(WHATSAPP_PACKAGE)

private fun Context.start(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: RuntimeException) {
    // ActivityNotFoundException o SecurityException: no hay quien lo abra o falta el permiso.
    Log.w(TAG, "No se ha podido contactar: ${intent.action}", e)
    false
}
