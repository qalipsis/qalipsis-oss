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

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.micrometer.core.instrument.Tag
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.meters.CampaignMeterRegistry
import io.qalipsis.api.meters.Counter
import io.qalipsis.api.meters.Timer
import io.qalipsis.api.report.CampaignReportLiveStateRegistry
import io.qalipsis.api.runtime.Minion
import io.qalipsis.api.steps.Step
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

@WithMockk
internal class ReportingStepDecoratorTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var minion: Minion

    @RelaxedMockK
    private lateinit var decorated: Step<Any, Int>

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var reportLiveStateRegistry: CampaignReportLiveStateRegistry

    @RelaxedMockK
    private lateinit var meterRegistry: CampaignMeterRegistry

    @RelaxedMockK
    private lateinit var startStopContext: StepStartStopContext

    private val stepStartTimeout: Duration = Duration.ofMillis(30)

    private val reportErrors: Boolean = false

    @InjectMockKs
    private lateinit var reportingStepDecorator: ReportingStepDecorator<Any, Int>

    @RelaxedMockK
    private lateinit var runningStepsGauge: AtomicInteger

    @RelaxedMockK
    private lateinit var executedStepCounter: Counter

    @RelaxedMockK
    private lateinit var completionTimer: Timer

    @RelaxedMockK
    private lateinit var failureTimer: Timer

    @BeforeEach
    internal fun setUp() {
        every { decorated.retryPolicy } returns null
        every { decorated.name } returns "the decorated"

        every { startStopContext.campaignKey } returns "my-campaign"
        every { startStopContext.scenarioName } returns "my-scenario"

        every { meterRegistry.gauge("running-steps", any<List<Tag>>(), any<AtomicInteger>()) } returns runningStepsGauge
        every { meterRegistry.counter("executed-steps", "scenario", "my-scenario") } returns executedStepCounter

        every {
            meterRegistry.timer(
                "step-execution",
                "step",
                "the decorated",
                "status",
                "completed"
            )
        } returns completionTimer
        every {
            meterRegistry.timer(
                "step-execution",
                "step",
                "the decorated",
                "status",
                "failed"
            )
        } returns failureTimer
    }

    @Test
    internal fun `should init the decorated step`() = testDispatcherProvider.run {
        // when
        reportingStepDecorator.init()

        // then
        coVerifyOnce { decorated.init() }
    }

    @Test
    internal fun `should destroy the decorated step`() = testDispatcherProvider.run {
        // when
        reportingStepDecorator.destroy()

        // then
        coVerifyOnce { decorated.destroy() }
    }

    @Test
    internal fun `should add next step to the decorated step`() = testDispatcherProvider.run {
        // given
        val next: Step<Any, Any> = relaxedMockk()

        // when
        reportingStepDecorator.addNext(next)

        // then
        coVerifyOnce { decorated.addNext(refEq(next)) }
    }

    @Test
    internal fun `should start the decorated step and reset the meters`() = testDispatcherProvider.run {
        // when
        reportingStepDecorator.start(startStopContext)

        // then
        coVerify {
            decorated.start(refEq(startStopContext))
            meterRegistry.gauge("running-steps", any<List<Tag>>(), any<AtomicInteger>())
            meterRegistry.counter("executed-steps", "scenario", "my-scenario")
            meterRegistry.timer("step-execution", "step", "the decorated", "status", "completed")
            meterRegistry.timer("step-execution", "step", "the decorated", "status", "failed")
            reportLiveStateRegistry.recordSuccessfulStepInitialization(
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                stepName = "the decorated"
            )
        }
        confirmVerified(
            meterRegistry,
            eventsLogger,
            runningStepsGauge,
            failureTimer,
            completionTimer,
            executedStepCounter,
            reportLiveStateRegistry
        )
    }


    @Test
    internal fun `should start the decorated step with failure`() = testDispatcherProvider.run {
        // given
        val failure = RuntimeException("An error")
        coEvery { decorated.start(any()) } throws failure

        // when
        val exception = assertThrows<RuntimeException> {
            reportingStepDecorator.start(startStopContext)
        }

        // then
        assertThat(exception.message).isEqualTo("An error")
        coVerifyOnce {
            decorated.start(refEq(startStopContext))
            reportLiveStateRegistry.recordFailedStepInitialization(
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                stepName = "the decorated",
                cause = withArg {
                    assertThat(it).isInstanceOf<RuntimeException>().prop(RuntimeException::message)
                        .isEqualTo("An error")
                }
            )
        }
        confirmVerified(
            meterRegistry,
            eventsLogger,
            runningStepsGauge,
            failureTimer,
            completionTimer,
            executedStepCounter,
            reportLiveStateRegistry
        )
    }

    @Test
    internal fun `should stop the decorated step`() = testDispatcherProvider.run {
        // when
        reportingStepDecorator.stop(startStopContext)

        // then
        coVerifyOnce {
            decorated.stop(refEq(startStopContext))
        }
        confirmVerified(
            eventsLogger,
            runningStepsGauge,
            failureTimer,
            completionTimer,
            executedStepCounter,
            reportLiveStateRegistry
        )
    }

    @Test
    internal fun `should count the execution as a success`() = testDispatcherProvider.run {
        // given
        val context: StepContext<Any, Int> = relaxedMockk {
            every { campaignKey } returns "my-campaign"
            every { scenarioName } returns "my-scenario"
        }
        coJustRun { decorated.execute(any(), any()) }
        val step = ReportingStepDecorator(decorated, true, eventsLogger, meterRegistry, reportLiveStateRegistry)
        step.start(startStopContext)

        // when
        step.execute(minion, context)

        // then
        coVerifyOnce {
            reportLiveStateRegistry.recordSuccessfulStepInitialization(
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                stepName = "the decorated"
            )
            eventsLogger.debug("step.execution.started", timestamp = any(), tagsSupplier = any())
            runningStepsGauge.incrementAndGet()
            decorated.execute(refEq(minion), refEq(context))
            eventsLogger.debug("step.execution.complete", timestamp = any(), tagsSupplier = any())
            completionTimer.record(any<Duration>())
            reportLiveStateRegistry.recordSuccessfulStepExecution(
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                stepName = "the decorated"
            )
            runningStepsGauge.decrementAndGet()
            executedStepCounter.increment()
        }
        confirmVerified(
            eventsLogger,
            runningStepsGauge,
            failureTimer,
            completionTimer,
            executedStepCounter,
            reportLiveStateRegistry
        )
    }

    @Test
    internal fun `should count the execution as a success but not return when the decorated step is invisible`() =
        testDispatcherProvider.run {
            // given
            every { decorated.name } returns "__the decorated"
            val context: StepContext<Any, Int> = relaxedMockk {
                every { campaignKey } returns "my-campaign"
                every { scenarioName } returns "my-scenario"
            }
            coJustRun { decorated.execute(any(), any()) }
            val step = ReportingStepDecorator(decorated, true, eventsLogger, meterRegistry, reportLiveStateRegistry)
            step.start(startStopContext)

            // when
            step.execute(minion, context)

            // then
            coVerifyOnce {
                reportLiveStateRegistry.recordSuccessfulStepInitialization(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    stepName = "__the decorated"
                )
                decorated.execute(refEq(minion), refEq(context))
            }
            confirmVerified(
                eventsLogger,
                runningStepsGauge,
                failureTimer,
                completionTimer,
                executedStepCounter,
                reportLiveStateRegistry
            )
    }

    @Test
    internal fun `should count the execution as an error when there is an exception`() =
        testDispatcherProvider.run {
            // given
            val context: StepContext<Any, Int> = relaxedMockk {
                every { campaignKey } returns "my-campaign"
                every { scenarioName } returns "my-scenario"
            }
            val error = RuntimeException("An error")
            coEvery { decorated.execute(any(), any()) } throws error
            val step = ReportingStepDecorator(decorated, true, eventsLogger, meterRegistry, reportLiveStateRegistry)
            step.start(startStopContext)

            // when
            assertThrows<RuntimeException> {
                step.execute(minion, context)
            }

            // then
            coVerifyOnce {
                reportLiveStateRegistry.recordSuccessfulStepInitialization(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    stepName = "the decorated"
                )
                eventsLogger.debug("step.execution.started", timestamp = any(), tagsSupplier = any())
                runningStepsGauge.incrementAndGet()
                decorated.execute(refEq(minion), refEq(context))
                eventsLogger.warn("step.execution.failed", refEq(error), timestamp = any(), tagsSupplier = any())
                failureTimer.record(any<Duration>())
                reportLiveStateRegistry.recordFailedStepExecution(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    stepName = "the decorated",
                    cause = refEq(error)
                )
                runningStepsGauge.decrementAndGet()
                executedStepCounter.increment()
            }
            confirmVerified(
                eventsLogger,
                runningStepsGauge,
                failureTimer,
                completionTimer,
                executedStepCounter,
                reportLiveStateRegistry
            )
        }

    @Test
    internal fun `should count the execution as an error when there is an exception but not report the exact failure`() =
        testDispatcherProvider.run {
            // given
            val context: StepContext<Any, Int> = relaxedMockk {
                every { campaignKey } returns "my-campaign"
                every { scenarioName } returns "my-scenario"
            }
            val error = RuntimeException("An error")
            coEvery { decorated.execute(any(), any()) } throws error
            val step = ReportingStepDecorator(decorated, false, eventsLogger, meterRegistry, reportLiveStateRegistry)
            step.start(startStopContext)

            // when
            assertThrows<RuntimeException> {
                step.execute(minion, context)
            }

            // then
            coVerifyOnce {
                reportLiveStateRegistry.recordSuccessfulStepInitialization(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    stepName = "the decorated"
                )
                eventsLogger.debug("step.execution.started", timestamp = any(), tagsSupplier = any())
                runningStepsGauge.incrementAndGet()
                decorated.execute(refEq(minion), refEq(context))
                eventsLogger.warn("step.execution.failed", refEq(error), timestamp = any(), tagsSupplier = any())
                failureTimer.record(any<Duration>())
                reportLiveStateRegistry.recordFailedStepExecution(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    stepName = "the decorated",
                    cause = null
                )
                runningStepsGauge.decrementAndGet()
                executedStepCounter.increment()
            }
            confirmVerified(
                eventsLogger,
                runningStepsGauge,
                failureTimer,
                completionTimer,
                executedStepCounter,
                reportLiveStateRegistry
            )
        }

    @Test
    internal fun `should count the execution as an error when there is an exception but not report when the decorated step is invisible`() =
        testDispatcherProvider.run {
            // given
            every { decorated.name } returns "__the decorated"
            val context: StepContext<Any, Int> = relaxedMockk {
                every { campaignKey } returns "my-campaign"
                every { scenarioName } returns "my-scenario"
            }
            val error = RuntimeException("An error")
            coEvery { decorated.execute(any(), any()) } throws error
            val step = ReportingStepDecorator(decorated, true, eventsLogger, meterRegistry, reportLiveStateRegistry)
            step.start(startStopContext)

            // when
            assertThrows<RuntimeException> {
                step.execute(minion, context)
            }

            // then
            coVerifyOnce {
                reportLiveStateRegistry.recordSuccessfulStepInitialization(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    stepName = "__the decorated"
                )
                decorated.execute(refEq(minion), refEq(context))
                eventsLogger.warn("minion.operation.failed", refEq(error), timestamp = any(), tagsSupplier = any())
            }
            confirmVerified(
                eventsLogger,
                runningStepsGauge,
                failureTimer,
                completionTimer,
                executedStepCounter,
                reportLiveStateRegistry
            )
        }

    @Test
    internal fun `should count the execution as an error when there is no exception but the context switches to exhausted`() =
        testDispatcherProvider.run {
            // given
            val context: StepContext<Any, Int> = relaxedMockk()
            coEvery { decorated.execute(any(), any()) } answers {
                // Stubs the context to be exhausted from now on.
                every { context.isExhausted } returns true
            }
            val step = ReportingStepDecorator(decorated, true, eventsLogger, meterRegistry, reportLiveStateRegistry)
            step.start(startStopContext)

            // when
            step.execute(minion, context)

            // then
            coVerifyOnce { decorated.execute(refEq(minion), refEq(context)) }
        }

    @Test
    internal fun `should count the execution as a success when the context remains exhausted without exception`() =
        testDispatcherProvider.run {
            // given
            val context: StepContext<Any, Int> = relaxedMockk()
            every { context.isExhausted } returns true
            coJustRun { decorated.execute(any(), any()) }
            val step = ReportingStepDecorator(decorated, true, eventsLogger, meterRegistry, reportLiveStateRegistry)
            step.start(startStopContext)

            // when
            step.execute(minion, context)

            // then
            coVerifyOnce { decorated.execute(refEq(minion), refEq(context)) }
        }

}
