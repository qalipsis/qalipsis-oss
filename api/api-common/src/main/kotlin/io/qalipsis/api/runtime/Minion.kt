/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.api.runtime

import io.qalipsis.api.context.CampaignKey
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

    val campaignKey: CampaignKey

    val scenarioName: ScenarioName

    val isSingleton: Boolean

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
     * Configures the [MDC] context for the execution of the minion.
     */
    fun completeMdcContext() {
        MDC.put("campaign", this.campaignKey)
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

    /**
     * Stops all the activities of the minion and executes the completion hooks.
     *
     * @param interrupt when set to true, all the jobs assigned to the minion are cancelled
     */
    suspend fun stop(interrupt: Boolean = false)
}
