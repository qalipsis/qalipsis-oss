package io.qalipsis.core.factories.steps

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.report.CampaignStateKeeper
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
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
internal class VerificationStepTest {

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var meterRegistry: MeterRegistry

    @RelaxedMockK
    private lateinit var successCounter: Counter

    @RelaxedMockK
    private lateinit var failureCounter: Counter

    @RelaxedMockK
    private lateinit var errorCounter: Counter

    @RelaxedMockK
    private lateinit var campaignStateKeeper: CampaignStateKeeper

    @RelaxedMockK
    private lateinit var stepStartStopContext: StepStartStopContext

    @BeforeEach
    internal fun setUp() {
        val meterTags = relaxedMockk<Tags>()
        val successMeterTags = relaxedMockk<Tags>()
        val failureMeterTags = relaxedMockk<Tags>()
        val errorMeterTags = relaxedMockk<Tags>()
        every { stepStartStopContext.toMetersTags() } returns meterTags
        every { meterTags.and("status", "success") } returns successMeterTags
        every { meterTags.and("status", "failure") } returns failureMeterTags
        every { meterTags.and("status", "error") } returns errorMeterTags
        every { meterRegistry.counter("step-my-step-assertion", refEq(successMeterTags)) } returns successCounter
        every { meterRegistry.counter("step-my-step-assertion", refEq(failureMeterTags)) } returns failureCounter
        every { meterRegistry.counter("step-my-step-assertion", refEq(errorMeterTags)) } returns errorCounter
    }

    @Test
    @Timeout(1)
    fun `should simply forward with default step`() = runBlockingTest {
        val step = VerificationStep<Int, Int>("my-step", eventsLogger, meterRegistry, campaignStateKeeper)
        step.start(stepStartStopContext)
        val ctx = StepTestHelper.createStepContext<Int, Int>(input = 1)

        step.execute(ctx)
        step.stop(relaxedMockk {
            every { campaignId } returns "my-campaign"
            every { scenarioId } returns "my-scenario"
        })
        val output = (ctx.output as Channel).receive()
        assertEquals(1, output)
        assertFalse((ctx.output as Channel).isClosedForReceive)
        assertFalse(ctx.isExhausted)

        verifyOnce {
            successCounter.increment()
            eventsLogger.info("step.assertion.success", timestamp = any(), tagsSupplier = any())
            campaignStateKeeper.put(
                eq("my-campaign"),
                eq("my-scenario"),
                eq("my-step"),
                eq(ReportMessageSeverity.INFO),
                any()
            )
        }

        confirmVerified(eventsLogger, successCounter, failureCounter, errorCounter, campaignStateKeeper)
    }

    @Test
    @Timeout(2)
    fun `should apply mapping`() = runBlockingTest {
        val step = VerificationStep<Int, String>(
            "my-step",
            eventsLogger,
            meterRegistry,
            campaignStateKeeper
        ) { value -> value.toString() }
        step.start(stepStartStopContext)
        val ctx = StepTestHelper.createStepContext<Int, String>(input = 1)

        step.execute(ctx)
        step.stop(relaxedMockk {
            every { campaignId } returns "my-campaign"
            every { scenarioId } returns "my-scenario"
        })
        val output = (ctx.output as Channel).receive()
        assertEquals("1", output)
        assertFalse((ctx.output as Channel).isClosedForReceive)
        assertFalse(ctx.isExhausted)

        verifyOnce {
            successCounter.increment()
            eventsLogger.info("step.assertion.success", timestamp = any(), tagsSupplier = any())
            campaignStateKeeper.put(
                eq("my-campaign"),
                eq("my-scenario"),
                eq("my-step"),
                eq(ReportMessageSeverity.INFO),
                any()
            )
        }
        confirmVerified(eventsLogger, successCounter, failureCounter, errorCounter, campaignStateKeeper)
    }

    @Test
    @Timeout(1)
    fun `should not forward data when assertion throwing error`() = runBlockingTest {
        val step = VerificationStep<Int, String>("my-step", eventsLogger, meterRegistry, campaignStateKeeper) { value ->
            fail<Any>("This is an error")
            value.toString()
        }
        step.start(stepStartStopContext)
        val ctx = StepTestHelper.createStepContext<Int, String>(input = 1)

        step.execute(ctx)
        step.stop(relaxedMockk {
            every { campaignId } returns "my-campaign"
            every { scenarioId } returns "my-scenario"
        })
        assertTrue((ctx.output as Channel).isEmpty)

        assertFalse((ctx.output as Channel).isClosedForReceive)
        assertTrue(ctx.isExhausted)

        verifyOnce {
            failureCounter.increment()
            eventsLogger.warn(
                "step.assertion.failure",
                value = "This is an error",
                timestamp = any(),
                tagsSupplier = any()
            )
            campaignStateKeeper.put(
                eq("my-campaign"),
                eq("my-scenario"),
                eq("my-step"),
                eq(ReportMessageSeverity.ERROR),
                any()
            )
        }

        confirmVerified(eventsLogger, successCounter, failureCounter, errorCounter, campaignStateKeeper)
    }

    @Test
    @Timeout(1)
    fun `should not forward data when assertion throwing exception`() = runBlockingTest {
        val step = VerificationStep<Int, String>("my-step", eventsLogger, meterRegistry, campaignStateKeeper) {
            throw RuntimeException("The error")
        }
        step.start(stepStartStopContext)
        val ctx = StepTestHelper.createStepContext<Int, String>(input = 1)

        step.execute(ctx)
        step.stop(relaxedMockk {
            every { campaignId } returns "my-campaign"
            every { scenarioId } returns "my-scenario"
        })
        assertTrue((ctx.output as Channel).isEmpty)

        assertFalse((ctx.output as Channel).isClosedForReceive)
        assertTrue(ctx.isExhausted)

        verifyOnce {
            errorCounter.increment()
            eventsLogger.warn("step.assertion.error", value = "The error", timestamp = any(), tagsSupplier = any())
            campaignStateKeeper.put(
                eq("my-campaign"),
                eq("my-scenario"),
                eq("my-step"),
                eq(ReportMessageSeverity.ERROR),
                any()
            )
        }
        confirmVerified(eventsLogger, successCounter, failureCounter, errorCounter, campaignStateKeeper)
    }

}
