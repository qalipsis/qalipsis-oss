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
import io.qalipsis.api.meters.Timer
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
internal class TimerMeterStepTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var campaignReportLiveStateRegistry: CampaignReportLiveStateRegistry

    @MockK
    private lateinit var campaignMeterRegistry: CampaignMeterRegistry

    @MockK
    private lateinit var timer: Timer

    @RelaxedMockK
    private lateinit var stepStartStopContext: StepStartStopContext

    private val meterTags = mapOf<String, String>()

    @BeforeEach
    internal fun setup() {
        every { stepStartStopContext.toMetersTags() } returns meterTags
        every { stepStartStopContext.scenarioName } returns "my-scenario"
        every { stepStartStopContext.stepName } returns "timer-step"
        every { stepStartStopContext.campaignKey } returns "my-campaign"
        every { timer.report(any()) } returns timer
        coJustRun { timer.record(any<Duration>()) }
    }

    @Test
    fun `should execute the step and forward the output without errors`() = testDispatcherProvider.run {
        // given
        val block: (context: StepContext<Int, Int>, input: Int) -> Duration =
            { _, _ -> Duration.ofMillis(1000) }
        val checkers =
            listOf<Pair<Timer.() -> Duration, ValueChecker<Duration>>>(
                Pair({ Duration.ofMillis(1200) }, BetweenChecker(Duration.ofMillis(1000), Duration.ofMillis(2000))),
                Pair({ Duration.ofMillis(1200) }, LessThanChecker(Duration.ofMillis(300))),
            )
        every {
            campaignMeterRegistry.timer(
                scenarioName = "my-scenario",
                stepName = "timer-step",
                name = "timer-meter",
                tags = meterTags,
                percentiles = setOf(95.0, 99.0)
            )
        } returns timer
        val timerMeterStep = spyk(
            TimerMeterStep(
                id = "timer-step",
                retryPolicy = null,
                coroutineScope = this,
                campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                meterName = "timer-meter",
                percentiles = setOf(95.0, 99.0),
                block = block,
                checkers = checkers,
                campaignMeterRegistry = campaignMeterRegistry,
                checkPeriod = Duration.ofMillis(100)
            ), recordPrivateCalls = true
        )
        val latch = SuspendedCountLatch(2, true)
        coEvery { timerMeterStep.checkState(any<StepStartStopContext>()) } coAnswers {
            callOriginal()
            latch.decrement()
        }
        timerMeterStep.start(stepStartStopContext)
        val ctx = createStepContext<Int, Int>(
            input = 1,
            campaignKey = "my-campaign",
            scenarioName = "my-scenario",
            minionId = "my-minion"
        )

        // when
        timerMeterStep.execute(ctx)
        latch.await()
        timerMeterStep.stop(stepStartStopContext)

        // then
        val output = ctx.consumeOutputValue()
        assertEquals(1, output)
        assertFalse((ctx.output as Channel).isClosedForReceive)
        assertFalse(ctx.isExhausted)
        assertThat(timerMeterStep.statusJob()).isNotNull()

        coVerify {
            campaignMeterRegistry.timer(
                scenarioName = "my-scenario",
                stepName = "timer-step",
                name = "timer-meter",
                tags = emptyMap(),
                percentiles = setOf(95.0, 99.0)
            )
            timer.record(Duration.ofMillis(1000))
            timerMeterStep.checkState(stepStartStopContext)
        }
        coVerifyExactly(3) {
            timerMeterStep.checkState(stepStartStopContext)
        }
        verify {
            timer.report(any())
            timerMeterStep.startStatusCheck(stepStartStopContext)
        }

        confirmVerified(campaignMeterRegistry, timer)
    }

    @Test
    fun `should not start the job to evaluate meter conditions when there are no checkers`() =
        testDispatcherProvider.run {
            // given
            val block: (context: StepContext<Int, Int>, input: Int) -> Duration =
                { _, _ -> Duration.ofMillis(1000) }
            val checkers = emptyList<Pair<Timer.() -> Duration, ValueChecker<Duration>>>()
            val timerMeterStep = spyk(
                TimerMeterStep(
                    id = "timer-step",
                    retryPolicy = null,
                    coroutineScope = this,
                    campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                    meterName = "timer-meter",
                    block = block,
                    checkers = checkers,
                    campaignMeterRegistry = campaignMeterRegistry,
                    percentiles = setOf(95.0, 99.0),
                    checkPeriod = Duration.ofMillis(100),
                ), recordPrivateCalls = true
            )
            every {
                campaignMeterRegistry.timer(
                    scenarioName = "my-scenario",
                    stepName = "timer-step",
                    name = "timer-meter",
                    tags = meterTags,
                    percentiles = setOf(95.0, 99.0)
                )
            } returns timer
            timerMeterStep.start(stepStartStopContext)
            val ctx = createStepContext<Int, Int>(
                input = 1,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion"
            )

            // when
            timerMeterStep.execute(ctx)
            timerMeterStep.stop(stepStartStopContext)

            // then
            val output = ctx.consumeOutputValue()
            assertEquals(1, output)
            assertFalse((ctx.output as Channel).isClosedForReceive)
            assertFalse(ctx.isExhausted)
            assertThat(timerMeterStep.statusJob()).isNull()

            coVerify {
                campaignMeterRegistry.timer(
                    scenarioName = "my-scenario",
                    stepName = "timer-step",
                    name = "timer-meter",
                    tags = emptyMap(),
                    percentiles = setOf(95.0, 99.0)
                )
                timer.record(Duration.ofMillis(1000))
            }
            verify { timer.report(any()) }
            coVerifyOnce { timerMeterStep.checkState(stepStartStopContext) }
            confirmVerified(campaignMeterRegistry, timer)
        }

    @Test
    fun `should report errors to the campaignReportLiveStateRegistry`() =
        testDispatcherProvider.run {
            // given
            val block: (context: StepContext<Int, Int>, input: Int) -> Duration =
                { _, _ -> Duration.ofMillis(10000) }

            val checkers =
                listOf<Pair<Timer.() -> Duration, ValueChecker<Duration>>>(
                    Pair(
                        { Duration.ofMillis(1200) },
                        NotBetweenChecker(Duration.ofMillis(1000), Duration.ofMillis(2000))
                    ),
                    Pair({ Duration.ofMillis(1200) }, GreaterThanChecker(Duration.ofMillis(300))),
                )
            val latch = SuspendedCountLatch(2, true)
            val timerMeterStep = spyk(
                TimerMeterStep(
                    id = "timer-step",
                    retryPolicy = null,
                    coroutineScope = this,
                    campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                    meterName = "timer-meter",
                    block = block,
                    checkers = checkers,
                    campaignMeterRegistry = campaignMeterRegistry,
                    percentiles = setOf(95.0, 99.0),
                    checkPeriod = Duration.ofMillis(100)
                ), recordPrivateCalls = true
            )
            every {
                campaignMeterRegistry.timer(
                    scenarioName = "my-scenario",
                    stepName = "timer-step",
                    name = "timer-meter",
                    tags = meterTags,
                    percentiles = setOf(95.0, 99.0)
                )
            } returns timer
            coEvery { timerMeterStep.checkState(any<StepStartStopContext>()) } coAnswers {
                callOriginal()
                latch.decrement()
            }
            timerMeterStep.start(stepStartStopContext)
            val ctx = createStepContext<Int, Int>(
                input = 1,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion",
                stepName = "timer-step"
            )

            // when
            timerMeterStep.execute(ctx)
            latch.await()
            timerMeterStep.stop(stepStartStopContext)

            // then
            val output = ctx.consumeOutputValue()
            assertEquals(1, output)
            assertFalse((ctx.output as Channel).isClosedForReceive)
            assertFalse(ctx.isExhausted)
            assertThat(timerMeterStep.statusJob()).isNotNull()

            coVerify {
                campaignMeterRegistry.timer(
                    scenarioName = "my-scenario",
                    stepName = "timer-step",
                    name = "timer-meter",
                    tags = emptyMap(),
                    percentiles = setOf(95.0, 99.0)
                )
                timer.record(Duration.ofMillis(10000))

                campaignReportLiveStateRegistry.put(
                    "my-campaign", "my-scenario", "timer-step", ReportMessageSeverity.ERROR, null, """
                    Value PT1.2S should not be between bounds: PT1S and PT2S
                """.trimIndent()
                )
            }
            coVerifyExactly(3) {
                timerMeterStep.checkState(stepStartStopContext)
            }
            verify {
                timer.report(any())
                timerMeterStep.startStatusCheck(stepStartStopContext)
            }

            confirmVerified(campaignMeterRegistry, timer, campaignReportLiveStateRegistry)
        }

    @Test
    fun `should delete pre-recorded errors to the campaignReportLiveStateRegistry when checkers return no violations`() =
        testDispatcherProvider.run {
            // given
            val block: (context: StepContext<Int, Int>, input: Int) -> Duration =
                { _, _ -> Duration.ofMillis(10000) }

            val checkers =
                listOf<Pair<Timer.() -> Duration, ValueChecker<Duration>>>(
                    Pair({ Duration.ofMillis(1200) }, GreaterThanChecker(Duration.ofMillis(300))),
                )
            val latch = SuspendedCountLatch(2, true)
            val timerMeterStep = spyk(
                TimerMeterStep(
                    id = "timer-step",
                    retryPolicy = null,
                    coroutineScope = this,
                    campaignReportLiveStateRegistry = campaignReportLiveStateRegistry,
                    meterName = "timer-meter",
                    block = block,
                    checkers = checkers,
                    percentiles = setOf(95.0, 99.0),
                    checkPeriod = Duration.ofMillis(100),
                    campaignMeterRegistry = campaignMeterRegistry
                ), recordPrivateCalls = true
            )
            every {
                campaignMeterRegistry.timer(
                    scenarioName = "my-scenario",
                    stepName = "timer-step",
                    name = "timer-meter",
                    tags = meterTags,
                    percentiles = setOf(95.0, 99.0)
                )
            } returns timer
            coEvery { timerMeterStep.checkState(any<StepStartStopContext>()) } coAnswers {
                callOriginal()
                latch.decrement()
            }
            timerMeterStep.setProperty("messageId", "message-id")
            timerMeterStep.start(stepStartStopContext)
            val ctx = createStepContext<Int, Int>(
                input = 1,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion",
                stepName = "timer-step"
            )

            // when
            timerMeterStep.execute(ctx)
            latch.await()
            timerMeterStep.stop(stepStartStopContext)

            // then
            val output = ctx.consumeOutputValue()
            assertEquals(1, output)
            assertFalse((ctx.output as Channel).isClosedForReceive)
            assertFalse(ctx.isExhausted)
            assertThat(timerMeterStep.statusJob()).isNotNull()

            coVerify {
                campaignMeterRegistry.timer(
                    scenarioName = "my-scenario",
                    stepName = "timer-step",
                    name = "timer-meter",
                    tags = emptyMap(),
                    percentiles = setOf(95.0, 99.0)
                )
                timer.record(Duration.ofMillis(10000))

                campaignReportLiveStateRegistry.delete("my-campaign", "my-scenario", "timer-step", "message-id")
            }
            coVerifyExactly(3) {
                timerMeterStep.checkState(stepStartStopContext)
            }
            verify {
                timer.report(any())
                timerMeterStep.startStatusCheck(stepStartStopContext)
            }

            confirmVerified(campaignMeterRegistry, timer, campaignReportLiveStateRegistry)
        }

}