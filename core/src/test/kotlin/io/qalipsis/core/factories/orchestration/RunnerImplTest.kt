package io.qalipsis.core.factories.orchestration

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.events.EventsLogger
import io.qalipsis.api.report.CampaignStateKeeper
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.factories.steps
import io.qalipsis.core.factories.testDag
import io.qalipsis.core.factories.testStep
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyExactly
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.mockk.verifyOnce
import io.qalipsis.test.time.QalipsisTimeAssertions
import io.qalipsis.test.time.measureTime
import io.qalipsis.test.utils.setProperty
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
@WithMockk
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
internal class RunnerImplTest {

    @RelaxedMockK
    private lateinit var eventsLogger: EventsLogger

    @RelaxedMockK
    private lateinit var campaignStateKeeper: CampaignStateKeeper

    @RelaxedMockK
    private lateinit var meterRegistry: MeterRegistry

    @BeforeEach
    internal fun setUp() {
        val defaultGauge: AtomicInteger = relaxedMockk()
        val defaultCounter: Counter = relaxedMockk()
        val defaultTimer: Timer = relaxedMockk()

        every { meterRegistry.gauge(any(), any<AtomicInteger>()) } returns defaultGauge
        every { meterRegistry.gauge(any(), any<List<Tag>>(), any<AtomicInteger>()) } returns defaultGauge
        every { meterRegistry.counter(any()) } returns defaultCounter
        every { meterRegistry.timer(any(), *anyVararg()) } returns defaultTimer
    }

    @Test
    @Timeout(10)
    internal fun `should execute the full dag asynchronously and update meters`() = runBlockingTest {
        // given
        val runningStepsGauge: AtomicInteger = relaxedMockk()
        val idleMinionsGauge: AtomicInteger = relaxedMockk()
        val runningMinionsGauge: AtomicInteger = relaxedMockk()
        val executedStepCounter: Counter = relaxedMockk()
        val stepExecutionTimer: Timer = relaxedMockk()

        every { meterRegistry.gauge("idle-minions", any<AtomicInteger>()) } returns idleMinionsGauge
        every { meterRegistry.gauge("running-minions", any<AtomicInteger>()) } returns runningMinionsGauge
        every { meterRegistry.gauge("running-steps", any<AtomicInteger>()) } returns runningStepsGauge
        every { meterRegistry.counter("executed-steps") } returns executedStepCounter
        every { meterRegistry.timer("step-execution", *anyVararg()) } returns stepExecutionTimer

        every {
            meterRegistry.gauge("minion-running-steps", any<List<Tag>>(), any<AtomicInteger>())
        } returnsArgument 2

        val dag = testDag {
            this.testStep("step-1", 1).all {
                step("step-2").processError("step-3")
                delayedStep("step-4", 2, 300).all {
                    step("step-5")
                    processError("step-6")
                }
            }
        }
        val runner = RunnerImpl(eventsLogger, meterRegistry, campaignStateKeeper)
        runner.setProperty("executionScope", this)
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", "my-dag", false, eventsLogger, meterRegistry)

        // when
        val executionDuration = measureTime {
            runner.run(minion, dag)
        }
        minion.join()

        // then
        // The execution of the runner operation should return before the complete DAG is complete.
        QalipsisTimeAssertions.assertShorterOrEqualTo(Duration.ofMillis(290), executionDuration)
        val steps = dag.steps()
        (1..6).forEach {
            steps["step-$it"].let { step ->
                step!!.assertExecuted()
                step.assertNotExhaustedContext()
            }
        }
        steps["step-1"]!!.let {
            Assertions.assertEquals(Unit, it.received)
            it.assertHasParent(null)
        }
        steps["step-2"]!!.let {
            Assertions.assertEquals(1, it.received)
            it.assertHasParent("step-1")
        }
        steps["step-3"]!!.let {
            Assertions.assertEquals(1, it.received)
            it.assertHasParent("step-2")
        }
        steps["step-4"]!!.let {
            Assertions.assertEquals(1, it.received)
            it.assertHasParent("step-1")
        }
        steps["step-5"]!!.let {
            Assertions.assertEquals(2, it.received)
            it.assertHasParent("step-4")
        }
        steps["step-6"]!!.let {
            Assertions.assertEquals(2, it.received)
            it.assertHasParent("step-4")
        }

        verifyOnce {
            idleMinionsGauge.incrementAndGet()
            idleMinionsGauge.decrementAndGet()
            runningMinionsGauge.incrementAndGet()
            runningMinionsGauge.decrementAndGet()
        }
        coVerifyExactly(6) {
            runningStepsGauge.incrementAndGet()
            runningStepsGauge.decrementAndGet()
            executedStepCounter.increment()
            eventsLogger.info(eq("step.execution.started"), timestamp = any(), tagsSupplier = any())
            eventsLogger.info(eq("step.execution.complete"), timestamp = any(), tagsSupplier = any())
            meterRegistry.timer("step-execution", "step", any(), "status", "completed")
            stepExecutionTimer.record(any<Duration>())
        }
        coVerifyOnce {
            campaignStateKeeper.recordSuccessfulStepExecution(eq("my-campaign"), eq("my-scenario"), eq("step-1"), eq(1))
            campaignStateKeeper.recordSuccessfulStepExecution(eq("my-campaign"), eq("my-scenario"), eq("step-2"), eq(1))
            campaignStateKeeper.recordSuccessfulStepExecution(eq("my-campaign"), eq("my-scenario"), eq("step-3"), eq(1))
            campaignStateKeeper.recordSuccessfulStepExecution(eq("my-campaign"), eq("my-scenario"), eq("step-4"), eq(1))
            campaignStateKeeper.recordSuccessfulStepExecution(eq("my-campaign"), eq("my-scenario"), eq("step-5"), eq(1))
            campaignStateKeeper.recordSuccessfulStepExecution(eq("my-campaign"), eq("my-scenario"), eq("step-6"), eq(1))
        }

        confirmVerified(runningStepsGauge, runningStepsGauge, executedStepCounter, campaignStateKeeper)
    }

