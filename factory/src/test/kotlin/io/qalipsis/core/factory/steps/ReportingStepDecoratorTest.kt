package io.qalipsis.core.factory.steps

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import io.aerisconsulting.catadioptre.getProperty
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.orchestration.factories.Minion
import io.qalipsis.api.report.CampaignStateKeeper
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.steps.Step
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicLong

@WithMockk
internal class ReportingStepDecoratorTest {

    @RelaxedMockK
    lateinit var minion: Minion

    @RelaxedMockK
    private lateinit var decorated: Step<Any, Int>

    @RelaxedMockK
    private lateinit var campaignStateKeeper: CampaignStateKeeper

    @InjectMockKs
    private lateinit var reportingStepDecorator: ReportingStepDecorator<Any, Int>

    @Test
    internal fun `should init the decorated step`() = runBlockingTest {
        // when
        reportingStepDecorator.init()

        // then
        coVerifyOnce { decorated.init() }
    }

    @Test
    internal fun `should destroy the decorated step`() = runBlockingTest {
        // when
        reportingStepDecorator.destroy()

        // then
        coVerifyOnce { decorated.destroy() }
    }

    @Test
    internal fun `should add next step to the decorated step`() = runBlockingTest {
        // given
        val next: Step<Any, Any> = relaxedMockk()

        // when
        reportingStepDecorator.addNext(next)

        // then
        coVerifyOnce { decorated.addNext(refEq(next)) }
    }

    @Test
    internal fun `should start the decorated step and reset the counters`() = runBlockingTest {
        // given
        val startContext: StepStartStopContext = relaxedMockk()
        reportingStepDecorator.getProperty<AtomicLong>("successCount").set(12)
        reportingStepDecorator.getProperty<AtomicLong>("errorCount").set(3)

        // when
        reportingStepDecorator.start(startContext)

        // then
        assertThat(reportingStepDecorator).all {
            typedProp<AtomicLong>("successCount").transform { it.get() }.isEqualTo(0)
            typedProp<AtomicLong>("errorCount").transform { it.get() }.isEqualTo(0)
        }
        coVerifyOnce { decorated.start(refEq(startContext)) }
    }

    @Test
    internal fun `should stop the decorated step and send the report with errors`() = runBlockingTest {
        // given
        val startContext: StepStartStopContext = relaxedMockk {
            every { campaignId } returns "my-campaign"
            every { scenarioId } returns "my-scenario"
        }
        every { decorated.id } returns "the decorated"
        reportingStepDecorator.getProperty<AtomicLong>("successCount").set(12)
        reportingStepDecorator.getProperty<AtomicLong>("errorCount").set(3)

        // when
        reportingStepDecorator.stop(startContext)

        // then
        coVerifyOnce {
            decorated.stop(refEq(startContext))
            campaignStateKeeper.put(
                eq("my-campaign"),
                eq("my-scenario"),
                eq("the decorated"),
                eq(ReportMessageSeverity.ERROR),
                any()
            )
        }
    }

    @Test
    internal fun `should stop the decorated step and send the report with information`() = runBlockingTest {
        // given
        val startContext: StepStartStopContext = relaxedMockk {
            every { campaignId } returns "my-campaign"
            every { scenarioId } returns "my-scenario"
        }
        every { decorated.id } returns "the decorated"
        reportingStepDecorator.getProperty<AtomicLong>("successCount").set(12)
        reportingStepDecorator.getProperty<AtomicLong>("errorCount").set(0)

        // when
        reportingStepDecorator.stop(startContext)

        // then
        coVerifyOnce {
            decorated.stop(refEq(startContext))
            campaignStateKeeper.put(
                eq("my-campaign"),
                eq("my-scenario"),
                eq("the decorated"),
                eq(ReportMessageSeverity.INFO),
                any()
            )
        }
    }

    @Test
    internal fun `should count the execution as a success`() = runBlockingTest {
        // given
        val context: StepContext<Any, Int> = relaxedMockk()
        coJustRun { decorated.execute(any(), any()) }
        val step = ReportingStepDecorator(decorated, campaignStateKeeper)

        // when
        step.execute(minion, context)

        // then
        coVerifyOnce { decorated.execute(refEq(minion), refEq(context)) }
        assertThat(step).all {
            typedProp<AtomicLong>("successCount").transform { it.get() }.isEqualTo(1)
            typedProp<AtomicLong>("errorCount").transform { it.get() }.isEqualTo(0)
        }
    }

    @Test
    internal fun `should count the execution as an error when there is an exception`() = runBlockingTest {
        // given
        val context: StepContext<Any, Int> = relaxedMockk()
        coEvery { decorated.execute(any(), any()) } throws RuntimeException("An error")
        val step = ReportingStepDecorator(decorated, campaignStateKeeper)

        // when
        assertThrows<RuntimeException> {
            step.execute(minion, context)
        }

        // then
        coVerifyOnce { decorated.execute(refEq(minion), refEq(context)) }
        assertThat(step).all {
            typedProp<AtomicLong>("successCount").transform { it.get() }.isEqualTo(0)
            typedProp<AtomicLong>("errorCount").transform { it.get() }.isEqualTo(1)
        }
    }

    @Test
    internal fun `should count the execution as an error when there is no exception but the context switches to exhausted`() =
        runBlockingTest {
            // given
            val context: StepContext<Any, Int> = relaxedMockk()
            coEvery { decorated.execute(any(), any()) } answers {
                // Stubs the context to be exhausted from now on.
                every { context.isExhausted } returns true
            }
            val step = ReportingStepDecorator(decorated, campaignStateKeeper)

            // when
            step.execute(minion, context)

            // then
            coVerifyOnce { decorated.execute(refEq(minion), refEq(context)) }
            assertThat(step).all {
                typedProp<AtomicLong>("successCount").transform { it.get() }.isEqualTo(0)
                typedProp<AtomicLong>("errorCount").transform { it.get() }.isEqualTo(1)
            }
        }

    @Test
    internal fun `should count the execution as a success when the context remains exhausted without exception`() =
        runBlockingTest {
            // given
            val context: StepContext<Any, Int> = relaxedMockk()
            every { context.isExhausted } returns true
            coJustRun { decorated.execute(any(), any()) }
            val step = ReportingStepDecorator(decorated, campaignStateKeeper)

            // when
            step.execute(minion, context)

            // then
            coVerifyOnce { decorated.execute(refEq(minion), refEq(context)) }
            assertThat(step).all {
                typedProp<AtomicLong>("successCount").transform { it.get() }.isEqualTo(1)
                typedProp<AtomicLong>("errorCount").transform { it.get() }.isEqualTo(0)
            }
        }
}
