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
import io.qalipsis.api.meters.Throughput
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.factory.steps.meter.catadioptre.checkState
import io.qalipsis.core.factory.steps.meter.catadioptre.startStatusCheck
import io.qalipsis.core.factory.steps.meter.catadioptre.statusJob
import io.qalipsis.core.factory.steps.meter.checkers.BetweenChecker
import io.qalipsis.core.factory.steps.meter.checkers.GreaterThanOrEqualChecker
import io.qalipsis.core.factory.steps.meter.checkers.LessThanChecker
import io.qalipsis.core.factory.steps.meter.checkers.LessThanOrEqualChecker
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
internal class ThroughputMeterStepTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var campaignReportLiveStateRegistry: CampaignReportLiveStateRegistry

    @RelaxedMockK
    private lateinit var campaignMeterRegistry: CampaignMeterRegistry

    @MockK
    private lateinit var throughput: Throughput

    @RelaxedMockK
    private lateinit var stepStartStopContext: StepStartStopContext

    private val meterTags = mapOf<String, String>()

    @BeforeEach
    internal fun setup() {
        every { stepStartStopContext.toMetersTags() } returns meterTags
        every { stepStartStopContext.scenarioName } returns "my-scenario"
        every { stepStartStopContext.stepName } returns "throughput-step"
        every { stepStartStopContext.campaignKey } returns "my-campaign"
        every { throughput.report(any()) } returns throughput
        coJustRun { throughput.record(any<Double>()) }
    }

    @Test
    fun `should execute the step and forward the output without errors`() = testDispatcherProvider.run {
        // given
        val block: (context: StepContext<Int, Int>, input: Int) -> Double =
            { _, _ -> 22.0 }
        val checkers =
            listOf<Pair<Throughput.() -> Double, ValueChecker<Double>>>(
                Pair({ 12.60 }, BetweenChecker(12.0, 18.0)),
                Pair({ 12.60 }, LessThanChecker(18.0))
            )
        val meterTags = mapOf<String, String>()
        val throughputMeterStep = spyk(
            ThroughputMeterStep(
                id = "throughput-step",
                retryPolicy = null,
                coroutineScope = this,
                campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                meterName = "throughput-meter",
                block = block,
                checkers = checkers,
                campaignMeterRegistry = campaignMeterRegistry,
                checkPeriod = Duration.ofMillis(100),
                percentiles = setOf(95.0, 99.0),
            ), recordPrivateCalls = true
        )
        every {
            campaignMeterRegistry.throughput(
                scenarioName = "my-scenario",
                stepName = "throughput-step",
                name = "throughput-meter",
                tags = meterTags,
                percentiles = setOf(95.0, 99.0)
            )
        } returns throughput
        val latch = SuspendedCountLatch(2, true)
        coEvery { throughputMeterStep.checkState(any<StepStartStopContext>()) } coAnswers { latch.decrement() }
        throughputMeterStep.start(stepStartStopContext)
        val ctx = createStepContext<Int, Int>(
            input = 1,
            campaignKey = "my-campaign",
            scenarioName = "my-scenario",
            minionId = "my-minion"
        )

        // when
        throughputMeterStep.execute(ctx)
        latch.await()
        throughputMeterStep.stop(stepStartStopContext)

        // then
        val output = ctx.consumeOutputValue()
        assertEquals(1, output)
        assertFalse((ctx.output as Channel).isClosedForReceive)
        assertFalse(ctx.isExhausted)
        assertThat(throughputMeterStep.statusJob()).isNotNull()

        coVerify {
            campaignMeterRegistry.throughput(
                scenarioName = "my-scenario",
                stepName = "throughput-step",
                name = "throughput-meter",
                tags = emptyMap(),
                percentiles = setOf(95.0, 99.0)
            )
            throughput.record(22.0)
            throughputMeterStep.checkState(stepStartStopContext)
        }
        coVerifyExactly(3) {
            throughputMeterStep.checkState(stepStartStopContext)
        }
        verify {
            throughput.report(any())
            throughputMeterStep.startStatusCheck(stepStartStopContext)
        }

        confirmVerified(campaignMeterRegistry, throughput)
    }

    @Test
    fun `should not start the job to evaluate meter conditions when there are no checkers`() =
        testDispatcherProvider.run {
            // given
            val block: (context: StepContext<Int, Int>, input: Int) -> Double =
                { _, _ -> 15.9 }
            val checkers = emptyList<Pair<Throughput.() -> Double, ValueChecker<Double>>>()
            val throughputMeterStep = spyk(
                ThroughputMeterStep(
                    id = "throughput-step",
                    retryPolicy = null,
                    coroutineScope = this,
                    campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                    meterName = "throughput-meter",
                    block = block,
                    checkers = checkers,
                    campaignMeterRegistry = campaignMeterRegistry,
                    checkPeriod = Duration.ofMillis(100),
                    percentiles = setOf(95.0, 99.0),
                    ), recordPrivateCalls = true
            )
            every {
                campaignMeterRegistry.throughput(
                    scenarioName = "my-scenario",
                    stepName = "throughput-step",
                    name = "throughput-meter",
                    tags = meterTags,
                    percentiles = setOf(95.0, 99.0)
                )
            } returns throughput
            throughputMeterStep.start(stepStartStopContext)
            val ctx = createStepContext<Int, Int>(
                input = 1,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion"
            )

            // when
            throughputMeterStep.execute(ctx)
            throughputMeterStep.stop(stepStartStopContext)

            // then
            val output = ctx.consumeOutputValue()
            assertEquals(1, output)
            assertFalse((ctx.output as Channel).isClosedForReceive)
            assertFalse(ctx.isExhausted)
            assertThat(throughputMeterStep.statusJob()).isNull()

            coVerify {
                campaignMeterRegistry.throughput(
                    scenarioName = "my-scenario",
                    stepName = "throughput-step",
                    name = "throughput-meter",
                    tags = emptyMap(),
                    percentiles = setOf(95.0, 99.0)
                )
                throughput.record(15.9)
            }
            verify { throughput.report(any()) }
            coVerifyOnce { throughputMeterStep.checkState(stepStartStopContext) }
            confirmVerified(campaignMeterRegistry, throughput)
        }

    @Test
    fun `should report errors to the campaignReportLiveStateRegistry`() =
        testDispatcherProvider.run {
            // given
            val block: (context: StepContext<Int, Int>, input: Int) -> Double =
                { _, _ -> 38.5 }
            val checkers =
                listOf<Pair<Throughput.() -> Double, ValueChecker<Double>>>(
                    Pair({ 13.60 }, LessThanChecker(12.0)),
                    Pair({ 12.60 }, GreaterThanOrEqualChecker(27.0))
                )
            val latch = SuspendedCountLatch(2, true)
            every {
                campaignMeterRegistry.throughput(
                    scenarioName = "my-scenario",
                    stepName = "throughput-step",
                    name = "throughput-meter",
                    tags = meterTags,
                    percentiles = setOf(95.0, 99.0)
                )
            } returns throughput
            val throughputMeterStep = spyk(
                ThroughputMeterStep(
                    id = "throughput-step",
                    retryPolicy = null,
                    coroutineScope = this,
                    campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                    meterName = "throughput-meter",
                    block = block,
                    checkers = checkers,
                    campaignMeterRegistry = campaignMeterRegistry,
                    checkPeriod = Duration.ofMillis(100),
                    percentiles = setOf(95.0, 99.0 ),
                    ), recordPrivateCalls = true
            )
            coEvery { throughputMeterStep.checkState(any<StepStartStopContext>()) } coAnswers {
                callOriginal()
                latch.decrement()
            }
            throughputMeterStep.start(stepStartStopContext)
            val ctx = createStepContext<Int, Int>(
                input = 1,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion",
                stepName = "throughput-step"
            )

            // when
            throughputMeterStep.execute(ctx)
            latch.await()
            throughputMeterStep.stop(stepStartStopContext)

            // then
            val output = ctx.consumeOutputValue()
            assertEquals(1, output)
            assertFalse((ctx.output as Channel).isClosedForReceive)
            assertFalse(ctx.isExhausted)
            assertThat(throughputMeterStep.statusJob()).isNotNull()

            coVerify {
                campaignMeterRegistry.throughput(
                    scenarioName = "my-scenario",
                    stepName = "throughput-step",
                    name = "throughput-meter",
                    tags = emptyMap(),
                    percentiles = setOf(95.0, 99.0)
                )
                throughput.record(38.5)

                campaignReportLiveStateRegistry.put(
                    "my-campaign", "my-scenario", "throughput-step", ReportMessageSeverity.ERROR, null, """
                        Value should be less than 12.0
                        Value should be greater than or equal to 27.0
                    """.trimIndent()
                )
            }
            coVerifyExactly(3) {
                throughputMeterStep.checkState(stepStartStopContext)
            }
            verify {
                throughput.report(any())
                throughputMeterStep.startStatusCheck(stepStartStopContext)
            }

            confirmVerified(campaignMeterRegistry, throughput, campaignReportLiveStateRegistry)
        }

    @Test
    fun `should delete pre-recorded errors to the campaignReportLiveStateRegistry when checkers return no violations`() =
        testDispatcherProvider.run {
            // given
            val block: (context: StepContext<Int, Int>, input: Int) -> Double =
                { _, _ -> 52.0 }
            val checkers = listOf<Pair<Throughput.() -> Double, ValueChecker<Double>>>(
                Pair({ 18.0 }, LessThanOrEqualChecker(18.0))
            )
            val latch = SuspendedCountLatch(2, true)
            val throughputMeterStep = spyk(
                ThroughputMeterStep(
                    id = "throughput-step",
                    retryPolicy = null,
                    coroutineScope = this,
                    campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                    meterName = "throughput-meter",
                    block = block,
                    checkers = checkers,
                    campaignMeterRegistry = campaignMeterRegistry,
                    checkPeriod = Duration.ofMillis(100),
                    percentiles = setOf(95.0, 99.0),
                    ), recordPrivateCalls = true
            )
            every {
                campaignMeterRegistry.throughput(
                    scenarioName = "my-scenario",
                    stepName = "throughput-step",
                    name = "throughput-meter",
                    tags = meterTags,
                    percentiles = setOf(95.0, 99.0)
                )
            } returns throughput
            coEvery { throughputMeterStep.checkState(any<StepStartStopContext>()) } coAnswers {
                callOriginal()
                latch.decrement()
            }
            throughputMeterStep.setProperty("messageId", "message-id")
            throughputMeterStep.start(stepStartStopContext)
            val ctx = createStepContext<Int, Int>(
                input = 1,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion",
                stepName = "throughput-step"
            )

            // when
            throughputMeterStep.execute(ctx)
            latch.await()
            throughputMeterStep.stop(stepStartStopContext)

            // then
            val output = ctx.consumeOutputValue()
            assertEquals(1, output)
            assertFalse((ctx.output as Channel).isClosedForReceive)
            assertFalse(ctx.isExhausted)
            assertThat(throughputMeterStep.statusJob()).isNotNull()

            coVerify {
                campaignMeterRegistry.throughput(
                    scenarioName = "my-scenario",
                    stepName = "throughput-step",
                    name = "throughput-meter",
                    tags = emptyMap(),
                    percentiles = setOf(95.0, 99.0)
                )
                throughput.record(52.0)
                campaignReportLiveStateRegistry.delete("my-campaign", "my-scenario", "throughput-step", "message-id")
            }
            coVerifyExactly(3) {
                throughputMeterStep.checkState(stepStartStopContext)
            }
            verify {
                throughput.report(any())
                throughputMeterStep.startStatusCheck(stepStartStopContext)
            }
            confirmVerified(campaignMeterRegistry, throughput, campaignReportLiveStateRegistry)
        }

}