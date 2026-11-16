package dev.jesusmolano.alisio.core.now

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** La regla «Reanudar» de Ahora (Música siempre a mano). */
class NowMusicTest {
    private val zone = ZoneOffset.UTC

    // Miércoles 11 de noviembre de 2026, 08:00.
    private val now = at(11, 8, 0)
    private val minute = 60_000L
    private val apps = listOf(NowApp("spotify", 0), NowApp("news", 0))

    private fun at(day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).toInstant(zone).toEpochMilli()

    /** Música puesta a las 08:05 en los laborables anteriores que se indiquen (1 = ayer). */
    private fun mornings(vararg daysAgo: Int): List<Long> = daysAgo.map { at(11 - it, 8, 5) }

    private fun input(
        starts: List<Long> = mornings(1, 2, 5),
        playing: Boolean = false,
        lastPlayedAt: Long? = null,
        openings: Map<String, List<Long>> = emptyMap(),
        dockKeys: Set<String> = emptySet(),
        feedback: Map<Pair<NowRule, String>, NowFeedback> = emptyMap(),
        forgotten: Map<Pair<NowRule, String>, Long> = emptyMap(),
        music: NowMusic? = NowMusic("spotify", starts, playing, lastPlayedAt)
    ) = NowInput(
        now = now,
        zone = zone,
        apps = apps,
        openings = openings,
        dockKeys = dockKeys,
        feedback = feedback,
        forgotten = forgotten,
        music = music
    )

    @Test
    fun `con la costumbre de poner musica a esta hora propone reanudarla`() {
        assertEquals(NowSuggestion(NowRule.MUSIC, "spotify", evidence = 3), suggest(input()))
    }

    @Test
    fun `vale aunque la app este en el dock`() {
        assertEquals(NowRule.MUSIC, suggest(input(dockKeys = setOf("spotify")))?.rule)
    }

    @Test
    fun `menos de tres dias no son costumbre`() {
        assertNull(suggest(input(starts = mornings(1, 2))))
    }

    @Test
    fun `los fines de semana no cuentan para un laborable`() {
        // Sábado 7 y domingo 8: no son días como hoy.
        assertNull(suggest(input(starts = listOf(at(7, 8, 5), at(8, 8, 5), at(10, 8, 5)))))
    }

    @Test
    fun `si ya suena algo no hay nada que reanudar`() {
        assertNull(suggest(input(playing = true)))
    }

    @Test
    fun `tras sonar hace menos de una hora no se propone`() {
        assertNull(suggest(input(lastPlayedAt = now - 10 * minute)))
        assertNull(suggest(input(starts = mornings(1, 2, 5) + (now - 20 * minute))))
        assertEquals(NowRule.MUSIC, suggest(input(lastPlayedAt = now - MUSIC_RECENT_MILLIS - minute))?.rule)
    }

    @Test
    fun `sin app de musica o desinstalada no se propone`() {
        assertNull(suggest(input(music = null)))
        assertNull(suggest(input(music = NowMusic("gone", mornings(1, 2, 5), playing = false))))
    }

    @Test
    fun `respeta los descartes como las demas reglas`() {
        val hidden = mapOf((NowRule.MUSIC to "spotify") to NowFeedback(1, now - 60 * minute))
        assertNull(suggest(input(feedback = hidden)))
        val silenced = mapOf((NowRule.MUSIC to "spotify") to NowFeedback(DISMISSALS_TO_SILENCE, 0))
        assertNull(suggest(input(feedback = silenced)))
        val old = mapOf((NowRule.MUSIC to "spotify") to NowFeedback(1, now - DISMISS_HIDE_MILLIS - minute))
        assertEquals(NowRule.MUSIC, suggest(input(feedback = old))?.rule)
    }

    @Test
    fun `lo olvidado deja de contar`() {
        val forgotten = mapOf((NowRule.MUSIC to "spotify") to at(10, 0, 0))
        assertNull(suggest(input(forgotten = forgotten)))
    }

    @Test
    fun `va antes que la costumbre de abrir otra app`() {
        val habit = mapOf("news" to listOf(at(10, 8, 0), at(9, 8, 0), at(6, 8, 0)))
        assertEquals(NowRule.MUSIC, suggest(input(openings = habit))?.rule)
        // Descartada la música, sale la otra costumbre.
        val silenced = mapOf((NowRule.MUSIC to "spotify") to NowFeedback(DISMISSALS_TO_SILENCE, 0))
        assertEquals("news", suggest(input(openings = habit, feedback = silenced))?.appKey)
    }

    @Test
    fun `sin escuchar las sesiones no se propone aunque haya costumbre`() {
        // Sin escucha no se sabe si ya suena: «Reanudar» podría caer sobre música que ya suena.
        assertNull(suggest(input(music = NowMusic("spotify", mornings(1, 2, 5), playing = false, watching = false))))
    }
}
