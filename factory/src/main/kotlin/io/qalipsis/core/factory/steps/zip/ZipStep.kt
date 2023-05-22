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

package io.qalipsis.core.factory.steps.zip

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.core.exceptions.NotInitializedStepException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.launch


/**
 * Step to join flows from several [Step]s into a single one - attach values from the left and the right side using
 * a “fair” mode: the first coming on the left side is paired the first value from the right side. While this steps
 * technically has several parents, only one is considered at the primary, from which will the current one will
 * inherit the context.
 *
 * This step uses the same mechanism than [io.qalipsis.core.factory.steps.singleton.SingletonProxyStep] to receive
 * data from secondary steps.
 *
 * The step execution is suspended until values from all parent steps are provided.
 *
 * @author Polina Bril
 */
internal class ZipStep<I, O>(
    id: StepName,
    private val coroutineScope: CoroutineScope,
    /**
     * Configuration for the consumption from right steps.
     */
    private val rightSources: Collection<RightSource<out Any>>,

    ) : AbstractStep<I, O>(id, null) {

    private val consumptionJobs = mutableListOf<Job>()

    private val rightValues = Channel<Any?>(UNLIMITED)

    private var running = false

    override suspend fun start(context: StepStartStopContext) {
        // Starts the coroutines to buffer the data coming from the right steps.
        val stepName = this.name
        rightSources.forEach { source ->
            consumptionJobs.add(
                coroutineScope.launch {
                    log.debug { "Starting the coroutine buffering right records from step ${source.sourceStepName}" }
                    val subscription = source.topic.subscribe(stepName)
                    while (subscription.isActive()) {
                        val record = subscription.pollValue()
                        log.trace { "Adding right record to channel" }
                        rightValues.send(record.value)
                    }
                    log.debug { "Leaving the coroutine buffering right records" }
                }
            )
        }
        running = true
    }

    override suspend fun stop(context: StepStartStopContext) {
        running = false
        consumptionJobs.forEach { it.cancel() }
        rightSources.forEach { it.topic.close() }
        rightValues.cancel()
    }

    override suspend fun execute(context: StepContext<I, O>) {
        if (!running) throw NotInitializedStepException()

        val input = context.receive()
        val secondaryValues = rightValues.receive()
        log.trace { "Forwarding values" }
        context.send((input to secondaryValues) as O)
    }

    companion object {

        private val log = logger()
    }
}
