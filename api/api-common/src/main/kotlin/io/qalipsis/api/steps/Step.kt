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

package io.qalipsis.api.steps

import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.runtime.Minion

/**
 * Part of scenario responsible for processing a record.
 *
 * A step can convert the record, use is as a source for requests, consume messages to provide records.
 *
 * @param I type of the data input
 * @param O type of the data output
 *
 * @author Eric Jessé
 */
interface Step<I, O> {

    val name: StepName

    var retryPolicy: RetryPolicy?

    /**
     * Returns the list of next steps or an empty list if there is none.
     */
    val next: List<Step<O, *>>

    /**
     * Adds a step to the collection of next ones.
     */
    fun addNext(nextStep: Step<*, *>)

    /**
     * Operation to execute just after the creation of the step.
     */
    suspend fun init() = Unit

    /**
     * Operation to execute when a campaign starts.
     */
    @Throws(Exception::class)
    suspend fun start(context: StepStartStopContext) = Unit

    /**
     * Executes the operation wrapped by the step, passing the minion to it.
     */
    @Throws(Exception::class)
    suspend fun execute(minion: Minion, context: StepContext<I, O>) = execute(context)

    /**
     * Executes the operation wrapped by the step.
     */
    @Throws(Exception::class)
    suspend fun execute(context: StepContext<I, O>)

    /**
     * Operation to execute when a campaign ends.
     */
    suspend fun stop(context: StepStartStopContext) = Unit

    /**
     * Notifies the step that no more execution will be performed for the minion attached to [completionContext].
     * This function is called once the tail get out the step, whether the execution was successful or nor.
     *
     * @param completionContext context carrying the information of the tail of the minion workflow
     */
    suspend fun complete(completionContext: CompletionContext) = Unit

    /**
     * Notifies the step that the execution of the minions with IDs [minionIds] is complete and all the remaining
     * information can be deleted.
     */
    suspend fun discard(minionIds: Collection<MinionId>) = Unit

    /**
     * Operation to execute just before the destruction of the step.
     */
    suspend fun destroy() = Unit
}
