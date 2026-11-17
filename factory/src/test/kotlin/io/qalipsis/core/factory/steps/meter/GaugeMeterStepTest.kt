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
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.aerisconsulting.catadioptre.setProperty
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.mockk.verify
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Gauge
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.factory.meters.inmemory.valued.InMemoryGauge
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
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper.createStepContext
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

/**
 * @author Francisca Eze
 */

@WithMockk
internal class GaugeMeterStepTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var campaignReportLiveStateRegistry: CampaignReportLiveStateRegistry

    @RelaxedMockK
    private lateinit var campaignMeterRegistry: CampaignMeterRegistry

    @MockK
    private lateinit var gauge: Gauge

    @RelaxedMockK
    private lateinit var stepStartStopContext: StepStartStopContext

    @BeforeEach
    internal fun setup() {
        val meterTags = mapOf<String, String>()
        every { stepStartStopContext.toMetersTags() } returns meterTags
        every { stepStartStopContext.scenarioName } returns "my-scenario"
        every { stepStartStopContext.stepName } returns "gauge-step"
        every { stepStartStopContext.campaignKey } returns "my-campaign"
        every {
            campaignMeterRegistry.gauge(
                scenarioName = "my-scenario",
                stepName = "gauge-step",
                name = "gauge-meter",
                tags = meterTags
            )
        } returns gauge
        every { gauge.report(any()) } returns gauge
    }

    @Test
    fun `should execute the step and forward the output without errors`() = testDispatcherProvider.run {
        // given
        val block: (context: StepContext<Int, Int>, input: Int) -> Double =
            { _, _ -> 12.0 }
        val checkers =
            listOf<Pair<Gauge.() -> Double, ValueChecker<Double>>>(
                Pair({ 12.60 }, BetweenChecker("count", 12.0, 18.0)),
                Pair({ 12.60 }, LessThanChecker("count", 18.0))
            )
        val gaugeMeterStep = spyk(
            GaugeMeterStep(
                id = "gauge-step",
                retryPolicy = null,
                coroutineScope = this,
                campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                meterName = "gauge-meter",
                record = Gauge::set,
                block = block,
                checkers = checkers,
                campaignMeterRegistry = campaignMeterRegistry,
                checkPeriod = Duration.ofMillis(100)
            ), recordPrivateCalls = true
        )
        val latch = SuspendedCountLatch(2, true)
        coEvery { gaugeMeterStep.checkState(any<StepStartStopContext>()) } coAnswers {
            callOriginal()
            latch.decrement()
        }
        every { gauge.set(12.0) } returns 12.0

        gaugeMeterStep.start(stepStartStopContext)
        val ctx = createStepContext<Int, Int>(
            input = 1,
            campaignKey = "my-campaign",
            scenarioName = "my-scenario",
            minionId = "my-minion"
        )

        // when
        gaugeMeterStep.execute(ctx)
        latch.await()
        gaugeMeterStep.stop(stepStartStopContext)

        // then
        val output = ctx.consumeOutputValue()
        assertEquals(1, output)
        assertFalse((ctx.output as Channel).isClosedForReceive)
        assertFalse(ctx.isExhausted)
        assertThat(gaugeMeterStep.statusJob()).isNotNull()

        coVerify {
            campaignMeterRegistry.gauge(
                scenarioName = "my-scenario",
                stepName = "gauge-step",
                name = "gauge-meter",
                tags = emptyMap()
            )
            gauge.set(12.0)
            gaugeMeterStep.checkState(stepStartStopContext)
        }
        coVerifyExactly(3) {
            gaugeMeterStep.checkState(stepStartStopContext)
        }
        verify {
            gauge.report(any())
            gaugeMeterStep.startStatusCheck(stepStartStopContext)
        }

        confirmVerified(campaignMeterRegistry, gauge)
    }

    @Test
    fun `should pass each value to the function resolved for the step, and to that one only`() =
        testDispatcherProvider.run {
            // given
            val levels = listOf(12.0, 5.0, 5.0, 20.0)
            val setValues = mutableListOf<Double>()
            val addedValues = mutableListOf<Double>()
            every { gauge.set(capture(setValues)) } returns 0.0
            every { gauge.increment(capture(addedValues)) } returns 0.0

            // when
            listOf<Gauge.(Double) -> Double>(Gauge::set, Gauge::increment).forEach { record ->
                val values = levels.iterator()
                val gaugeMeterStep = GaugeMeterStep(
                    id = "gauge-step",
                    retryPolicy = null,
                    coroutineScope = this,
                    campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                    meterName = "gauge-meter",
                    record = record,
                    block = { _, _: Int -> values.next() },
                    checkers = emptyList(),
                    campaignMeterRegistry = campaignMeterRegistry,
                    checkPeriod = Duration.ofMillis(100)
                )
                gaugeMeterStep.start(stepStartStopContext)
                repeat(levels.size) {
                    gaugeMeterStep.execute(
                        createStepContext(
                            input = 1,
                            campaignKey = "my-campaign",
                            scenarioName = "my-scenario",
                            minionId = "my-minion"
                        )
                    )
                }
                gaugeMeterStep.stop(stepStartStopContext)
            }

            // then
            assertThat(setValues).isEqualTo(levels)
            assertThat(addedValues).isEqualTo(levels)
        }

    @Test
    fun `should evaluate the expectations on the level of a real gauge and not on the sum of the levels`() =
        testDispatcherProvider.run {
            // given
            // A real gauge, to verify what the step leaves in it and what the checkers then receive.
            val realGauge = InMemoryGauge(mockk(relaxed = true), relaxedMockk())
            every {
                campaignMeterRegistry.gauge(
                    scenarioName = "my-scenario",
                    stepName = "gauge-step",
                    name = "consumer-lag",
                    tags = emptyMap()
                )
            } returns realGauge
            // Levels of a queue depth: it grows, peaks far above the threshold, then goes back down.
            val levels = listOf(100.0, 300.0, 4200.0, 50.0).iterator()
            val gaugeMeterStep = GaugeMeterStep(
                id = "gauge-step",
                retryPolicy = null,
                coroutineScope = this,
                campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                meterName = "consumer-lag",
                record = Gauge::set,
                block = { _, _: Int -> levels.next() },
                checkers = listOf<Pair<Gauge.() -> Double, ValueChecker<Double>>>(
                    Pair({ value() }, LessThanChecker("value", 500.0))
                ),
                campaignMeterRegistry = campaignMeterRegistry,
                checkPeriod = Duration.ofMinutes(1)
            )
            gaugeMeterStep.start(stepStartStopContext)

            // when
            val observedValues = mutableListOf<Double>()
            repeat(4) {
                gaugeMeterStep.execute(
                    createStepContext(
                        input = 1,
                        campaignKey = "my-campaign",
                        scenarioName = "my-scenario",
                        minionId = "my-minion"
                    )
                )
                observedValues += realGauge.value()
                gaugeMeterStep.checkState(stepStartStopContext)
            }

            // then
            // The gauge holds the levels instead of adding them up, which would give 100, 400, 4600, 4650.
            assertThat(observedValues).isEqualTo(listOf(100.0, 300.0, 4200.0, 50.0))

            // Only the level above the threshold is a violation, and it is reported with its own value.
            val messages = mutableListOf<String>()
            coVerify {
                campaignReportLiveStateRegistry.put(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    stepName = "gauge-step",
                    severity = ReportMessageSeverity.ERROR,
                    messageId = null,
                    message = capture(messages)
                )
            }
            assertThat(messages).isEqualTo(listOf("The value is 4200.0 but should be less than 500.0"))

            // The last level is below the threshold again, so the message is removed from the report.
            coVerifyOnce {
                campaignReportLiveStateRegistry.delete(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    stepName = "gauge-step",
                    messageId = any()
                )
            }
        }

    @Test
    fun `should not start the job to evaluate meter conditions when there are no checkers`() =
        testDispatcherProvider.run {
            // given
            val block: (context: StepContext<Int, Int>, input: Int) -> Double =
                { _, _ -> 12.0 }
            val checkers = emptyList<Pair<Gauge.() -> Double, ValueChecker<Double>>>()
            val gaugeMeterStep = spyk(
                GaugeMeterStep(
                    id = "gauge-step",
                    retryPolicy = null,
                    coroutineScope = this,
                    campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                    meterName = "gauge-meter",
                    record = Gauge::set,
                    block = block,
                    checkers = checkers,
                    campaignMeterRegistry = campaignMeterRegistry,
                    checkPeriod = Duration.ofMillis(100)
                ), recordPrivateCalls = true
            )
            gaugeMeterStep.start(stepStartStopContext)
            every { gauge.set(12.0) } returns 12.0
            val ctx = createStepContext<Int, Int>(
                input = 1,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion"
            )

            // when
            gaugeMeterStep.execute(ctx)
            gaugeMeterStep.stop(stepStartStopContext)

            // then
            val output = ctx.consumeOutputValue()
            assertEquals(1, output)
            assertFalse((ctx.output as Channel).isClosedForReceive)
            assertFalse(ctx.isExhausted)
            assertThat(gaugeMeterStep.statusJob()).isNull()

            coVerify {
                campaignMeterRegistry.gauge(
                    scenarioName = "my-scenario",
                    stepName = "gauge-step",
                    name = "gauge-meter",
                    tags = emptyMap()
                )
                gauge.set(12.0)
            }
            verify { gauge.report(any()) }
            coVerifyOnce { gaugeMeterStep.checkState(stepStartStopContext) }
            confirmVerified(campaignMeterRegistry, gauge)
        }

    @Test
    fun `should report errors to the campaignReportLiveStateRegistry`() =
        testDispatcherProvider.run {
            // given
            val block: (context: StepContext<Int, Int>, input: Int) -> Double =
                { _, _ -> 12.0 }
            val checkers =
                listOf<Pair<Gauge.() -> Double, ValueChecker<Double>>>(
                    Pair({ 13.60 }, NotBetweenChecker("count", 12.0, 18.0)),
                    Pair({ 12.60 }, GreaterThanChecker("count", 18.0))
                )
            val latch = SuspendedCountLatch(2, true)
            val gaugeMeterStep = spyk(
                GaugeMeterStep(
                    id = "gauge-step",
                    retryPolicy = null,
                    coroutineScope = this,
                    campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                    meterName = "gauge-meter",
                    record = Gauge::set,
                    block = block,
                    checkers = checkers,
                    campaignMeterRegistry = campaignMeterRegistry,
                    checkPeriod = Duration.ofMillis(100)
                ), recordPrivateCalls = true
            )
            coEvery { gaugeMeterStep.checkState(any<StepStartStopContext>()) } coAnswers {
                callOriginal()
                latch.decrement()
            }
            every { gauge.set(12.0) } returns 12.0
            gaugeMeterStep.start(stepStartStopContext)
            val ctx = createStepContext<Int, Int>(
                input = 1,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion",
                stepName = "gauge-step"
            )

            // when
            gaugeMeterStep.execute(ctx)
            latch.await()
            gaugeMeterStep.stop(stepStartStopContext)

            // then
            val output = ctx.consumeOutputValue()
            assertEquals(1, output)
            assertFalse((ctx.output as Channel).isClosedForReceive)
            assertFalse(ctx.isExhausted)
            assertThat(gaugeMeterStep.statusJob()).isNotNull()

            coVerify {
                campaignMeterRegistry.gauge(
                    scenarioName = "my-scenario",
                    stepName = "gauge-step",
                    name = "gauge-meter",
                    tags = emptyMap()
                )
                gauge.set(12.0)

                campaignReportLiveStateRegistry.put(
                    "my-campaign", "my-scenario", "gauge-step", ReportMessageSeverity.ERROR, null, """
                    The count is 13.6 but should not be between bounds: 12.0 and 18.0
                    The count is 12.6 but should be greater than 18.0
                """.trimIndent()
                )
            }
            coVerifyExactly(3) {
                gaugeMeterStep.checkState(stepStartStopContext)
            }
            verify {
                gauge.report(any())
                gaugeMeterStep.startStatusCheck(stepStartStopContext)
            }

            confirmVerified(campaignMeterRegistry, gauge, campaignReportLiveStateRegistry)
        }

    @Test
    fun `should delete pre-recorded errors to the campaignReportLiveStateRegistry when checkers return no violations`() =
        testDispatcherProvider.run {
            // given
            val block: (context: StepContext<Int, Int>, input: Int) -> Double =
                { _, _ -> 12.0 }
            val checkers =
                listOf<Pair<Gauge.() -> Double, ValueChecker<Double>>>(
                    Pair({ 21.60 }, GreaterThanChecker("count", 18.0))
                )
            val latch = SuspendedCountLatch(2, true)

            val gaugeMeterStep = spyk(
                GaugeMeterStep(
                    id = "gauge-step",
                    retryPolicy = null,
                    coroutineScope = this,
                    campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                    meterName = "gauge-meter",
                    record = Gauge::set,
                    block = block,
                    checkers = checkers,
                    campaignMeterRegistry = campaignMeterRegistry,
                    checkPeriod = Duration.ofMillis(100)
                ), recordPrivateCalls = true
            )
            coEvery { gaugeMeterStep.checkState(any<StepStartStopContext>()) } coAnswers {
                callOriginal()
                latch.decrement()
            }
            every { gauge.set(12.0) } returns 12.0
            gaugeMeterStep.setProperty("messageId", "message-id")
            gaugeMeterStep.start(stepStartStopContext)
            val ctx = createStepContext<Int, Int>(
                input = 1,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion",
                stepName = "gauge-step"
            )

            // when
            gaugeMeterStep.execute(ctx)
            latch.await()
            gaugeMeterStep.stop(stepStartStopContext)

            // then
            val output = ctx.consumeOutputValue()
            assertEquals(1, output)
            assertFalse((ctx.output as Channel).isClosedForReceive)
            assertFalse(ctx.isExhausted)
            assertThat(gaugeMeterStep.statusJob()).isNotNull()

            coVerify {
                campaignMeterRegistry.gauge(
                    scenarioName = "my-scenario",
                    stepName = "gauge-step",
                    name = "gauge-meter",
                    tags = emptyMap()
                )
                gauge.set(12.0)
                campaignReportLiveStateRegistry.delete("my-campaign", "my-scenario", "gauge-step", any())
            }
            coVerifyExactly(3) {
                gaugeMeterStep.checkState(stepStartStopContext)
            }
            verify {
                gauge.report(any())
                gaugeMeterStep.startStatusCheck(stepStartStopContext)
            }

            confirmVerified(campaignMeterRegistry, gauge, campaignReportLiveStateRegistry)
        }

}