package dev.jesusmolano.alisio.data.now

import dev.jesusmolano.alisio.core.now.NowFeedback
import dev.jesusmolano.alisio.core.now.NowRule
import dev.jesusmolano.alisio.core.time.Clock
import dev.jesusmolano.alisio.data.db.SuggestionFeedbackDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Descartes de las sugerencias de Ahora: con ellos el motor oculta o silencia cada una (§5.5). */
class NowFeedbackRepository(
    private val dao: SuggestionFeedbackDao,
    private val clock: Clock,
    private val scope: CoroutineScope
) {
    val feedback: StateFlow<Map<Pair<NowRule, String>, NowFeedback>> =
        dao.all()
            .map { rows ->
                rows.mapNotNull { row ->
                    // Una regla que ya no exista (versión anterior) se ignora en vez de romper la lectura.
                    val rule = NowRule.entries.firstOrNull { it.name == row.rule } ?: return@mapNotNull null
                    (rule to row.key) to NowFeedback(row.dismissals, row.lastDismissedAt)
                }.toMap()
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Apunta un descarte más de esa sugerencia, con la hora de ahora. */
    fun dismiss(rule: NowRule, key: String) {
        scope.launch { dao.addDismissal(rule.name, key, clock.now()) }
    }
}
