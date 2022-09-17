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

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.runtime.Minion
import io.qalipsis.api.steps.Step
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Duration

/**
 * @author Eric Jessé
 */
@WithMockk
internal class TimeoutStepDecoratorTest {

    @RelaxedMockK
    lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    lateinit var counter: Counter

    @RelaxedMockK
    lateinit var minion: Minion

    @BeforeEach
    internal fun setUp() {
        every { meterRegistry.counter(any(), *anyVararg()) } returns counter
    }

    @Test
    @Timeout(5)
    fun `should succeed when decorated step is faster than timeout`() {
        val decoratedStep: Step<Any, Any> = mockk {
            coEvery { execute(any(), any()) } answers { }
            every { name } answers { "my-step" }
            every { retryPolicy } returns null
            every { next } returns mutableListOf()
        }
        val ctx = StepTestHelper.createStepContext<Any, Any>()
        val step = spyk(TimeoutStepDecorator(Duration.ofMillis(10L), decoratedStep, meterRegistry))

        assertDoesNotThrow {
            runBlockingTest { step.execute(minion, ctx) }
        }
        coVerifyOnce { step.executeStep(refEq(minion), refEq(decoratedStep), refEq(ctx)) }
        Assertions.assertFalse(ctx.isExhausted)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        confirmVerified(meterRegistry, counter)
    }

    @Test
    @Timeout(5)
    fun `should fail when decorated step is longer than timeout`() = runBlockingTest {
        val timeout = 10L
        val decoratedStep: Step<Any, Any> = mockk {
            coEvery { execute(any(), any()) } coAnswers { delay(timeout + 10) }
            every { name } answers { "my-step" }
            every { retryPolicy } returns null
            every { next } returns mutableListOf()
        }
        val ctx = StepTestHelper.createStepContext<Any, Any>()
        val step = spyk(TimeoutStepDecorator(Duration.ofMillis(timeout), decoratedStep, meterRegistry))

        assertThrows<TimeoutCancellationException> {
            step.execute(minion, ctx)
        }
        coVerifyOnce { step.executeStep(refEq(minion), refEq(decoratedStep), refEq(ctx)) }
        Assertions.assertTrue(ctx.isExhausted)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        verifyOnce { meterRegistry.counter("step-my-step-timeout", "minion", "my-minion") }
        verifyOnce { counter.increment() }

        confirmVerified(meterRegistry, counter)
    }

}
