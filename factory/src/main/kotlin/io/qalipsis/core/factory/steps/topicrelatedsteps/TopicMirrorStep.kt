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

package io.qalipsis.core.factory.steps.topicrelatedsteps

import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.steps.Step

/**
 * Step forwarding the input to a topic as well as the output channel.
 *
 * @property topic topic to forward the data to
 * @property predicate predicate to match to forward the data to the topic, default to always true
 * @property wrap converter from context and received value into the entity to send to the topic
 * @param I type of the input and output
 * @param T type of the entity sent to the topic
 *
 * @author Eric Jessé
 */
internal open class TopicMirrorStep<I, T>(
    id: StepName,
    private val topic: Topic<T>,
    private val predicate: (context: StepContext<I, I>, value: Any?) -> Boolean = { _, _ -> true },
    @Suppress("UNCHECKED_CAST") private val wrap: (context: StepContext<I, I>, value: Any?) -> T = { _, value -> value as T }
) : AbstractStep<I, I>(id, null) {

    override fun addNext(nextStep: Step<*, *>) {
        // Do nothing, this step is connected to its next ones using the topic property.
        // It should not have any next step, otherwise there is a race to consume the output
        // between the topic feeder and the next steps.
    }

    override suspend fun destroy() {
        topic.close()
    }

    override suspend fun execute(context: StepContext<I, I>) {
        if (context.hasInput) {
            // Consumes the data emitted by the previous step to populate the broadcast channel to the proxy steps.
            val value = context.receive()
            if (predicate(context, value)) {
                val record = wrap(context, value)
                log.trace { "Adding $value to the topic" }
                topic.produceValue(record)
                log.trace { "$value was added to the topic" }
            } else {
                log.trace { "The value $value does not match the predicate" }
            }
            context.send(value)
        } else {
            log.trace { "The context $context has no input" }
        }
    }

    override suspend fun complete(completionContext: CompletionContext) {
        topic.complete()
        super.complete(completionContext)
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
