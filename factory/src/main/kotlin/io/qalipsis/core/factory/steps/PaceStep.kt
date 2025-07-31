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

package io.qalipsis.core.factory.steps

import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.steps.AbstractStep
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

/**
 * Step responsible for setting a pace in the workflow of a minion.
 *
 * @author Eric Jessé
 */
class PaceStep<I>(
    id: StepName,
    private val specification: (pastPeriodMs: Long) -> Long
) : AbstractStep<I, I>(id, null) {

    private val nextExecutions = ConcurrentHashMap<MinionId, NextExecution>()

    override suspend fun execute(context: StepContext<I, I>) {
        val input = context.receive()
        val nextExecution = nextExecutions[context.minionId] ?: NextExecution(specification(0))
        val waitingDelay = (nextExecution.timestampNanos - System.nanoTime()) / 1_000_000
        if (waitingDelay > 0) {
            log.trace { "Waiting for $waitingDelay ms" }
            delay(waitingDelay)
        }
        nextExecutions[context.minionId] = NextExecution(specification(nextExecution.periodMs))
        context.send(input)
    }

    private data class NextExecution(val periodMs: Long = 0,
                                     val timestampNanos: Long = System.nanoTime() + periodMs * 1_000_000)

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
