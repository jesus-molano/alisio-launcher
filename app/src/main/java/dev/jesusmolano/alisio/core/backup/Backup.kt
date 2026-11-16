package dev.jesusmolano.alisio.core.backup

import dev.jesusmolano.alisio.core.json.JsonException
import dev.jesusmolano.alisio.core.json.parseJson
import dev.jesusmolano.alisio.core.json.toJson
import java.util.Base64
import kotlin.reflect.KClass

/**
 * La versión del formato del archivo. Sube cuando un archivo nuevo dejaría de leerse bien en una
 * versión anterior de Alisio; leer uno más antiguo sigue funcionando.
 */
const val BACKUP_VERSION = 1

/** Un valor de las preferencias de DataStore, con su tipo: al cargar tiene que volver con el mismo. */
sealed interface PrefValue {
    data class Bool(val value: Boolean) : PrefValue

    data class Whole(val value: Int) : PrefValue

    data class Big(val value: Long) : PrefValue

    data class Real(val value: Double) : PrefValue

    data class Small(val value: Float) : PrefValue

    data class Text(val value: String) : PrefValue

    data class Texts(val value: Set<String>) : PrefValue
}

/**
 * Las preferencias de una copia que se pueden escribir: las de una clave que Alisio conoce y con el
 * tipo con el que la lee ([expected] da ese tipo, `null` si no la conoce). El archivo declara el
 * tipo de cada valor, pero un archivo editado a mano o de otra versión puede no coincidir, y un tipo
 * distinto en DataStore hace fallar la lectura de todas las preferencias.
 */
fun acceptedPrefs(
    values: Map<String, PrefValue>,
    expected: (String) -> KClass<out PrefValue>?
): Map<String, PrefValue> = values.filter { (name, value) -> expected(name)?.isInstance(value) == true }

/** Un hueco del dock, como en `dock_slot`. */
data class BackupSlot(
    val position: Int,
    val appKey: String?,
    val stackName: String?,
    val stackIcon: String?,
    val stackGlyph: String?,
    val stackImage: String?
)

/** Una app o un acceso directo fijado dentro de una pila, como en `stack_item`. */
data class BackupStackItem(val slot: Int, val key: String, val position: Int)

/** El destino al deslizar sobre el icono de [appKey], como en `icon_gesture`. */
data class BackupGesture(
    val appKey: String,
    val targetAppKey: String?,
    val shortcutPackage: String?,
    val shortcutId: String?,
    val shortcutUser: Long?
)

data class BackupZone(val id: Long, val name: String, val position: Int)

data class BackupCustom(val appKey: String, val label: String?, val icon: String?, val hidden: Boolean)

/** Un acceso directo fijado a Alisio: hay que volver a fijarlo, el sistema no lo copia. */
data class BackupPin(val packageName: String, val id: String, val userSerial: Long)

/**
 * Toda la configuración de Alisio en un archivo (docs/IDEAS.md, A16): preferencias, dock y pilas,
 * gestos, islas, lo cambiado de cada app con sus imágenes, y los accesos fijados. No lleva el
 * historial de aperturas ni nada de la agenda: no es configuración y es lo más personal.
 *
 * @param images el PNG de cada imagen propia, por su nombre de archivo.
 * @param labels el nombre de cada paquete que se menciona, para decir qué apps faltan al cargarlo.
 */
data class Backup(
    val version: Int = BACKUP_VERSION,
    val prefs: Map<String, PrefValue> = emptyMap(),
    val dock: List<BackupSlot> = emptyList(),
    val stacks: List<BackupStackItem> = emptyList(),
    val gestures: List<BackupGesture> = emptyList(),
    val zones: List<BackupZone> = emptyList(),
    val assignments: Map<String, String> = emptyMap(),
    val custom: List<BackupCustom> = emptyList(),
    val images: Map<String, ByteArray> = emptyMap(),
    val labels: Map<String, String> = emptyMap(),
    val pins: List<BackupPin> = emptyList()
) {
    /** Los paquetes de apps que la configuración menciona: el dock, las pilas, los gestos, las islas… */
    fun packages(): Set<String> = buildSet {
        dock.forEach { slot -> slot.appKey?.let { add(packageOf(it)) } }
        stacks.forEach { add(packageOf(it.key)) }
        gestures.forEach { gesture ->
            add(packageOf(gesture.appKey))
            gesture.targetAppKey?.let { add(packageOf(it)) }
            gesture.shortcutPackage?.let(::add)
        }
        assignments.keys.forEach { add(packageOf(it)) }
        custom.forEach { add(packageOf(it.appKey)) }
        pins.forEach { add(it.packageName) }
    }.filterTo(LinkedHashSet()) { it.isNotEmpty() }
}

