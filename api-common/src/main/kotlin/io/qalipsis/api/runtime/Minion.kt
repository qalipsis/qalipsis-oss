package io.qalipsis.api.runtime

import io.qalipsis.api.context.CampaignName
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.sync.SuspendedCountLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.slf4j.MDC
import kotlin.coroutines.CoroutineContext

/**
 *
 *
 * @author Eric Jessé
 */
interface Minion {

    val id: MinionId

    val campaignName: CampaignName

    val scenarioName: ScenarioName

    fun onComplete(block: suspend (() -> Unit))

    /**
     * Suspends the caller until the minion has started.
     */
    suspend fun waitForStart()

    /**
     * Waits for the minion to have all its jobs started and completed.
     */
    suspend fun join()

    /**
     * Cancels all running coroutines of the current minion.
     */
    suspend fun cancel()

    /**
     * Configures the [MDC] context for the execution of the minion.
     */
    fun completeMdcContext() {
        MDC.put("campaign", this.campaignName)
        MDC.put("scenario", this.scenarioName)
        MDC.put("minion", this.id)
    }

    /**
     * Cleans the [MDC] context from the fields added in [completeMdcContext].
     */
    fun cleanMdcContext() {
        MDC.remove("campaign")
        MDC.remove("scenario")
        MDC.remove("minion")
    }

    /**
     * Launches a coroutine to be attached to the minion. Calls to [join] are suspended until all the [Job]s
     * are completed, either successfully or not.
     */
    suspend fun launch(
        scope: CoroutineScope,
        context: CoroutineContext = scope.coroutineContext,
        countLatch: SuspendedCountLatch? = null,
        block: suspend CoroutineScope.() -> Unit
    ): Job?

    /**
     * Returns `true` of the start flag was released.
     */
    fun isStarted(): Boolean

    /**
     * Releases the start flag to resume the callers waiting for the minion to start.
     */
    suspend fun start()
}
