package dev.jesusmolano.alisio.data.notes

import dev.jesusmolano.alisio.core.notes.clampNote
import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.data.db.HomeNote
import dev.jesusmolano.alisio.data.db.HomeNoteDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Las notas del Inicio, la más reciente primero. Se crean desde la barra («+ texto») o al compartir
 * un texto o un enlace con Alisio, y se tachan desde el Inicio.
 */
class NotesRepository(private val dao: HomeNoteDao, private val clock: Clock, private val scope: CoroutineScope) {
    val notes: StateFlow<List<HomeNote>> = dao.all().stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun add(text: String) {
        val clean = clampNote(text.trim())
        if (clean.isEmpty()) return
        scope.launch { dao.put(HomeNote(text = clean, createdAt = clock.now())) }
    }

    /** Tacha la nota. Devuelve cómo deshacerlo: vuelve con su id y su hora, en el mismo sitio. */
    fun remove(note: HomeNote): () -> Unit {
        scope.launch { dao.delete(note.id) }
        return { scope.launch { dao.put(note) } }
    }
}