    @Test
    @Timeout(3)
    internal fun `should execute the error processing only`() = runBlockingTest {
        // given
        val dag = testDag {
            this.testStep<Int>("step-1", generateException = true).step("step-2").processError("step-3").step("step-4").decoratedProcessError("step-5")
        }
        val runner = RunnerImpl(eventsLogger, meterRegistry, campaignStateKeeper)
        runner.setProperty("executionScope", this)
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", "my-dag", false, eventsLogger, meterRegistry)

        // when
        runner.run(minion, dag)
        minion.join()

        // then
        val steps = dag.steps()
        steps["step-1"]!!.assertExecuted()
        steps["step-2"]!!.assertNotExecuted()
        steps["step-3"]!!.assertExecuted()
        steps["step-4"]!!.assertNotExecuted()
        steps["step-5"]!!.assertExecuted()

        coVerifyOnce {
            campaignStateKeeper.recordFailedStepExecution(eq("my-campaign"), eq("my-scenario"), eq("step-1"), eq(1))
            campaignStateKeeper.recordSuccessfulStepExecution(eq("my-campaign"), eq("my-scenario"), eq("step-3"), eq(1))
            campaignStateKeeper.recordSuccessfulStepExecution(eq("my-campaign"), eq("my-scenario"), eq("step-5"), eq(1))
        }

        confirmVerified(campaignStateKeeper)
    }