/** El archivo no es una copia de Alisio, es de una versión más nueva o está dañado. */
class BackupException(message: String) : Exception(message)

/**
 * El paquete de una clave de app (`paquete/actividad#serie`) o de acceso directo
 * (`shortcut:paquete/id#serie`).
 */
fun packageOf(key: String): String = key.removePrefix(SHORTCUT_PREFIX).substringBefore('/')

private const val SHORTCUT_PREFIX = "shortcut:"

/**
 * Lo que falta para que la copia quede entera en este teléfono: los paquetes mencionados que no
 * están instalados, con su nombre si el archivo lo trae, por orden alfabético. Lo guardado para ellos
 * se carga igual: vuelve a su sitio en cuanto se instalan.
 */
fun missingApps(backup: Backup, installed: Set<String>): List<Pair<String, String>> = backup.packages()
    .filter { it !in installed }
    .map { it to (backup.labels[it] ?: it) }
    .sortedBy { it.second.lowercase() }

fun encodeBackup(backup: Backup): String = toJson(
    linkedMapOf(
        "alisio" to backup.version,
        "prefs" to backup.prefs.toSortedMap().mapValues { (_, value) -> value.encode() },
        "dock" to backup.dock.map {
            mapOf(
                "position" to it.position,
                "app" to it.appKey,
                "stack" to it.stackName,
                "stackIcon" to it.stackIcon,
                "stackGlyph" to it.stackGlyph,
                "stackImage" to it.stackImage
            )
        },
        "stacks" to backup.stacks.map { mapOf("slot" to it.slot, "key" to it.key, "position" to it.position) },
        "gestures" to backup.gestures.map {
            mapOf(
                "app" to it.appKey,
                "targetApp" to it.targetAppKey,
                "shortcutPackage" to it.shortcutPackage,
                "shortcutId" to it.shortcutId,
                // Como texto: un número tan grande podría perder cifras al leerlo como decimal.
                "shortcutUser" to it.shortcutUser?.toString()
            )
        },
        "zones" to backup.zones.map { mapOf("id" to it.id.toString(), "name" to it.name, "position" to it.position) },
        "assignments" to backup.assignments.toSortedMap(),
        "custom" to backup.custom.map {
            mapOf("app" to it.appKey, "label" to it.label, "icon" to it.icon, "hidden" to it.hidden)
        },
        "images" to backup.images.toSortedMap().mapValues { Base64.getEncoder().encodeToString(it.value) },
        "labels" to backup.labels.toSortedMap(),
        "pins" to backup.pins.map {
            mapOf("package" to it.packageName, "id" to it.id, "user" to it.userSerial.toString())
        }
    )
)

private fun PrefValue.encode(): Map<String, Any?> = when (this) {
    is PrefValue.Bool -> mapOf("type" to "boolean", "value" to value)
    is PrefValue.Whole -> mapOf("type" to "int", "value" to value)
    is PrefValue.Big -> mapOf("type" to "long", "value" to value.toString())
    is PrefValue.Real -> mapOf("type" to "double", "value" to value)
    is PrefValue.Small -> mapOf("type" to "float", "value" to value.toDouble())
    is PrefValue.Text -> mapOf("type" to "string", "value" to value)
    is PrefValue.Texts -> mapOf("type" to "set", "value" to value.sorted())
}

/**
 * Lee un archivo escrito por [encodeBackup]. Lo que no se entienda dentro de una lista (una fila sin
 * su clave) se salta; un archivo que no es de Alisio, o de una versión más nueva, lanza
 * [BackupException] y no se carga nada.
 */
