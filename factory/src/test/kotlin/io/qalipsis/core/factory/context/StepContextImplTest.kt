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

package io.qalipsis.core.factory.context

import io.qalipsis.api.context.StepContext
import io.qalipsis.test.coroutines.TestDispatcherProvider
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

internal class StepContextImplTest {

    @JvmField
    @RegisterExtension
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

}
