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

import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
internal class CatchExhaustedContextStepTest {

    @Test
    @Timeout(1)
    fun `should ignore block when context is not exhausted`() = runBlockingTest {
        val reference = AtomicInteger(0)
        val step = CatchExhaustedContextStep<Int>("") { _ -> reference.incrementAndGet() }
        val ctx = StepTestHelper.createStepContext<Any?, Int>(input = 123)

        step.execute(ctx)
        val result = ctx.consumeOutputValue()

        Assertions.assertEquals(0, reference.get())
        Assertions.assertEquals(123, result)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

    }

    @Test
    @Timeout(1)
    fun `should execute step when context is exhausted`() = runBlockingTest {
        val reference = AtomicInteger(0)
        val step = CatchExhaustedContextStep<Int>("") { _ -> reference.incrementAndGet() }
        val ctx = StepTestHelper.createStepContext<Any?, Int>(input = 456, isExhausted = true)

        step.execute(ctx)

        Assertions.assertEquals(1, reference.get())
        Assertions.assertTrue((ctx.output as Channel).isEmpty)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}
