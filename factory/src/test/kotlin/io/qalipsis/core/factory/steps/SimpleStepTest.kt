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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

internal class SimpleStepTest {

    @Test
    @Timeout(1)
    fun `should just execute the closure`() = runBlockingTest {
        val step =
            SimpleStep<Long, Long>("", null) { context -> context.send(context.receive()) }
        val ctx = StepTestHelper.createStepContext<Long, Long>(input = 123L)

        step.execute(ctx)
        val output = ctx.consumeOutputValue()
        assertEquals(123L, output)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        Assertions.assertFalse(ctx.isExhausted)
    }

    @Test
    @Timeout(1)
    fun `should throw the exception`() {
        val step = SimpleStep<Long, Long>("", null) { throw RuntimeException("This is an error") }
        val ctx = StepTestHelper.createStepContext<Long, Long>(input = -1L)

        Assertions.assertThrows(RuntimeException::class.java) {
            runBlockingTest {
                step.execute(ctx)
            }
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        Assertions.assertFalse(ctx.isExhausted)
    }
}
