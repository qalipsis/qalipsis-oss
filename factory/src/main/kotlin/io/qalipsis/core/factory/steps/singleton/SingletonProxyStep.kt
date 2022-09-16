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

package io.qalipsis.core.factory.steps.singleton

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.steps.AbstractStep

/**
 * Step to provide data from an output [io.qalipsis.api.steps.Step] running in a singleton [Minion] to a standard [Minion].
 *
 * This step is part of a non-singleton DAG and acts as a proxy to buffer and deliver the records to the contexts.
 *
 * @author Eric Jessé
 */
internal class SingletonProxyStep<I>(
    id: StepName,

    /**
     * Topic used to provide data to all the minions running the step.
     */
    private val topic: Topic<I>,

    /**
     * Specification to filter the record from the remote step.
     *
     * By default, all the records are accepted.
     */
    private val filter: (suspend (remoteRecord: I) -> Boolean) = { _ -> true }

) : AbstractStep<I, I>(id, null) {

    override suspend fun destroy() {
        topic.close()
    }

    override suspend fun execute(context: StepContext<I, I>) {
        val valueFromTopic = topic.subscribe("${context.minionId}-${context.stepName}").pollValue()
        if (filter(valueFromTopic)) {
            context.send(valueFromTopic)
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
