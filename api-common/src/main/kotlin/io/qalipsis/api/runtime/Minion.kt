/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
