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

package io.qalipsis.core.factory.orchestration

import io.mockk.coEvery
import io.mockk.slot
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.factory.generate
import io.qalipsis.core.factory.noOutput
import io.qalipsis.core.factory.steps
import io.qalipsis.core.factory.testDag
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.time.QalipsisTimeAssertions
import io.qalipsis.test.time.measureTime
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

/**
 * @author Eric Jessé
 */
@WithMockk
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
internal class RunnerImplTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    @Timeout(10)
    internal fun `should execute the full dag asynchronously`() = testCoroutineDispatcher.runTest {
        // given
        val dag = testDag {
            generate("step-1", 1).all {
                forward("step-2").processError("step-3")
                delayed("step-4", 2, 600).all {
                    forward("step-5")
                    processError("step-6")
                }
            }
        }
        val runner = RunnerImpl(this)
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", false, false)

        // when
        val executionDuration = measureTime {
            runner.run(minion, dag)
        }
        minion.join()

        // then
        // The execution of the runner operation should return before the complete DAG is complete.
        QalipsisTimeAssertions.assertShorterOrEqualTo(Duration.ofMillis(550), executionDuration)
        val steps = dag.steps()
        (1..6).forEach {
            steps["step-$it"].let { step ->
                step!!.assertExecutionCount(1)
                step.assertCompletionCount(1)
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
    }

    @Test
    @Timeout(3)
    internal fun `should execute the error processing only`() = testCoroutineDispatcher.runTest {
        // given
        val dag = testDag {
            noOutput<Int>("step-1", generateException = true).forward("step-2").processError("step-3").forward("step-4")
                .decoratedProcessError("step-5")
        }
        val runner = RunnerImpl(this)
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", false, false)

        // when
        runner.run(minion, dag)
        minion.join()

        // then
        val steps = dag.steps()
        steps["step-1"]!!.all {
            assertExecutionCount(1)
            assertCompletionCount(1)
        }
        steps["step-2"]!!.all {
            assertNotExecuted()
            assertCompletionCount(1)
        }
        steps["step-3"]!!.all {
            assertExecutionCount(1)
            assertCompletionCount(1)
        }
        steps["step-4"]!!.all {
            assertNotExecuted()
            assertCompletionCount(1)
        }
        steps["step-5"]!!.all {
            assertExecutionCount(1)
            assertCompletionCount(1)
        }
    }

    @Test
    @Timeout(3)
    internal fun `should execute the normal steps after recovery`() = testCoroutineDispatcher.runTest {
        // given
        val dag = testDag {
            noOutput<Int>("step-1", generateException = true)
                .forward("step-2").recoverError("step-3", 2).forward("step-4")
        }
        val runner = RunnerImpl(this)
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", false, false)

        // when
        runner.run(minion, dag)
        minion.join()

        // then
        val steps = dag.steps()
        steps["step-1"]!!.apply {
            assertExecutionCount(1)
            assertCompletionCount(1)
        }
        steps["step-2"]!!.apply {
            assertNotExecuted()
            assertCompletionCount(1)
        }
        steps["step-3"]!!.apply {
            assertExecutionCount(1)
            assertCompletionCount(1)
        }
        steps["step-4"]!!.apply {
            assertExecutionCount(1)
            assertCompletionCount(1)
            Assertions.assertEquals(2, received)
        }
    }

    @Test
    @Timeout(3)
    internal fun `should not execute next steps when there is no output but complete them`() =
        testCoroutineDispatcher.runTest {
            // given
            val dag = testDag {
                noOutput<Int>("step-1").blackhole("step-2").all {
                    forward("step-3").processError("step-4")
                    forward("step-5")
                }
            }
            val runner = RunnerImpl(this)
            val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", false, false)

            // when
            runner.run(minion, dag)
            withTimeout(500) {
                minion.join()
            }

            // then
            val steps = dag.steps()
            steps["step-1"]!!.apply {
                assertExecutionCount(1)
                assertCompletionCount(1)
            }
            (2..5).forEach {
                steps["step-$it"]!!.apply {
                    assertNotExecuted()
                    assertCompletionCount(1)
                }
            }
        }

    @Test
    @Timeout(3)
    internal fun `should use retry policy instead of executing the step directly`() = testCoroutineDispatcher.runTest {
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
            generate("step-1", output = 12, retryPolicy = retryPolicy)
                .forward("step-2")
        }
        val runner = RunnerImpl(this)
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", false, false)

        // when
        runner.run(minion, dag)
        step1Latch.await()

        // then
        coVerifyOnce { retryPolicy.execute<Int, Int>(any(), any()) }
        val steps = dag.steps()
        steps["step-1"]!!.apply {
            assertNotExecuted()
            assertCompletionCount(1)
        }
        steps["step-2"]!!.apply {
            assertExecutionCount(1)
            assertCompletionCount(1)
            Assertions.assertEquals(123, received)
        }
    }

    @Test
    internal fun `should complete only once per minion even when there is no output`() =
        testCoroutineDispatcher.runTest {
            // given
            val dag = testDag {
                generate("step-1", 1).repeated("step-2", 4).forward("step-3").all {
                    forward("step-4").processError("step-5")
                    blackhole("step-6").all {
                        forward("step-7")
                        processError("step-8")
                    }
                }
            }
            val runner = RunnerImpl(this)
            val minion1 = MinionImpl("my-minion-1", "my-campaign", "my-scenario", false, false)
            val minion2 = MinionImpl("my-minion-2", "my-campaign", "my-scenario", false, false)

            // when
            runner.run(minion1, dag)
            runner.run(minion2, dag)
            withTimeout(500) {
                minion1.join()
                minion2.join()
            }

            // then
            val steps = dag.steps()
            (1..2).forEach {
                steps["step-$it"]!!.apply {
                    assertExecutionCount(2)
                    assertCompletionCount(2)
                }
            }
            (3..6).forEach {
                steps["step-$it"]!!.apply {
                    assertExecutionCount(8)
                    assertCompletionCount(2)
                }
            }
            (7..8).forEach {
                steps["step-$it"]!!.apply {
                    assertNotExecuted()
                    assertCompletionCount(2)
                }
            }
        }

    @Test
    fun `should forward a tail context when no tail output was generated`() =
        testCoroutineDispatcher.runTest {
            // given
            val dag = testDag {
                noOutput<Unit>("step-1").repeated("step-2", 4).forward("step-3").all {
                    forward("step-4").processError("step-5")
                    blackhole("step-6").all {
                        forward("step-7")
                        processError("step-8")
                    }
                }
            }
            val runner = RunnerImpl(this)
            val minion1 = MinionImpl("my-minion-1", "my-campaign", "my-scenario", false, false)
            val minion2 = MinionImpl("my-minion-2", "my-campaign", "my-scenario", false, false)

            // when
            runner.run(minion1, dag)
            runner.run(minion2, dag)
            withTimeout(500) {
                minion1.join()
                minion2.join()
            }

            // then
            val steps = dag.steps()
            steps["step-1"]!!.apply {
                assertExecutionCount(2)
                assertCompletionCount(2)
            }
            (2..8).forEach {
                steps["step-$it"]!!.apply {
                    assertExecutionCount(0)
                    assertCompletionCount(2)
                }
            }
        }

}
