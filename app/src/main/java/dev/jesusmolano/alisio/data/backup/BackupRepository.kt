package dev.jesusmolano.alisio.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.jesusmolano.alisio.core.backup.Backup
import dev.jesusmolano.alisio.core.backup.BackupCustom
import dev.jesusmolano.alisio.core.backup.BackupException
import dev.jesusmolano.alisio.core.backup.BackupGesture
import dev.jesusmolano.alisio.core.backup.BackupPin
import dev.jesusmolano.alisio.core.backup.BackupSlot
import dev.jesusmolano.alisio.core.backup.BackupStackItem
import dev.jesusmolano.alisio.core.backup.BackupZone
import dev.jesusmolano.alisio.core.backup.decodeBackup
import dev.jesusmolano.alisio.core.backup.encodeBackup
import dev.jesusmolano.alisio.core.backup.missingApps
import dev.jesusmolano.alisio.core.icons.CustomIcon
import dev.jesusmolano.alisio.data.apps.AppsRepository
import dev.jesusmolano.alisio.data.apps.CustomImages
import dev.jesusmolano.alisio.data.db.AppCustom
import dev.jesusmolano.alisio.data.db.BackupDao
import dev.jesusmolano.alisio.data.db.DockSlot
import dev.jesusmolano.alisio.data.db.IconGesture
import dev.jesusmolano.alisio.data.db.StackItem
import dev.jesusmolano.alisio.data.db.ZoneAssignment
import dev.jesusmolano.alisio.data.db.ZoneRow
import dev.jesusmolano.alisio.data.settings.AlisioSettings
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lo que ha pasado al cargar una copia: las apps que faltan (paquete y nombre) y cómo deshacerlo. */
class BackupLoaded(val missing: List<Pair<String, String>>, val undo: suspend () -> Unit)

/**
 * La configuración de Alisio en un archivo elegido con el selector de documentos del sistema
 * (docs/IDEAS.md, A16): `allowBackup` está apagado y reinstalar perdía el dock, las pilas, las islas
 * y los gestos. Sin red ni permisos: el sistema da acceso al archivo elegido y a nada más.
 */
