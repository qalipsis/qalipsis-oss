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
import io.mockk.spyk
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
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
internal class CounterMeterStepTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var campaignReportLiveStateRegistry: CampaignReportLiveStateRegistry

    @RelaxedMockK
    private lateinit var campaignMeterRegistry: CampaignMeterRegistry

    @MockK
    private lateinit var counter: Counter

    @RelaxedMockK
    private lateinit var stepStartStopContext: StepStartStopContext

    @BeforeEach
    internal fun setup() {
        val meterTags = mapOf<String, String>()
        every { stepStartStopContext.toMetersTags() } returns meterTags
        every { stepStartStopContext.scenarioName } returns "my-scenario"
        every { stepStartStopContext.stepName } returns "counter-step"
        every { stepStartStopContext.campaignKey } returns "my-campaign"
        every {
            campaignMeterRegistry.counter(
                scenarioName = "my-scenario",
                stepName = "counter-step",
                name = "counter-meter",
                tags = meterTags
            )
        } returns counter
        every { counter.report(any()) } returns counter
        coJustRun { counter.increment(any<Double>()) }
    }

    @Test
    fun `should execute the step and forward the output without errors`() = testDispatcherProvider.run {
        // given
        val block: (context: StepContext<Int, Int>, input: Int) -> Double =
            { _, _ -> 12.0 }
        val checkers =
            listOf<Pair<Counter.() -> Double, ValueChecker<Double>>>(
                Pair({ 12.60 }, BetweenChecker(12.0, 18.0)),
                Pair({ 12.60 }, LessThanChecker(18.0))
            )
        val counterMeterStep = spyk(
            CounterMeterStep(
                id = "counter-step",
                retryPolicy = null,
                coroutineScope = this,
                campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                meterName = "counter-meter",
                block = block,
                checkers = checkers,
                campaignMeterRegistry = campaignMeterRegistry,
                checkPeriod = Duration.ofMillis(100)
            ), recordPrivateCalls = true
        )
        val latch = SuspendedCountLatch(2, true)
        coEvery { counterMeterStep.checkState(any<StepStartStopContext>()) } coAnswers { latch.decrement() }
        counterMeterStep.start(stepStartStopContext)
        val ctx = createStepContext<Int, Int>(
            input = 1,
            campaignKey = "my-campaign",
            scenarioName = "my-scenario",
            minionId = "my-minion"
        )

        // when
        counterMeterStep.execute(ctx)
        latch.await()
        counterMeterStep.stop(stepStartStopContext)

        // then
        val output = ctx.consumeOutputValue()
        assertEquals(1, output)
        assertFalse((ctx.output as Channel).isClosedForReceive)
        assertFalse(ctx.isExhausted)
        assertThat(counterMeterStep.statusJob()).isNotNull()

        coVerify {
            campaignMeterRegistry.counter(
                scenarioName = "my-scenario",
                stepName = "counter-step",
                name = "counter-meter",
                tags = emptyMap()
            )
            counter.increment(12.0)
            counterMeterStep.checkState(stepStartStopContext)
        }
        coVerifyExactly(3) {
            counterMeterStep.checkState(stepStartStopContext)
        }
        verify {
            counter.report(any())
            counterMeterStep.startStatusCheck(stepStartStopContext)
        }

        confirmVerified(campaignMeterRegistry, counter)
    }

    @Test
    fun `should not start the job to evaluate meter conditions when there are no checkers`() =
        testDispatcherProvider.run {
            // given
            val block: (context: StepContext<Int, Int>, input: Int) -> Double =
                { _, _ -> 12.0 }
            val checkers = emptyList<Pair<Counter.() -> Double, ValueChecker<Double>>>()
            val counterMeterStep = spyk(
                CounterMeterStep(
                    id = "counter-step",
                    retryPolicy = null,
                    coroutineScope = this,
                    campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                    meterName = "counter-meter",
                    block = block,
                    checkers = checkers,
                    campaignMeterRegistry = campaignMeterRegistry,
                    checkPeriod = Duration.ofMillis(100)
                ), recordPrivateCalls = true
            )
            counterMeterStep.start(stepStartStopContext)
            val ctx = createStepContext<Int, Int>(
                input = 1,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion"
            )

            // when
            counterMeterStep.execute(ctx)
            counterMeterStep.stop(stepStartStopContext)

            // then
            val output = ctx.consumeOutputValue()
            assertEquals(1, output)
            assertFalse((ctx.output as Channel).isClosedForReceive)
            assertFalse(ctx.isExhausted)
            assertThat(counterMeterStep.statusJob()).isNull()

            coVerify {
                campaignMeterRegistry.counter(
                    scenarioName = "my-scenario",
                    stepName = "counter-step",
                    name = "counter-meter",
                    tags = emptyMap()
                )
                counter.increment(12.0)
            }
            verify { counter.report(any()) }
            coVerifyOnce { counterMeterStep.checkState(stepStartStopContext) }
            confirmVerified(campaignMeterRegistry, counter)
        }

    @Test
    fun `should report errors to the campaignReportLiveStateRegistry`() =
        testDispatcherProvider.run {
            // given
            val block: (context: StepContext<Int, Int>, input: Int) -> Double =
                { _, _ -> 12.0 }
            val checkers =
                listOf<Pair<Counter.() -> Double, ValueChecker<Double>>>(
                    Pair({ 13.60 }, NotBetweenChecker(12.0, 18.0)),
                    Pair({ 12.60 }, GreaterThanChecker(18.0))
                )
            val latch = SuspendedCountLatch(2, true)
            val counterMeterStep = spyk(
                CounterMeterStep(
                    id = "counter-step",
                    retryPolicy = null,
                    coroutineScope = this,
                    campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                    meterName = "counter-meter",
                    block = block,
                    checkers = checkers,
                    campaignMeterRegistry = campaignMeterRegistry,
                    checkPeriod = Duration.ofMillis(100)
                ), recordPrivateCalls = true
            )
            coEvery { counterMeterStep.checkState(any<StepStartStopContext>()) } coAnswers {
                callOriginal()
                latch.decrement()
            }
            counterMeterStep.start(stepStartStopContext)
            val ctx = createStepContext<Int, Int>(
                input = 1,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion",
                stepName = "counter-step"
            )

            // when
            counterMeterStep.execute(ctx)
            latch.await()
            counterMeterStep.stop(stepStartStopContext)

            // then
            val output = ctx.consumeOutputValue()
            assertEquals(1, output)
            assertFalse((ctx.output as Channel).isClosedForReceive)
            assertFalse(ctx.isExhausted)
            assertThat(counterMeterStep.statusJob()).isNotNull()

            coVerify {
                campaignMeterRegistry.counter(
                    scenarioName = "my-scenario",
                    stepName = "counter-step",
                    name = "counter-meter",
                    tags = emptyMap()
                )
                counter.increment(12.0)

                campaignReportLiveStateRegistry.put(
                    "my-campaign", "my-scenario", "counter-step", ReportMessageSeverity.ERROR, null, """
                    Value 13.6 should not be between bounds: 12.0 and 18.0
                    Value should be greater than 18.0
                """.trimIndent()
                )
            }
            coVerifyExactly(3) {
                counterMeterStep.checkState(stepStartStopContext)
            }
            verify {
                counter.report(any())
                counterMeterStep.startStatusCheck(stepStartStopContext)
            }

            confirmVerified(campaignMeterRegistry, counter, campaignReportLiveStateRegistry)
        }

    @Test
    fun `should delete pre-recorded errors to the campaignReportLiveStateRegistry when checkers return no violations`() =
        testDispatcherProvider.run {
            // given
            val block: (context: StepContext<Int, Int>, input: Int) -> Double =
                { _, _ -> 12.0 }
            val checkers =
                listOf<Pair<Counter.() -> Double, ValueChecker<Double>>>(
                    Pair({ 21.60 }, GreaterThanChecker(18.0))
                )
            val latch = SuspendedCountLatch(2, true)
            val counterMeterStep = spyk(
                CounterMeterStep(
                    id = "counter-step",
                    retryPolicy = null,
                    coroutineScope = this,
                    campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                    meterName = "counter-meter",
                    block = block,
                    checkers = checkers,
                    campaignMeterRegistry = campaignMeterRegistry,
                    checkPeriod = Duration.ofMillis(100)
                ), recordPrivateCalls = true
            )
            coEvery { counterMeterStep.checkState(any<StepStartStopContext>()) } coAnswers {
                callOriginal()
                latch.decrement()
            }
            counterMeterStep.setProperty("messageId", "message-id")
            counterMeterStep.start(stepStartStopContext)
            val ctx = createStepContext<Int, Int>(
                input = 1,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion",
                stepName = "counter-step"
            )

            // when
            counterMeterStep.execute(ctx)
            latch.await()
            counterMeterStep.stop(stepStartStopContext)

            // then
            val output = ctx.consumeOutputValue()
            assertEquals(1, output)
            assertFalse((ctx.output as Channel).isClosedForReceive)
            assertFalse(ctx.isExhausted)
            assertThat(counterMeterStep.statusJob()).isNotNull()

            coVerify {
                campaignMeterRegistry.counter(
                    scenarioName = "my-scenario",
                    stepName = "counter-step",
                    name = "counter-meter",
                    tags = emptyMap()
                )
                counter.increment(12.0)
                campaignReportLiveStateRegistry.delete("my-campaign", "my-scenario", "counter-step", "message-id")
            }
            coVerifyExactly(3) {
                counterMeterStep.checkState(stepStartStopContext)
            }
            verify {
                counter.report(any())
                counterMeterStep.startStatusCheck(stepStartStopContext)
            }

            confirmVerified(campaignMeterRegistry, counter, campaignReportLiveStateRegistry)
        }

}