package io.qalipsis.api.orchestration.factories

import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.MinionId
import kotlinx.coroutines.Job

/**
 *
 *
 * @author Eric Jessé
 */
interface Minion {

    val id: MinionId

    val campaignId: CampaignId

    val dagId: DirectedAcyclicGraphId

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
     * Attaches a coroutine to the corresponding minion in order to monitor the activity state.
     * When the job is completed, the counter is decreased and the completion flag is closed to release the
     * [join] function.
     */
    suspend fun attach(job: Job)

    /**
     * Returns `true` of the start flag was released.
     */
    fun isStarted(): Boolean

    /**
     * Releases the start flag to resume the callers waiting for the minion to start.
     */
    suspend fun start()
}
