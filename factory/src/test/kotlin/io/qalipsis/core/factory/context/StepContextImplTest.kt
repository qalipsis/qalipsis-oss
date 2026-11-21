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

package io.qalipsis.core.factory.context

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.qalipsis.api.context.StepContext
import io.qalipsis.core.factory.context.catadioptre.bufferedOutputRecords
import io.qalipsis.test.coroutines.TestDispatcherProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

internal class StepContextImplTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    internal fun `should confirm no input when none was added`() {
        // given
        val context = StepContextImpl<String, Int>(minionId = "", scenarioName = "", stepName = "")

        // when+then
        assertFalse(context.hasInput)
    }

    @Test
    internal fun `should confirm input existence when one was added`() {
        // given
        val context = StepContextImpl<String, Int>(
            input = Channel<String>(1).also { it.trySend("Test") },
            minionId = "",
            scenarioName = "",
            stepName = ""
        )

        // when+then
        assertTrue(context.hasInput)
    }

    @Test
    @Timeout(2)
    internal fun `should confirm output existence only when at least one is added, event when consumed`() =
        testDispatcherProvider.runTest {
            // given
            val output = Channel<StepContext.StepOutputRecord<Int>>(1)
            val context = StepContextImpl<String, Int>(output = output, minionId = "", scenarioName = "", stepName = "")

            // when+then
            assertFalse(context.generatedOutput)

            // when
            context.send(123)

            // then
            assertTrue(context.generatedOutput)

            //when
            output.receive()

            // then
            assertTrue(context.generatedOutput)
        }

    @Test
    @Timeout(2)
    fun `given no record sent when awaiting the empty output then it returns without creating the latch`() = runTest {
        // given
        val context = StepContextImpl<String, Int>(minionId = "", scenarioName = "", stepName = "")

        // when
        context.awaitEmptyOutput()
        context.close()

        // then
        assertThat(context.bufferedOutputRecords()).isNull()
    }

    @Test
    @Timeout(2)
    fun `given records sent when awaiting the empty output then it waits until all are consumed`() = runTest {
        // given
        val context = StepContextImpl<String, Int>(minionId = "", scenarioName = "", stepName = "")
        context.send(1)
        context.send(2)
        val consumed = mutableListOf<Int>()

        // when
        val awaiting = launch { context.awaitEmptyOutput() }
        runCurrent()

        // then
        assertThat(context.bufferedOutputRecords()).isNotNull()
        assertThat(awaiting.isCompleted).isFalse()

        // when
        val consuming = launch { context.consume { consumed += it.value } }
        advanceUntilIdle()

        // then
        assertThat(consumed).containsExactly(1, 2)
        assertThat(awaiting.isCompleted).isTrue()
        consuming.cancel()
    }
}
