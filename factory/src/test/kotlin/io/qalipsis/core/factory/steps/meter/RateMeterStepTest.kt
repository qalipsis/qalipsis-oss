/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.core.factory.steps.meter

import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.aerisconsulting.catadioptre.setProperty
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.verify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Rate
import io.qalipsis.api.meters.steps.TrackedThresholdRatio
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.factory.steps.meter.catadioptre.checkState
import io.qalipsis.core.factory.steps.meter.catadioptre.startStatusCheck
import io.qalipsis.core.factory.steps.meter.catadioptre.statusJob
import io.qalipsis.core.factory.steps.meter.checkers.BetweenChecker
import io.qalipsis.core.factory.steps.meter.checkers.GreaterThanChecker
import io.qalipsis.core.factory.steps.meter.checkers.LessThanChecker
import io.qalipsis.core.factory.steps.meter.checkers.NotBetweenChecker
import io.qalipsis.core.factory.steps.meter.checkers.ValueChecker
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyExactly
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.steps.StepTestHelper.createStepContext
import java.time.Duration
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Francisca Eze
 */

@WithMockk
internal class RateMeterStepTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var campaignReportLiveStateRegistry: CampaignReportLiveStateRegistry

    @RelaxedMockK
    private lateinit var campaignMeterRegistry: CampaignMeterRegistry

    @MockK
    private lateinit var rate: Rate

    @RelaxedMockK
    private lateinit var stepStartStopContext: StepStartStopContext

    @BeforeEach
    internal fun setup() {
        val meterTags = mapOf<String, String>()
        every { stepStartStopContext.toMetersTags() } returns meterTags
        every { stepStartStopContext.scenarioName } returns "my-scenario"
        every { stepStartStopContext.stepName } returns "rate-step"
        every { stepStartStopContext.campaignKey } returns "my-campaign"
        every {
            campaignMeterRegistry.rate(
                scenarioName = "my-scenario",
                stepName = "rate-step",
                name = "rate-meter",
                tags = meterTags
            )
        } returns rate
        every { rate.report(any()) } returns rate
        coJustRun { rate.incrementTotal(any<Double>()) }
        coJustRun { rate.incrementBenchmark(any<Double>()) }
    }

    @Test
    fun `should execute the step and forward the output without errors`() = testDispatcherProvider.run {
        // given
        val block: (context: StepContext<Int, Int>, input: Int) -> TrackedThresholdRatio =
            { _, _ -> TrackedThresholdRatio(2.0, 17.9) }
        val checkers =
            listOf<Pair<Rate.() -> Double, ValueChecker<Double>>>(
                Pair({ 12.60 }, BetweenChecker(12.0, 18.0)),
                Pair({ 12.60 }, LessThanChecker(18.0))
            )
        val rateMeterStep = spyk(
            RateMeterStep(
                id = "rate-step",
                retryPolicy = null,
                coroutineScope = this,
                campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                meterName = "rate-meter",
                block = block,
                checkers = checkers,
                campaignMeterRegistry = campaignMeterRegistry,
                checkPeriod = Duration.ofMillis(100)
            ), recordPrivateCalls = true
        )
        val latch = SuspendedCountLatch(2, true)
        coEvery { rateMeterStep.checkState(any<StepStartStopContext>()) } coAnswers {
            callOriginal()
            latch.decrement()
        }
        rateMeterStep.start(stepStartStopContext)
        val ctx = createStepContext<Int, Int>(
            input = 1,
            campaignKey = "my-campaign",
            scenarioName = "my-scenario",
            minionId = "my-minion"
        )

        // when
        rateMeterStep.execute(ctx)
        latch.await()
        rateMeterStep.stop(stepStartStopContext)

        // then
        val output = ctx.consumeOutputValue()
        assertEquals(1, output)
        assertFalse((ctx.output as Channel).isClosedForReceive)
        assertFalse(ctx.isExhausted)
        assertThat(rateMeterStep.statusJob()).isNotNull()

        coVerify {
            campaignMeterRegistry.rate(
                scenarioName = "my-scenario",
                stepName = "rate-step",
                name = "rate-meter",
                tags = emptyMap()
            )
            rate.incrementTotal(17.9)
            rate.incrementBenchmark(2.0)
            rateMeterStep.checkState(stepStartStopContext)
        }
        coVerifyExactly(3) {
            rateMeterStep.checkState(stepStartStopContext)
        }
        verify {
            rate.report(any())
            rateMeterStep.startStatusCheck(stepStartStopContext)
        }

        confirmVerified(campaignMeterRegistry, rate)
    }

    @Test
    fun `should not start the job to evaluate meter conditions when there are no checkers`() =
        testDispatcherProvider.run {
            // given
            val block: (context: StepContext<Int, Int>, input: Int) -> TrackedThresholdRatio =
                { _, _ -> TrackedThresholdRatio(2.0, 17.9) }
            val checkers = emptyList<Pair<Rate.() -> Double, ValueChecker<Double>>>()
            val rateMeterStep = spyk(
                RateMeterStep(
                    id = "rate-step",
                    retryPolicy = null,
                    coroutineScope = this,
                    campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                    meterName = "rate-meter",
                    block = block,
                    checkers = checkers,
                    campaignMeterRegistry = campaignMeterRegistry,
                    checkPeriod = Duration.ofMillis(100)
                ), recordPrivateCalls = true
            )
            rateMeterStep.start(stepStartStopContext)
            val ctx = createStepContext<Int, Int>(
                input = 1,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion"
            )

            // when
            rateMeterStep.execute(ctx)
            rateMeterStep.stop(stepStartStopContext)

            // then
            val output = ctx.consumeOutputValue()
            assertEquals(1, output)
            assertFalse((ctx.output as Channel).isClosedForReceive)
            assertFalse(ctx.isExhausted)
            assertThat(rateMeterStep.statusJob()).isNull()

            coVerify {
                campaignMeterRegistry.rate(
                    scenarioName = "my-scenario",
                    stepName = "rate-step",
                    name = "rate-meter",
                    tags = emptyMap()
                )
                rate.incrementBenchmark(2.0)
                rate.incrementTotal(17.9)
            }
            verify { rate.report(any()) }
            coVerifyOnce { rateMeterStep.checkState(stepStartStopContext) }
            confirmVerified(campaignMeterRegistry, rate)
        }

    @Test
    fun `should report errors to the campaignReportLiveStateRegistry`() =
        testDispatcherProvider.run {
            // given
            val block: (context: StepContext<Int, Int>, input: Int) -> TrackedThresholdRatio =
                { _, _ -> TrackedThresholdRatio(2.0, 17.9) }
            val checkers =
                listOf<Pair<Rate.() -> Double, ValueChecker<Double>>>(
                    Pair({ 13.60 }, NotBetweenChecker(12.0, 18.0)),
                    Pair({ 12.60 }, GreaterThanChecker(18.0))
                )
            val latch = SuspendedCountLatch(2, true)
            val rateMeterStep = spyk(
                RateMeterStep(
                    id = "rate-step",
                    retryPolicy = null,
                    coroutineScope = this,
                    campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                    meterName = "rate-meter",
                    block = block,
                    checkers = checkers,
                    campaignMeterRegistry = campaignMeterRegistry,
                    checkPeriod = Duration.ofMillis(100)
                ), recordPrivateCalls = true
            )
            coEvery { rateMeterStep.checkState(any<StepStartStopContext>()) } coAnswers {
                callOriginal()
                latch.decrement()
            }
            rateMeterStep.start(stepStartStopContext)
            val ctx = createStepContext<Int, Int>(
                input = 1,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion",
                stepName = "rate-step"
            )

            // when
            rateMeterStep.execute(ctx)
            latch.await()
            rateMeterStep.stop(stepStartStopContext)

            // then
            val output = ctx.consumeOutputValue()
            assertEquals(1, output)
            assertFalse((ctx.output as Channel).isClosedForReceive)
            assertFalse(ctx.isExhausted)
            assertThat(rateMeterStep.statusJob()).isNotNull()

            coVerify {
                campaignMeterRegistry.rate(
                    scenarioName = "my-scenario",
                    stepName = "rate-step",
                    name = "rate-meter",
                    tags = emptyMap()
                )
                rate.incrementBenchmark(2.0)
                rate.incrementTotal(17.9)

                campaignReportLiveStateRegistry.put(
                    "my-campaign", "my-scenario", "rate-step", ReportMessageSeverity.ERROR, null, """
                    Value 13.6 should not be between bounds: 12.0 and 18.0
                    Value should be greater than 18.0
                """.trimIndent()
                )
            }
            coVerifyExactly(3) {
                rateMeterStep.checkState(stepStartStopContext)
            }
            verify {
                rate.report(any())
                rateMeterStep.startStatusCheck(stepStartStopContext)
            }

            confirmVerified(campaignMeterRegistry, rate, campaignReportLiveStateRegistry)
        }

    @Test
    fun `should delete pre-recorded errors to the campaignReportLiveStateRegistry when checkers return no violations`() =
        testDispatcherProvider.run {
            // given
            val block: (context: StepContext<Int, Int>, input: Int) -> TrackedThresholdRatio =
                { _, _ -> TrackedThresholdRatio(2.0, 17.9) }
            val checkers =
                listOf<Pair<Rate.() -> Double, ValueChecker<Double>>>(
                    Pair({ 21.60 }, GreaterThanChecker(18.0))
                )
            val latch = SuspendedCountLatch(2, true)
            val rateMeterStep = spyk(
                RateMeterStep(
                    id = "rate-step",
                    retryPolicy = null,
                    coroutineScope = this,
                    campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                    meterName = "rate-meter",
                    block = block,
                    checkers = checkers,
                    campaignMeterRegistry = campaignMeterRegistry,
                    checkPeriod = Duration.ofMillis(100)
                ), recordPrivateCalls = true
            )
            coEvery { rateMeterStep.checkState(any<StepStartStopContext>()) } coAnswers {
                callOriginal()
                latch.decrement()
            }
            rateMeterStep.setProperty("messageId", "message-id")
            rateMeterStep.start(stepStartStopContext)
            val ctx = createStepContext<Int, Int>(
                input = 1,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion",
                stepName = "rate-step"
            )

            // when
            rateMeterStep.execute(ctx)
            latch.await()
            rateMeterStep.stop(stepStartStopContext)

            // then
            val output = ctx.consumeOutputValue()
            assertEquals(1, output)
            assertFalse((ctx.output as Channel).isClosedForReceive)
            assertFalse(ctx.isExhausted)
            assertThat(rateMeterStep.statusJob()).isNotNull()

            coVerify {
                campaignMeterRegistry.rate(
                    scenarioName = "my-scenario",
                    stepName = "rate-step",
                    name = "rate-meter",
                    tags = emptyMap()
                )
                rate.incrementBenchmark(2.0)
                rate.incrementTotal(17.9)
                campaignReportLiveStateRegistry.delete("my-campaign", "my-scenario", "rate-step", "message-id")
            }
            coVerifyExactly(3) {
                rateMeterStep.checkState(stepStartStopContext)
            }
            verify {
                rate.report(any())
                rateMeterStep.startStatusCheck(stepStartStopContext)
            }

            confirmVerified(campaignMeterRegistry, rate, campaignReportLiveStateRegistry)
        }

}