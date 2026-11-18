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

import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class PipeStepTest {

    @field:RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    internal fun `should forward input to output`() = testCoroutineDispatcher.runTest {
        // given
        val step = PipeStep<Int>("")
        val ctx = StepTestHelper.createStepContext<Int, Int>(
            input = 1,
            campaignKey = "my-campaign",
            scenarioName = "my-scenario",
            minionId = "my-minion",
            isTail = false
        )

        // when
        step.execute(ctx)

        //then
        val output = ctx.consumeOutputValue()
        assertEquals(1, output)
        assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}