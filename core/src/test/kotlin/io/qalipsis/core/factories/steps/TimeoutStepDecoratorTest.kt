package io.qalipsis.core.factories.steps

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.orchestration.factories.Minion
import io.qalipsis.api.steps.Step
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.*
import java.time.Duration

/**
 * @author Eric Jessé
 */
@Suppress("EXPERIMENTAL_API_USAGE")
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
    fun shouldSucceedWhenDecoratedStepIsFasterThanTimeout() {
        val decoratedStep: Step<Any, Any> = mockk {
            coEvery { execute(any(), any()) } answers { }
            every { id } answers { "my-step" }
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
    fun shouldFailWhenDecoratedStepIsLongerThanTimeout() = runBlockingTest {
        val timeout = 10L
        val decoratedStep: Step<Any, Any> = mockk {
            coEvery { execute(any(), any()) } coAnswers { delay(timeout + 10) }
            every { id } answers { "my-step" }
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
