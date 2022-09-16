/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.orchestration

import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.runtime.DirectedAcyclicGraph
import io.qalipsis.api.runtime.Minion
import io.qalipsis.api.steps.Step
import kotlinx.coroutines.Job

/**
 * Core component in charge of executing all the steps on all the minions.
 *
 * @author Eric Jessé
 */
interface Runner {

    /**
     * Executes the dag onto the specified minion.
     */
    suspend fun run(minion: Minion, dag: DirectedAcyclicGraph)

    /**
     * Releases the minion and make it execute [rootStep] and its successors.
     *
     * @param minion the minion to launch
     * @param rootStep the first step of the chain to execute
     * @param stepContext the initial step context
     * @param completionConsumer action to execute after the lately executed step of the tree, which might have an output or not, be exhausted...
     */
    suspend fun runMinion(
        minion: Minion, rootStep: Step<*, *>, stepContext: StepContext<*, *>,
        completionConsumer: (suspend (ctx: StepContext<*, *>) -> Unit)? = null
    )

    /**
     * Makes [minion] executes [rootStep] and its successors, calling completionConsumer when all the steps are complete.
     *
     * @param minion the minion to launch
     * @param rootStep the first step of the chain to execute
     * @param stepContext the initial step context
     * @param completionConsumer action to execute after the lately executed step of the tree, which might have an output or not, be exhausted...
     */
    suspend fun execute(
        minion: Minion, rootStep: Step<*, *>, stepContext: StepContext<*, *>,
        completionConsumer: (suspend (stepContext: StepContext<*, *>) -> Unit)? = null
    ): Job?

    /**
     * Notifies the [rootStep] and its successors of the completion of [minion].
     *
     * @param minion the minion to launch
     * @param rootStep the first step of the chain to execute
     * @param completionContext the completion context for the minion
     */
    suspend fun complete(minion: Minion, rootStep: Step<*, *>, completionContext: CompletionContext): Job?
}
