/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
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
