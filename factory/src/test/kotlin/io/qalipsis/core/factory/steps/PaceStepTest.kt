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

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.test.steps.StepTestHelper
import io.qalipsis.test.time.QalipsisTimeAssertions.assertLongerOrEqualTo
import io.qalipsis.test.time.QalipsisTimeAssertions.assertShorterOrEqualTo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
internal class PaceStepTest {

    @Test
    @Timeout(20)
    internal fun `add isolated pace for different minions`() = runBlocking {
        // Short warm-up to obtain finer precision.
        val warmUpStep = PaceStep<Long>("") { 0 }
        val ctx = StepTestHelper.createStepContext<Long, Long>(input = 1L)
        warmUpStep.execute(ctx)
        (ctx.output as Channel<Any?>).receive()

        // Period increases of 100 seconds until 600 ms is reached.
        val paceStep = 200L
        val paceMax = 1200L
        val step = PaceStep<Long>("") { (it + paceStep).coerceAtMost(paceMax) }
        val errorMargin = Duration.ofMillis(40L)
        var pastExecutionEnd: Long? = null

        // Check that values are individual for each minion.
        repeat(2) { minionIndex ->
            repeat(10) { rep ->
                log.debug { "Executing loop $rep of minion $minionIndex" }
                val context = StepTestHelper.createStepContext<Long, Long>(input = 1L, minionId = "minion-$minionIndex")
                val start = pastExecutionEnd ?: System.currentTimeMillis()
                step.execute(context)
                val end = System.currentTimeMillis()
                pastExecutionEnd = end
                val executionDuration = Duration.ofMillis(end - start)
                val expectedDuration = Duration.ofMillis(((rep.toLong() + 1) * paceStep).coerceAtMost(paceMax))

                assertLongerOrEqualTo(expectedDuration - errorMargin, executionDuration)
                assertShorterOrEqualTo(expectedDuration + errorMargin, executionDuration)
                Assertions.assertEquals(1L, (context.output as Channel).receive().value)
            }
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
