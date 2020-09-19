package io.evolue.core.factories.steps

import io.evolue.api.events.EventsLogger
import io.evolue.test.mockk.WithMockk
import io.evolue.test.mockk.verifyOnce
import io.evolue.test.steps.StepTestHelper
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * @author Eric Jessé
 */
@WithMockk
internal class AssertionStepTest {

    @RelaxedMockK
    lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    lateinit var counter: Counter

    @BeforeEach
    internal fun setUp() {
        every { meterRegistry.counter(any(), *anyVararg()) } returns counter
    }

    @Test
    @Timeout(1)
    fun shouldSimplyForwardWithDefaultStep() {
        val step = AssertionStep<Int, Int>("my-step", eventsLogger, meterRegistry)
        val ctx = StepTestHelper.createStepContext<Int, Int>(input = 1)

        runBlocking {
            step.execute(ctx)
            val output = (ctx.output as Channel).receive()
            assertEquals(output, 1)
        }
        assertFalse((ctx.output as Channel).isClosedForReceive)
        assertFalse(ctx.exhausted)

        verifyOnce {
            meterRegistry.counter("step-my-step-assertion", "status", "success", "minion", "my-minion")
            counter.increment()
            eventsLogger.info("step-my-step-assertion-success", tagsSupplier = any())
        }

        confirmVerified(eventsLogger, meterRegistry, counter)
    }

    @Test
    @Timeout(2)
    fun shouldApplyMapping() {
        val step = AssertionStep<Int, String>("my-step", eventsLogger, meterRegistry) { value -> value.toString() }
        val ctx = StepTestHelper.createStepContext<Int, String>(input = 1)

        runBlocking {
            step.execute(ctx)
            val output = (ctx.output as Channel).receive()
            assertEquals(output, "1")
        }
        assertFalse((ctx.output as Channel).isClosedForReceive)
        assertFalse(ctx.exhausted)

        verifyOnce {
            meterRegistry.counter("step-my-step-assertion", "status", "success", "minion", "my-minion")
            counter.increment()
            eventsLogger.info("step-my-step-assertion-success", tagsSupplier = any())
        }
        confirmVerified(eventsLogger, meterRegistry, counter)
    }

    @Test
    @Timeout(1)
    fun shouldNotForwardDataWhenAssertionThrowingError() {
        val step = AssertionStep<Int, String>("my-step", eventsLogger, meterRegistry) { value ->
            fail<Any>("This is an error")
            value.toString()
        }
        val ctx = StepTestHelper.createStepContext<Int, String>(input = 1)

        runBlocking {
            step.execute(ctx)
            assertTrue((ctx.output as Channel).isEmpty)
        }

        assertFalse((ctx.output as Channel).isClosedForReceive)
        assertTrue(ctx.exhausted)

        verifyOnce {
            meterRegistry.counter("step-my-step-assertion", "status", "failure", "minion", "my-minion")
            counter.increment()
            eventsLogger.warn("step-my-step-assertion-failure", tagsSupplier = any())
        }

        confirmVerified(eventsLogger, meterRegistry, counter)
    }

    @Test
    @Timeout(1)
    fun shouldNotForwardDataWhenAssertionThrowingException() {
        val step = AssertionStep<Int, String>("my-step", eventsLogger, meterRegistry) { value ->
            throw RuntimeException("The error")
            value.toString()
        }
        val ctx = StepTestHelper.createStepContext<Int, String>(input = 1)

        runBlocking {
            step.execute(ctx)
            assertTrue((ctx.output as Channel).isEmpty)
        }

        assertFalse((ctx.output as Channel).isClosedForReceive)
        assertTrue(ctx.exhausted)

        verifyOnce {
            meterRegistry.counter("step-my-step-assertion", "status", "error", "minion", "my-minion")
            counter.increment()
            eventsLogger.warn("step-my-step-assertion-error", tagsSupplier = any())
        }
        confirmVerified(eventsLogger, meterRegistry, counter)
    }
}
