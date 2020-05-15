package io.evolue.core.factory.orchestration

import io.evolue.api.context.StepContext
import io.evolue.api.events.EventLogger
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.sync.SuspendedCountLatch
import io.evolue.core.factory.dag
import io.evolue.core.factory.delayedStep
import io.evolue.core.factory.step
import io.evolue.core.factory.steps
import io.evolue.test.coroutines.AbstractCoroutinesTest
import io.evolue.test.mockk.coVerifyExactly
import io.evolue.test.mockk.coVerifyOnce
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.mockk.verifyOnce
import io.evolue.test.time.EvolueTimeAssertions
import io.evolue.test.time.coMeasureTime
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
@ExtendWith(MockKExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
internal class RunnerTest : AbstractCoroutinesTest() {

    @RelaxedMockK("eventReporter")
    lateinit private var eventLogger: EventLogger

    @RelaxedMockK("meterRegistry")
    lateinit private var meterRegistry: MeterRegistry

    @BeforeEach
    internal fun setUp() {
        clearAllMocks()
        val defaultGauge: AtomicInteger = relaxedMockk()
        val defaultCounter: Counter = relaxedMockk()
        val defaultTimer: Timer = relaxedMockk()

        every { meterRegistry.gauge(any(), any<AtomicInteger>()) } returns defaultGauge
        every { meterRegistry.gauge(any(), any<List<Tag>>(), any<AtomicInteger>()) } returns defaultGauge
        every { meterRegistry.counter(any()) } returns defaultCounter
        every { meterRegistry.timer(any(), *anyVararg()) } returns defaultTimer
    }

    @Test
    @Timeout(1)
    internal fun `should execute the full dag asynchronously and update meters`() {
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
            meterRegistry.gauge("minion-executing-steps", any<List<Tag>>(), any<AtomicInteger>())
        } returnsArgument 2

        val dag = dag {
            this.step("step-1", 1).step("step-2").processError("step-3")
            this.delayedStep("step-4", 2, 200).all {
                step("step-5")
                processError("step-6")
            }
        }
        val runner = Runner(eventLogger, meterRegistry)
        val minion = MinionImpl("my-minion", false, eventLogger, meterRegistry)

        // when
        val executionDuration = coMeasureTime {
            runner.run(minion, dag)
        }
        runBlocking {
            minion.join()
        }

        // then
        // The execution of the runner operation should return before the complete DAG is complete.
        EvolueTimeAssertions.assertShorterOrEqualTo(Duration.ofMillis(190), executionDuration)
        val steps = dag.steps()
        (1..6).forEach {
            steps["step-$it"].let { step ->
                step!!.assertExecuted()
                step.assertNotExhaustedContext()
            }
        }
        steps["step-1"]!!.let {
            Assertions.assertNull(it.received)
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
            Assertions.assertNull(it.received)
            it.assertHasParent(null)
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
            eventLogger.debug(match { it.startsWith("step-") && it.endsWith("-started") }, tagsSupplier = any())
            eventLogger.debug(match { it.startsWith("step-") && it.endsWith("-completed") }, tagsSupplier = any())
            meterRegistry.timer("step-execution", "step", any(), "status", "completed")
            stepExecutionTimer.record(any<Duration>())
        }

        confirmVerified(runningStepsGauge, runningStepsGauge, executedStepCounter)
    }

    @Test
    @Timeout(1)
    internal fun `should execute the error processing only`() {
        // given
        val dag = dag {
            this.step<Int>("step-1", generateException = true).step("step-2").processError("step-3").step("step-4")
        }
        val runner = Runner(eventLogger, meterRegistry)
        val minion = MinionImpl("my-minion", false, eventLogger, meterRegistry)

        // when
        runBlocking {
            runner.run(minion, dag)
            minion.join()
        }

        // then
        val steps = dag.steps()
        steps["step-2"]!!.assertNotExecuted()
        steps["step-3"]!!.assertExecuted()
        steps["step-4"]!!.assertNotExecuted()
    }

    @Test
    @Timeout(1)
    internal fun `should execute the normal steps after recovery`() {
        // given
        val dag = dag {
            this.step<Int>("step-1", generateException = true)
                .step("step-2").recoverError("step-3", 2).step("step-4")
        }
        val runner = Runner(eventLogger, meterRegistry)
        val minion = MinionImpl("my-minion", false, eventLogger, meterRegistry)

        // when
        runBlocking {
            runner.run(minion, dag)
            minion.join()
        }

        // then
        val steps = dag.steps()
        steps["step-2"]!!.assertNotExecuted()
        steps["step-3"]!!.assertExecuted()
        steps["step-4"]!!.let {
            Assertions.assertEquals(2, it.received)
            it.assertExecuted()
        }
    }

    @Test
    @Timeout(1)
    internal fun `should suspend next steps when there is no output`() {
        // given
        val dag = dag {
            this.step<Int>("step-1").all {
                this.step("step-2").processError("step-3")
                this.step("step-4")
            }
        }
        val runner = Runner(eventLogger, meterRegistry)
        val minion = MinionImpl("my-minion", false, eventLogger, meterRegistry)

        // when
        runBlocking {
            runner.run(minion, dag)
            withTimeout(500) {
                minion.join()
            }
        }

        // then
        val steps = dag.steps()
        (2..4).forEach {
            steps["step-$it"]!!.assertNotExecuted()
        }
    }

    @Test
    @Timeout(1)
    internal fun `should use retry policy instead of executing the step directly`() {
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
                context.output.send(123)
            }
        }
        val dag = dag {
            this.step<Int>("step-1", output = 12, retryPolicy = retryPolicy)
                .step("step-2")
        }
        val runner = Runner(eventLogger, meterRegistry)
        val minion = MinionImpl("my-minion", false, eventLogger, meterRegistry)

        // when
        runBlocking {
            runner.run(minion, dag)
            step1Latch.await()
        }

        // then
        coVerifyOnce { retryPolicy.execute<Int, Int>(any(), any()) }
        val steps = dag.steps()
        steps["step-1"]!!.assertNotExecuted()
        steps["step-2"]!!.let { step ->
            step.assertExecuted()
            Assertions.assertEquals(123, step.received)
        }


    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