    @Test
    @Timeout(3)
    internal fun `should execute the normal steps after recovery`() = runBlockingTest {
        // given
        val dag = testDag {
            this.testStep<Int>("step-1", generateException = true)
                .step("step-2").recoverError("step-3", 2).step("step-4")
        }
        val runner = RunnerImpl(eventsLogger, meterRegistry, campaignStateKeeper)
        runner.setProperty("executionScope", this)
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", "my-dag", false, eventsLogger, meterRegistry)

        // when
        runner.run(minion, dag)
        minion.join()

        // then
        val steps = dag.steps()
        steps["step-1"]!!.assertExecuted()
        steps["step-2"]!!.assertNotExecuted()
        steps["step-3"]!!.assertExecuted()
        steps["step-4"]!!.let {
            Assertions.assertEquals(2, it.received)
            it.assertExecuted()
        }

        coVerifyOnce {
            campaignStateKeeper.recordFailedStepExecution(eq("my-campaign"), eq("my-scenario"), eq("step-1"), eq(1))
            campaignStateKeeper.recordSuccessfulStepExecution(eq("my-campaign"), eq("my-scenario"), eq("step-3"), eq(1))
            campaignStateKeeper.recordSuccessfulStepExecution(eq("my-campaign"), eq("my-scenario"), eq("step-4"), eq(1))
        }

        confirmVerified(campaignStateKeeper)
    }

    @Test
    @Timeout(3)
    internal fun `should suspend next steps when there is no output`() = runBlockingTest {
        // given
        val dag = testDag {
            this.testStep<Int>("step-1").all {
                this.step("step-2").processError("step-3")
                this.step("step-4")
            }
        }
        val runner = RunnerImpl(eventsLogger, meterRegistry, campaignStateKeeper)
        runner.setProperty("executionScope", this)
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", "my-dag", false, eventsLogger, meterRegistry)

        // when
        runner.run(minion, dag)
        withTimeout(500) {
            minion.join()
        }

        // then
        val steps = dag.steps()
        steps["step-1"]!!.assertExecuted()
        (2..4).forEach {
            steps["step-$it"]!!.assertNotExecuted()
        }

        coVerifyOnce {
            campaignStateKeeper.recordSuccessfulStepExecution(eq("my-campaign"), eq("my-scenario"), eq("step-1"), eq(1))
        }

        confirmVerified(campaignStateKeeper)
    }

    @Test
    @Timeout(3)
    internal fun `should use retry policy instead of executing the step directly`() = runBlockingTest {
        // given
        val contextSlot = slot<StepContext<Unit, Int>>()
        val retryableFunctionSlot = slot<suspend (StepContext<Unit, Int>) -> Unit>()
        val step1Latch = SuspendedCountLatch(1)
        val retryPolicy: RetryPolicy = relaxedMockk {
            coEvery {
                execute(capture(contextSlot), capture(retryableFunctionSlot))
            } coAnswers {
                step1Latch.decrement()
                val context: StepContext<Unit, Int> = firstArg()
                context.send(123)
            }
        }
        val dag = testDag {
            this.testStep("step-1", output = 12, retryPolicy = retryPolicy)
                .step("step-2")
        }
        val runner = RunnerImpl(eventsLogger, meterRegistry, campaignStateKeeper)
        runner.setProperty("executionScope", this)
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", "my-dag", false, eventsLogger, meterRegistry)

        // when
        runner.run(minion, dag)
        step1Latch.await()

        // then
        coVerifyOnce { retryPolicy.execute<Int, Int>(any(), any()) }
        val steps = dag.steps()
        steps["step-1"]!!.assertNotExecuted()
        steps["step-2"]!!.let { step ->
            step.assertExecuted()
            Assertions.assertEquals(123, step.received)
        }

        coVerifyOnce {
            campaignStateKeeper.recordSuccessfulStepExecution(eq("my-campaign"), eq("my-scenario"), eq("step-1"), eq(1))
            campaignStateKeeper.recordSuccessfulStepExecution(eq("my-campaign"), eq("my-scenario"), eq("step-2"), eq(1))
        }

        confirmVerified(campaignStateKeeper)
    }
}
