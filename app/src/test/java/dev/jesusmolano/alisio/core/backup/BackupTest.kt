package dev.jesusmolano.alisio.core.backup

import dev.jesusmolano.alisio.core.json.parseJson
import dev.jesusmolano.alisio.core.json.toJson
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {
    private val full =
        Backup(
            prefs =
                mapOf(
                    "accent" to PrefValue.Whole(2),
                    "now_enabled" to PrefValue.Bool(false),
                    "weather_latitude" to PrefValue.Real(40.42),
                    "font" to PrefValue.Text("inter \"propia\""),
                    "paused_apps" to PrefValue.Texts(setOf("com.x", "com.y")),
                    "big" to PrefValue.Big(1_763_000_000_123L),
                    "small" to PrefValue.Small(0.5f)
                ),
            dock =
                listOf(
                    BackupSlot(0, "com.a/.Main#0", null, null, null, null),
                    BackupSlot(1, null, "Dinero", "image", null, "pila.png")
                ),
            stacks = listOf(
                BackupStackItem(1, "com.bank/.Main#0", 0),
                BackupStackItem(1, "shortcut:com.maps/casa#0", 1)
            ),
            gestures = listOf(BackupGesture("com.a/.Main#0", null, "com.maps", "casa", 10L)),
            zones = listOf(BackupZone(3, "Trabajo", 0)),
            assignments = mapOf("com.dev/.Main#0" to "dev"),
            custom = listOf(BackupCustom("com.a/.Main#0", "Correo", "image:a.png", hidden = true)),
            images = mapOf("a.png" to byteArrayOf(1, 2, 3, -1)),
            labels = mapOf("com.bank" to "Banco"),
            pins = listOf(BackupPin("com.maps", "casa", 0))
        )

    @Test
    fun `lo guardado vuelve igual`() {
        val read = decodeBackup(encodeBackup(full))
        assertEquals(full.prefs, read.prefs)
        assertEquals(full.dock, read.dock)
        assertEquals(full.stacks, read.stacks)
        assertEquals(full.gestures, read.gestures)
        assertEquals(full.zones, read.zones)
        assertEquals(full.assignments, read.assignments)
        assertEquals(full.custom, read.custom)
        assertArrayEquals(full.images.getValue("a.png"), read.images.getValue("a.png"))
        assertEquals(full.labels, read.labels)
        assertEquals(full.pins, read.pins)
        assertEquals(BACKUP_VERSION, read.version)
    }

    @Test
    fun `un archivo ajeno o de una version nueva no se carga`() {
        assertThrows(BackupException::class.java) { decodeBackup("no es json") }
        assertThrows(BackupException::class.java) { decodeBackup("{\"otra\": 1}") }
        assertThrows(BackupException::class.java) { decodeBackup("{\"alisio\": ${BACKUP_VERSION + 1}}") }
    }

    @Test
    fun `las filas incompletas se saltan`() {
        val read = decodeBackup("{\"alisio\": 1, \"stacks\": [{\"slot\": 0}, {\"slot\": 0, \"key\": \"com.a/.M#0\"}]}")
        assertEquals(listOf(BackupStackItem(0, "com.a/.M#0", 0)), read.stacks)
    }

    @Test
    fun `dice que apps faltan con su nombre`() {
        val missing = missingApps(full, installed = setOf("com.a", "com.dev"))
        assertEquals(listOf("com.bank" to "Banco", "com.maps" to "com.maps"), missing)
    }

    @Test
    fun `el escritor de JSON se lee con el lector`() {
        val value = mapOf("a" to listOf(1, 2.5, "x\ny", null, true), "b" to emptyMap<String, Any>())
        assertEquals(
            mapOf("a" to listOf(1.0, 2.5, "x\ny", null, true), "b" to emptyMap<String, Any>()),
            parseJson(toJson(value))
        )
    }

    @Test
    fun `a known key with another type or an unknown key is not written`() {
        val expected = mapOf("accent" to PrefValue.Whole::class, "icon_style" to PrefValue.Text::class)
        // Un archivo editado a mano: el tipo declarado cuadra con su valor, pero no con el que lee Alisio.
        val text = toJson(
            mapOf(
                "alisio" to 1.0,
                "prefs" to mapOf(
                    "accent" to mapOf("type" to "string", "value" to "2"),
                    "icon_style" to mapOf("type" to "string", "value" to "squircle"),
                    "unknown" to mapOf("type" to "boolean", "value" to true)
                )
            )
        )
        val prefs = decodeBackup(text).prefs
        assertEquals(PrefValue.Text("2"), prefs["accent"])

        assertEquals(mapOf("icon_style" to PrefValue.Text("squircle")), acceptedPrefs(prefs) { expected[it] })
    }

    @Test
    fun `cargar una copia no cambia si ya se vio la bienvenida`() {
        val device = setOf("welcome_done", "onboarding_skipped")
        val current = mapOf("welcome_done" to PrefValue.Bool(true), "accent" to PrefValue.Whole(1))
        // Una copia anterior a la bienvenida no la lleva; una editada a mano podría traerla en false.
        val fromOld = mapOf("accent" to PrefValue.Whole(3))
        val fromEdited = mapOf("accent" to PrefValue.Whole(3), "welcome_done" to PrefValue.Bool(false))

        val expected = mapOf("accent" to PrefValue.Whole(3), "welcome_done" to PrefValue.Bool(true))
        assertEquals(expected, mergeRestored(current, fromOld, device))
        assertEquals(expected, mergeRestored(current, fromEdited, device))
        // En un teléfono que no la ha visto, la copia tampoco la marca como vista.
        assertEquals(mapOf("accent" to PrefValue.Whole(3)), mergeRestored(emptyMap(), fromEdited, device))
    }
}
