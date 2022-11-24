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
import io.micrometer.core.instrument.Tags
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
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
    private lateinit var meterRegistry: CampaignMeterRegistry

    @RelaxedMockK
    private lateinit var successCounter: Counter

    @RelaxedMockK
    private lateinit var failureCounter: Counter

    @RelaxedMockK
    private lateinit var errorCounter: Counter

    @RelaxedMockK
    private lateinit var reportLiveStateRegistry: CampaignReportLiveStateRegistry

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
        val step = VerificationStep<Int, Int>("my-step", eventsLogger, meterRegistry, reportLiveStateRegistry)
        step.start(stepStartStopContext)
        val ctx = StepTestHelper.createStepContext<Int, Int>(input = 1)

        step.execute(ctx)
        step.stop(relaxedMockk {
            every { campaignKey } returns "my-campaign"
            every { scenarioName } returns "my-scenario"
        })
        val output = ctx.consumeOutputValue()
        assertEquals(1, output)
        assertFalse((ctx.output as Channel).isClosedForReceive)
        assertFalse(ctx.isExhausted)

        coVerifyOnce {
            successCounter.increment()
            eventsLogger.info("step.assertion.success", timestamp = any(), tagsSupplier = any())
            reportLiveStateRegistry.put(
                eq("my-campaign"),
                eq("my-scenario"),
                eq("my-step"),
                eq(ReportMessageSeverity.INFO),
                any()
            )
        }

        confirmVerified(eventsLogger, successCounter, failureCounter, errorCounter, reportLiveStateRegistry)
    }

    @Test
    @Timeout(2)
    fun `should apply mapping`() = runBlockingTest {
        val step = VerificationStep<Int, String>(
            "my-step",
            eventsLogger,
            meterRegistry,
            reportLiveStateRegistry
        ) { value -> value.toString() }
        step.start(stepStartStopContext)
        val ctx = StepTestHelper.createStepContext<Int, String>(input = 1)

        step.execute(ctx)
        step.stop(relaxedMockk {
            every { campaignKey } returns "my-campaign"
            every { scenarioName } returns "my-scenario"
        })
        val output = ctx.consumeOutputValue()
        assertEquals("1", output)
        assertFalse((ctx.output as Channel).isClosedForReceive)
        assertFalse(ctx.isExhausted)

        coVerifyOnce {
            successCounter.increment()
            eventsLogger.info("step.assertion.success", timestamp = any(), tagsSupplier = any())
            reportLiveStateRegistry.put(
                eq("my-campaign"),
                eq("my-scenario"),
                eq("my-step"),
                eq(ReportMessageSeverity.INFO),
                any()
            )
        }
        confirmVerified(eventsLogger, successCounter, failureCounter, errorCounter, reportLiveStateRegistry)
    }

    @Test
    @Timeout(1)
    fun `should not forward data when assertion throwing error`() = runBlockingTest {
        val step =
            VerificationStep<Int, String>("my-step", eventsLogger, meterRegistry, reportLiveStateRegistry) { value ->
                fail<Any>("This is an error")
                value.toString()
            }
        step.start(stepStartStopContext)
        val ctx = StepTestHelper.createStepContext<Int, String>(input = 1)

        step.execute(ctx)
        step.stop(relaxedMockk {
            every { campaignKey } returns "my-campaign"
            every { scenarioName } returns "my-scenario"
        })
        assertTrue((ctx.output as Channel).isEmpty)

        assertFalse((ctx.output as Channel).isClosedForReceive)
        assertTrue(ctx.isExhausted)

        coVerifyOnce {
            failureCounter.increment()
            eventsLogger.warn(
                "step.assertion.failure",
                value = "This is an error",
                timestamp = any(),
                tagsSupplier = any()
            )
            reportLiveStateRegistry.put(
                eq("my-campaign"),
                eq("my-scenario"),
                eq("my-step"),
                eq(ReportMessageSeverity.ERROR),
                any()
            )
        }

        confirmVerified(eventsLogger, successCounter, failureCounter, errorCounter, reportLiveStateRegistry)
    }

    @Test
    @Timeout(1)
    fun `should not forward data when assertion throwing exception`() = runBlockingTest {
        val step = VerificationStep<Int, String>("my-step", eventsLogger, meterRegistry, reportLiveStateRegistry) {
            throw RuntimeException("The error")
        }
        step.start(stepStartStopContext)
        val ctx = StepTestHelper.createStepContext<Int, String>(input = 1)

        step.execute(ctx)
        step.stop(relaxedMockk {
            every { campaignKey } returns "my-campaign"
            every { scenarioName } returns "my-scenario"
        })
        assertTrue((ctx.output as Channel).isEmpty)

        assertFalse((ctx.output as Channel).isClosedForReceive)
        assertTrue(ctx.isExhausted)

        coVerifyOnce {
            errorCounter.increment()
            eventsLogger.warn("step.assertion.error", value = "The error", timestamp = any(), tagsSupplier = any())
            reportLiveStateRegistry.put(
                eq("my-campaign"),
                eq("my-scenario"),
                eq("my-step"),
                eq(ReportMessageSeverity.ERROR),
                any()
            )
        }
        confirmVerified(eventsLogger, successCounter, failureCounter, errorCounter, reportLiveStateRegistry)
    }

}
