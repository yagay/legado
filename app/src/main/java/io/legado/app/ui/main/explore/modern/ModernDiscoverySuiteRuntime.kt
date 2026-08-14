package io.legado.app.ui.main.explore.modern

import io.legado.app.ui.main.explore.DiscoverySuiteWidget
import io.legado.app.ui.main.explore.DiscoverySuiteWidgetTarget
import kotlinx.coroutines.Job
import kotlin.random.Random

/**
 * 套件布局的运行期状态容器。
 *
 * 队列、分页游标、签名和预取任务均属于现代布局，不再作为字段散落在上游
 * ExploreFragment 中。
 */
internal class ModernDiscoverySuiteRuntime {

    val widgetSignatures = hashMapOf<String, String>()
    val preparedRandomBatches = hashMapOf<String, SuitePreparedBatch>()
    val randomPrepareJobs = hashMapOf<String, Job>()

    private val randomDecks = hashMapOf<String, SuiteRandomDeck>()
    private val horizontalPagingStates = hashMapOf<String, SuiteHorizontalPagingState>()
    private val rankedPagingStates = hashMapOf<String, SuiteRankedPagingState>()

    fun clear() {
        widgetSignatures.clear()
        synchronized(randomDecks) {
            randomDecks.clear()
        }
        preparedRandomBatches.clear()
        randomPrepareJobs.values.forEach(Job::cancel)
        randomPrepareJobs.clear()
        horizontalPagingStates.clear()
        rankedPagingStates.clear()
    }

    fun randomDeck(widget: DiscoverySuiteWidget): SuiteRandomDeck {
        val signature = widget.deckSignature()
        return synchronized(randomDecks) {
            val current = randomDecks[widget.id]
            if (current != null && current.signature == signature) {
                return@synchronized current
            }
            val targets = widget.validRandomTargets()
            SuiteRandomDeck(
                signature = signature,
                targetIndex = if (targets.isEmpty()) {
                    0
                } else {
                    Random(System.nanoTime()).nextInt(targets.size)
                }
            ).also {
                randomDecks[widget.id] = it
            }
        }
    }

    fun horizontalPagingState(widget: DiscoverySuiteWidget): SuiteHorizontalPagingState {
        val signature = widget.horizontalPagingSignature()
        return synchronized(horizontalPagingStates) {
            horizontalPagingStates[widget.id]
                ?.takeIf { it.signature == signature }
                ?: SuiteHorizontalPagingState(signature = signature).also {
                    horizontalPagingStates[widget.id] = it
                }
        }
    }

    fun rankedPagingState(
        widget: DiscoverySuiteWidget,
        target: DiscoverySuiteWidgetTarget
    ): SuiteRankedPagingState {
        val key = widget.rankedPagingKey(target)
        val signature = widget.rankedPagingSignature(target)
        return synchronized(rankedPagingStates) {
            rankedPagingStates[key]
                ?.takeIf { it.signature == signature }
                ?: SuiteRankedPagingState(signature = signature).also {
                    rankedPagingStates[key] = it
                }
        }
    }
}
