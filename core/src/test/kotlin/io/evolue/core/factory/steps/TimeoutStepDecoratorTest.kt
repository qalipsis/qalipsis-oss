package io.evolue.core.factory.steps

import io.evolue.api.steps.Step
import io.evolue.test.mockk.coVerifyOnce
import io.evolue.test.mockk.verifyOnce
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration

/**
 * @author Eric Jessé
 */
@ExtendWith(MockKExtension::class)
internal class TimeoutStepDecoratorTest {

    @RelaxedMockK
    lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    lateinit var counter: Counter

    @BeforeEach
    internal fun setUp() {
        every { meterRegistry.counter(any(), *anyVararg()) } returns counter
    }

    @Test
    @Timeout(5)
    fun shouldSucceedWhenDecoratedStepIsFasterThanTimeout() {
        val decoratedStep: Step<Any, Any> = mockk {
            coEvery { execute(any()) } answers { }
            every { id } answers { "my-step" }
            every { retryPolicy } returns null
            every { next } returns mutableListOf()
        }
        val ctx = StepTestHelper.createStepContext<Any, Any>()
        val step = spyk(TimeoutStepDecorator(Duration.ofMillis(10L), decoratedStep, meterRegistry))

        assertDoesNotThrow {
            runBlocking {
                step.execute(ctx)
            }
        }
        coVerifyOnce { step.executeStep(refEq(decoratedStep), refEq(ctx)) }
        Assertions.assertFalse(ctx.exhausted)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        confirmVerified(meterRegistry, counter)
    }

    @Test
    @Timeout(5)
    fun shouldFailWhenDecoratedStepIsLongerThanTimeout() {
        val timeout = 10L
        val decoratedStep: Step<Any, Any> = mockk {
            coEvery { execute(any()) } coAnswers { delay(timeout + 10) }
            every { id } answers { "my-step" }
            every { retryPolicy } returns null
            every { next } returns mutableListOf()
        }
        val ctx = StepTestHelper.createStepContext<Any, Any>()
        val step = spyk(TimeoutStepDecorator(Duration.ofMillis(timeout), decoratedStep, meterRegistry))

        assertThrows<TimeoutCancellationException> {
            runBlocking {
                step.execute(ctx)
            }
        }
        coVerifyOnce { step.executeStep(refEq(decoratedStep), refEq(ctx)) }
        Assertions.assertTrue(ctx.exhausted)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        verifyOnce { meterRegistry.counter("step-my-step-timeout", "minion", "my-minion") }
        verifyOnce { counter.increment() }

        confirmVerified(meterRegistry, counter)
    }

}