fun decodeBackup(text: String): Backup {
    val root =
        try {
            parseJson(text) as? Map<*, *>
        } catch (e: JsonException) {
            throw BackupException("No es JSON: ${e.message}")
        } ?: throw BackupException("No es un objeto")
    val version = (root["alisio"] as? Double)?.toInt() ?: throw BackupException("No es una copia de Alisio")
    if (version > BACKUP_VERSION) throw BackupException("Versión $version, más nueva que esta")
    return Backup(
        version = version,
        prefs = root.objects("prefs").mapNotNull { (key, value) -> decodePref(value)?.let { key to it } }.toMap(),
        dock = root.rows("dock").mapNotNull {
            BackupSlot(
                position = it.int("position") ?: return@mapNotNull null,
                appKey = it.str("app"),
                stackName = it.str("stack"),
                stackIcon = it.str("stackIcon"),
                stackGlyph = it.str("stackGlyph"),
                stackImage = it.str("stackImage")
            )
        },
        stacks = root.rows("stacks").mapNotNull {
            BackupStackItem(
                slot = it.int("slot") ?: return@mapNotNull null,
                key = it.str("key") ?: return@mapNotNull null,
                position = it.int("position") ?: 0
            )
        },
        gestures = root.rows("gestures").mapNotNull {
            BackupGesture(
                appKey = it.str("app") ?: return@mapNotNull null,
                targetAppKey = it.str("targetApp"),
                shortcutPackage = it.str("shortcutPackage"),
                shortcutId = it.str("shortcutId"),
                shortcutUser = it.str("shortcutUser")?.toLongOrNull()
            )
        },
        zones = root.rows("zones").mapNotNull {
            BackupZone(
                id = it.str("id")?.toLongOrNull() ?: return@mapNotNull null,
                name = it.str("name") ?: return@mapNotNull null,
                position = it.int("position") ?: 0
            )
        },
        assignments = root.objects("assignments").mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
            .toMap(),
        custom = root.rows("custom").mapNotNull {
            BackupCustom(
                appKey = it.str("app") ?: return@mapNotNull null,
                label = it.str("label"),
                icon = it.str("icon"),
                hidden = it["hidden"] as? Boolean ?: false
            )
        },
        images = root.objects("images").mapNotNull { (name, value) ->
            val bytes =
                try {
                    Base64.getDecoder().decode(value as? String ?: return@mapNotNull null)
                } catch (_: IllegalArgumentException) {
                    return@mapNotNull null
                }
            name to bytes
        }.toMap(),
        labels = root.objects("labels").mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap(),
        pins = root.rows("pins").mapNotNull {
            BackupPin(
                packageName = it.str("package") ?: return@mapNotNull null,
                id = it.str("id") ?: return@mapNotNull null,
                userSerial = it.str("user")?.toLongOrNull() ?: 0L
            )
        }
    )
}

private fun decodePref(value: Any?): PrefValue? {
    val entry = value as? Map<*, *> ?: return null
    val raw = entry["value"]
    return when (entry["type"]) {
        "boolean" -> (raw as? Boolean)?.let(PrefValue::Bool)
        "int" -> (raw as? Double)?.let { PrefValue.Whole(it.toInt()) }
        "long" -> (raw as? String)?.toLongOrNull()?.let(PrefValue::Big)
        "double" -> (raw as? Double)?.let(PrefValue::Real)
        "float" -> (raw as? Double)?.let { PrefValue.Small(it.toFloat()) }
        "string" -> (raw as? String)?.let(PrefValue::Text)
        "set" -> (raw as? List<*>)?.let { list -> PrefValue.Texts(list.filterIsInstance<String>().toSet()) }
        else -> null
    }
}

private fun Map<*, *>.rows(key: String): List<Map<*, *>> =
    (this[key] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()

private fun Map<*, *>.objects(key: String): List<Pair<String, Any?>> =
    (this[key] as? Map<*, *>).orEmpty().mapNotNull { (k, v) -> (k as? String)?.let { it to v } }

private fun Map<*, *>.str(key: String): String? = this[key] as? String

private fun Map<*, *>.int(key: String): Int? = (this[key] as? Double)?.toInt()
