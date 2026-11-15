package dev.jesusmolano.alisio.core.zones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZonesTest {
    private fun app(name: String, packageName: String = "pkg.$name") =
        ZoneApp("$packageName/.Main#0", name, packageName)

    private val claude = app("Claude")
    private val spotify = app("Spotify")
    private val bank = app("Banco")
    private val expenses = app("Gastos")
    private val gym = app("Gimnasio")
    private val settings = app("Ajustes", "com.android.settings")
    private val camera = app("Cámara", "com.sec.android.app.camera")
    private val maps = app("Maps")
    private val abaco = app("Ábaco")
    private val zoom = app("Zoom")

    private val all = listOf(zoom, maps, claude, spotify, bank, expenses, gym, settings, camera, abaco)

    private fun input(
        assignments: Map<String, ZoneId> = emptyMap(),
        custom: List<CustomZone> = emptyList(),
        frecency: Map<String, Double> = emptyMap()
    ) = ZoneInput(
        apps = all,
        dockApps = listOf(claude.key, spotify.key),
        stacks = listOf(ZoneStack(5, "Dinero", listOf(bank.key, expenses.key))),
        custom = custom,
        assignments = assignments,
        systemPackages = setOf(settings.packageName, camera.packageName),
        frecency = frecency
    )

    private fun List<Zone>.labels(id: ZoneId) = first { it.id == id }.apps.map { it.label }

    @Test
    fun `reparte por dock, pilas, sistema y resto en un orden fijo`() {
        val zones = buildZones(input())

        assertEquals(
            listOf(ZoneId.Daily, ZoneId.Stack(5), ZoneId.Dev, ZoneId.System, ZoneId.Rest),
            zones.map { it.id }
        )
        assertEquals(listOf("Claude", "Spotify"), zones.labels(ZoneId.Daily))
        assertEquals(listOf("Banco", "Gastos"), zones.labels(ZoneId.Stack(5)))
        assertEquals("Dinero", zones.first { it.id == ZoneId.Stack(5) }.name)
        assertEquals(emptyList<String>(), zones.labels(ZoneId.Dev))
        assertEquals(listOf("Ajustes", "Cámara"), zones.labels(ZoneId.System))
    }

    @Test
    fun `cada app esta en una sola isla`() {
        val zones = buildZones(input(assignments = mapOf(gym.key to ZoneId.Dev, claude.key to ZoneId.Dev)))

        assertEquals(all.map { it.key }.sorted(), zones.flatMap { it.apps }.map { it.key }.sorted())
    }

    @Test
    fun `resto va de la A a la Z sin distinguir acentos`() {
        val zones = buildZones(input(frecency = mapOf(zoom.key to 9.0)))

        assertEquals(listOf("Ábaco", "Gimnasio", "Maps", "Zoom"), zones.labels(ZoneId.Rest))
    }

    @Test
    fun `dentro de una isla manda la frecencia y el empate va por nombre`() {
        val zones =
            buildZones(
                input(
                    assignments = mapOf(maps.key to ZoneId.Dev, gym.key to ZoneId.Dev, zoom.key to ZoneId.Dev),
                    frecency = mapOf(zoom.key to 3.0, spotify.key to 1.5)
                )
            )

        assertEquals(listOf("Zoom", "Gimnasio", "Maps"), zones.labels(ZoneId.Dev))
        assertEquals(listOf("Spotify", "Claude"), zones.labels(ZoneId.Daily))
    }

    @Test
    fun `lo asignado a mano gana al dock y a sistema`() {
        val zones =
            buildZones(
                input(
                    assignments = mapOf(claude.key to ZoneId.Dev, camera.key to ZoneId.Rest, maps.key to ZoneId.System)
                )
            )

        assertEquals(listOf("Claude"), zones.labels(ZoneId.Dev))
        assertEquals(listOf("Spotify"), zones.labels(ZoneId.Daily))
        assertEquals(listOf("Ajustes", "Maps"), zones.labels(ZoneId.System))
        assertTrue("Cámara" in zones.labels(ZoneId.Rest))
    }

    @Test
    fun `las islas propias salen tras Dev, en su orden y aunque esten vacias`() {
        val custom = listOf(CustomZone(7, "Viajes"), CustomZone(2, "Juegos"))
        val zones = buildZones(input(assignments = mapOf(maps.key to ZoneId.Custom(7)), custom = custom))

        assertEquals(
            listOf(
                ZoneId.Daily,
                ZoneId.Stack(5),
                ZoneId.Dev,
                ZoneId.Custom(7),
                ZoneId.Custom(2),
                ZoneId.System,
                ZoneId.Rest
            ),
            zones.map { it.id }
        )
        assertEquals(listOf("Maps"), zones.labels(ZoneId.Custom(7)))
        assertEquals("Juegos", zones.first { it.id == ZoneId.Custom(2) }.name)
    }

    @Test
    fun `una asignacion a una isla borrada o a una del dock no cuenta`() {
        val zones =
            buildZones(input(assignments = mapOf(maps.key to ZoneId.Custom(99), gym.key to ZoneId.Daily)))

        assertTrue("Maps" in zones.labels(ZoneId.Rest))
        assertTrue("Gimnasio" in zones.labels(ZoneId.Rest))
    }

    @Test
    fun `Diario y Sistema no salen vacias`() {
        val zones = buildZones(ZoneInput(apps = listOf(maps)))

        assertEquals(listOf(ZoneId.Dev, ZoneId.Rest), zones.map { it.id })
    }

    @Test
    fun `Diario y Sistema vacias siguen a la vista si sus apps estan en otra isla`() {
        val zones =
            buildZones(
                input(
                    assignments =
                        mapOf(
                            claude.key to ZoneId.Dev,
                            spotify.key to ZoneId.Rest,
                            settings.key to ZoneId.Dev,
                            camera.key to ZoneId.Dev
                        )
                )
            )

        assertEquals(
            listOf(ZoneId.Daily, ZoneId.Stack(5), ZoneId.Dev, ZoneId.System, ZoneId.Rest),
            zones.map { it.id }
        )
        assertEquals(emptyList<String>(), zones.labels(ZoneId.Daily))
        assertEquals(emptyList<String>(), zones.labels(ZoneId.System))
        // Con la isla a la vista, volver a ella es un destino y borra la asignación.
        assertEquals(
            listOf(ZoneId.Daily, ZoneId.System, ZoneId.Rest),
            moveTargets(zones.map { it.id }, current = ZoneId.Dev, default = ZoneId.Daily)
        )
        assertEquals(AssignmentChange.Clear, assignmentChange(ZoneId.Daily, default = ZoneId.Daily))
    }

    @Test
    fun `una app en dos pilas se queda en la primera`() {
        val input =
            ZoneInput(
                apps = listOf(bank),
                stacks = listOf(ZoneStack(1, "A", listOf(bank.key)), ZoneStack(4, "B", listOf(bank.key)))
            )

        assertEquals(ZoneId.Stack(1), ZoneResolver(input).zoneOf(bank))
    }

    @Test
    fun `llevar una app a su isla de siempre borra la asignacion`() {
        assertEquals(AssignmentChange.Clear, assignmentChange(ZoneId.Daily, default = ZoneId.Daily))
        assertEquals(AssignmentChange.Clear, assignmentChange(ZoneId.Rest, default = ZoneId.Rest))
        assertEquals(AssignmentChange.Assign(ZoneId.Rest), assignmentChange(ZoneId.Rest, default = ZoneId.System))
        assertEquals(
            AssignmentChange.Assign(ZoneId.Custom(3)),
            assignmentChange(ZoneId.Custom(3), default = ZoneId.Rest)
        )
    }

    @Test
    fun `a Diario y a las pilas no se entra desde Zonas`() {
        assertEquals(AssignmentChange.Invalid, assignmentChange(ZoneId.Daily, default = ZoneId.Rest))
        assertEquals(AssignmentChange.Invalid, assignmentChange(ZoneId.Stack(2), default = ZoneId.Daily))
    }

    @Test
    fun `destinos de una app del dock movida a Dev`() {
        val zones = listOf(ZoneId.Daily, ZoneId.Stack(5), ZoneId.Dev, ZoneId.Custom(1), ZoneId.System, ZoneId.Rest)

        assertEquals(
            listOf(ZoneId.Daily, ZoneId.Custom(1), ZoneId.System, ZoneId.Rest),
            moveTargets(zones, current = ZoneId.Dev, default = ZoneId.Daily)
        )
    }

    @Test
    fun `la clave guardada de cada isla se vuelve a leer`() {
        val ids = listOf(ZoneId.Daily, ZoneId.Stack(3), ZoneId.Dev, ZoneId.Custom(12), ZoneId.System, ZoneId.Rest)

        ids.forEach { assertEquals(it, ZoneId.parse(it.encode())) }
        assertNull(ZoneId.parse("custom:x"))
        assertNull(ZoneId.parse("otra"))
        assertFalse(ZoneId.Stack(3).assignable)
        assertTrue(ZoneId.Custom(12).assignable)
    }
}
