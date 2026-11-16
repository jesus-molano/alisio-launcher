package dev.jesusmolano.alisio

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import dev.jesusmolano.alisio.core.notes.sharedNote

/**
 * Compartir un texto o un enlace con Alisio desde cualquier app: se guarda como nota del Inicio y se
 * vuelve a la app de la que venía, con un aviso corto. No pinta nada: el sistema la cierra sola.
 * Solo existe para el sistema con las notas encendidas (`AppContainer` la habilita o deshabilita).
 */
class ShareNoteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val note =
            if (intent?.action == Intent.ACTION_SEND) {
                sharedNote(
                    intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                    intent.getStringExtra(Intent.EXTRA_SUBJECT)
                )
            } else {
                null
            }
        val container = (application as AlisioApp).container
        if (note != null && container.settings.prefs.value?.homeNotes != false) {
            container.notes.add(note)
            Toast.makeText(applicationContext, R.string.note_shared, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(applicationContext, R.string.note_shared_empty, Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
