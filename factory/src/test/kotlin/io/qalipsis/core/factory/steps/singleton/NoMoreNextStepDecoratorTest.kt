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

import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.steps.Step
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 *
 * @author Eric Jessé
 */
@WithMockk
internal class NoMoreNextStepDecoratorTest {

    @RelaxedMockK
    lateinit var decoratedStep: Step<Int, Int>

    @Test
    @Timeout(1)
    internal fun `should execute decorated`() = runBlockingTest {
        coEvery { decoratedStep.execute(any()) } coAnswers {
            (firstArg() as StepContext<Int, Int>).also {
                it.send(it.receive())
            }
        }
        val step = NoMoreNextStepDecorator(decoratedStep)
        val ctx = StepTestHelper.createStepContext<Int, Int>(input = 1)

        step.execute(ctx)
        val output = ctx.consumeOutputValue()
        assertEquals(1, output)

        coVerifyOnce {
            decoratedStep.execute(refEq(ctx))
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }

    @Test
    @Timeout(1)
    internal fun `should provide decorated's next steps`() {
        val nextSteps = listOf<Step<Int, *>>()
        every { decoratedStep.next } returns nextSteps

        val step = NoMoreNextStepDecorator(decoratedStep)

        val result = step.decorated.next

        assertSame(nextSteps, result)
        verifyOnce {
            decoratedStep.next
        }
    }

    @Test
    @Timeout(1)
    internal fun `should init decorated`() = runBlockingTest {
        val step = NoMoreNextStepDecorator(decoratedStep)

        step.init()

        coVerifyOnce {
            decoratedStep.init()
        }
    }

    @Test
    @Timeout(1)
    internal fun `should destroy decorated`() = runBlockingTest {
        val step = NoMoreNextStepDecorator(decoratedStep)

        step.destroy()
        coVerifyOnce {
            decoratedStep.destroy()
        }
    }

    @Test
    @Timeout(1)
    internal fun `should start decorated`() = runBlockingTest {
        val step = NoMoreNextStepDecorator(decoratedStep)
        val startStopContext = relaxedMockk<StepStartStopContext>()

        step.start(startStopContext)
        coVerifyOnce {
            decoratedStep.start(refEq(startStopContext))
        }
    }

    @Test
    @Timeout(1)
    internal fun `should stop decorated`() = runBlockingTest {
        val step = NoMoreNextStepDecorator(decoratedStep)
        val startStopContext = relaxedMockk<StepStartStopContext>()

        step.stop(startStopContext)
        coVerifyOnce {
            decoratedStep.stop(refEq(startStopContext))
        }
    }

    @Test
    @Timeout(1)
    internal fun `should not add next decorated`() = runBlockingTest {
        val step = NoMoreNextStepDecorator(decoratedStep)

        step.addNext(relaxedMockk { })
    }
}