class BackupRepository(
    context: Context,
    private val dao: BackupDao,
    private val settings: AlisioSettings,
    private val images: CustomImages,
    private val apps: AppsRepository
) {
    private val resolver = context.applicationContext.contentResolver

    /** Escribe la copia en [uri]. Devuelve `false` si no se ha podido. */
    suspend fun export(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val text = encodeBackup(current())
            resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: return@withContext false
            true
        } catch (e: IOException) {
            Log.w(TAG, "No se ha podido escribir la copia", e)
            false
        } catch (e: RuntimeException) {
            // SecurityException si el permiso del selector ya no vale.
            Log.w(TAG, "No se ha podido abrir el archivo de la copia", e)
            false
        }
    }

    /**
     * Carga la copia de [uri] y sustituye la configuración entera. Devuelve `null` si el archivo no
     * se puede leer o no es una copia de Alisio; entonces no se ha cambiado nada.
     */
    suspend fun import(uri: Uri): BackupLoaded? = withContext(Dispatchers.IO) {
        val backup =
            try {
                val bytes = resolver.openInputStream(uri)?.use { it.readAtMost(MAX_BACKUP_BYTES) }
                if (bytes == null) {
                    Log.w(TAG, "La copia no se puede abrir o es demasiado grande")
                    return@withContext null
                }
                decodeBackup(bytes.decodeToString())
            } catch (e: IOException) {
                Log.w(TAG, "No se ha podido leer la copia", e)
                return@withContext null
            } catch (e: BackupException) {
                Log.w(TAG, "El archivo no es una copia válida: ${e.message}")
                return@withContext null
            } catch (e: RuntimeException) {
                Log.w(TAG, "No se ha podido abrir el archivo de la copia", e)
                return@withContext null
            }
        // Lo de ahora, en memoria: «Deshacer» lo vuelve a poner tal cual, también los accesos fijados.
        val before = current()
        if (!applySafely(backup, exactPins = false)) {
            // A medias (disco lleno a mitad de camino) la configuración no sería ni una ni otra.
            applySafely(before, exactPins = true)
            return@withContext null
        }
        val installed = apps.apps.value.orEmpty().mapTo(HashSet()) { it.component.packageName }
        BackupLoaded(missingApps(backup, installed)) {
            withContext(Dispatchers.IO) { applySafely(before, exactPins = true) }
        }
    }

    /** [apply] sin que un fallo de escritura (`SQLiteFullException`, por ejemplo) cierre Alisio. */
    private suspend fun applySafely(backup: Backup, exactPins: Boolean): Boolean = try {
        apply(backup, exactPins)
    } catch (e: RuntimeException) {
        Log.w(TAG, "No se ha podido aplicar la configuración", e)
        false
    }

    private suspend fun current(): Backup {
        val slots = dao.slots()
        val custom = dao.custom()
        // Las imágenes que de verdad se usan: las de las apps y la de cada pila.
        val imageNames =
            custom.mapNotNull { (CustomIcon.parse(it.icon) as? CustomIcon.Image)?.file } +
                slots.mapNotNull { it.stackImage }
        val stackItems = dao.stackItems()
        val gestures = dao.gestures()
        val assignments = dao.assignments()
        val pins = apps.pinnedShortcuts().map { (packageName, id, serial) -> BackupPin(packageName, id, serial) }
        val backup =
            Backup(
                prefs = settings.snapshot(),
                dock = slots.map {
                    BackupSlot(it.position, it.appKey, it.stackName, it.stackIcon, it.stackGlyph, it.stackImage)
                },
                stacks = stackItems.map { BackupStackItem(it.slot, it.appKey, it.position) },
                gestures = gestures.map {
                    BackupGesture(it.appKey, it.targetAppKey, it.shortcutPackage, it.shortcutId, it.shortcutUser)
                },
                zones = dao.zones().map { BackupZone(it.id, it.name, it.position) },
                assignments = assignments.associate { it.appKey to it.zone },
                custom = custom.map { BackupCustom(it.appKey, it.label, it.icon, it.hidden) },
                images = imageNames.distinct().mapNotNull { name -> images.bytes(name)?.let { name to it } }.toMap(),
                pins = pins
            )
        // El nombre de cada app que se menciona, para que otro teléfono pueda decir cuáles faltan.
        val mentioned = backup.packages()
        val labels =
            apps.apps.value.orEmpty()
                .filter { it.component.packageName in mentioned }
                .associate { it.component.packageName to it.originalLabel }
        return backup.copy(labels = labels)
    }

    /**
     * Sustituye la configuración por la de [backup]. Con [exactPins] los accesos fijados quedan
     * justo los de la copia (para deshacer una carga); sin él, se suman a los que ya hubiera.
     */
    private suspend fun apply(backup: Backup, exactPins: Boolean): Boolean {
        // Las imágenes y la base van antes que las preferencias: si algo falla, se escribe lo de
        // antes encima y unas preferencias a medias no dejan el Inicio con otro aspecto.
        backup.images.forEach { (name, bytes) -> images.restore(name, bytes) }
        dao.replaceAll(
            slots = backup.dock.map {
                DockSlot(it.position, it.appKey, it.stackName, it.stackIcon, it.stackGlyph, it.stackImage)
            },
            stackItems = backup.stacks.map { StackItem(it.slot, it.key, it.position) },
            gestures = backup.gestures.map {
                IconGesture(it.appKey, it.targetAppKey, it.shortcutPackage, it.shortcutId, it.shortcutUser)
            },
            zones = backup.zones.map { ZoneRow(it.id, it.name, it.position) },
            assignments = backup.assignments.map { (app, zone) -> ZoneAssignment(app, zone) },
            custom = backup.custom.map { AppCustom(it.appKey, it.label, it.icon, it.hidden) }
        )
        if (!settings.restore(backup.prefs)) return false
        withContext(Dispatchers.Main) {
            val pins = backup.pins.map { Triple(it.packageName, it.id, it.userSerial) }
            if (exactPins) apps.setPinned(pins) else apps.repin(pins)
            // Las imágenes recién escritas y el estilo de iconos de la copia se ven al volver a dibujar.
            apps.reload()
        }
        return true
    }

    private companion object {
        const val TAG = "BackupRepository"

        // Una copia con imágenes propias pesa unos cientos de KB: un archivo mucho mayor no es una
        // copia, y leerlo entero en memoria podría cerrar Alisio.
        const val MAX_BACKUP_BYTES = 32 * 1024 * 1024

        /** Todo el contenido, o `null` si pasa de [limit] bytes. */
        fun InputStream.readAtMost(limit: Int): ByteArray? {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = read(buffer)
                if (read < 0) return out.toByteArray()
                out.write(buffer, 0, read)
                if (out.size() > limit) return null
            }
        }
    }
}
