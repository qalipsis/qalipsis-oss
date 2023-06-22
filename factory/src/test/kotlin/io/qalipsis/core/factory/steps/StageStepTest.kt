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

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.steps.Step
import io.qalipsis.core.exceptions.StepExecutionException
import io.qalipsis.core.factory.coreStepContext
import io.qalipsis.core.factory.orchestration.MinionImpl
import io.qalipsis.core.factory.orchestration.RunnerImpl
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.CleanMockkRecordedCalls
import io.qalipsis.test.mockk.relaxedMockk
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension


@CleanMockkRecordedCalls
internal class StageStepTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    internal fun `should first add a head, then a next`() {
        // given
        val stageStep = StageStep<Double, String>("", null, mockk())
        val tailStep: Step<Double, String> = relaxedMockk()
        val stepAfter1: Step<String, Double> = relaxedMockk()
        val stepAfter2: Step<String, Unit> = relaxedMockk()

        // when
        stageStep.addNext(tailStep)

        // then
        assertThat(stageStep).all {
            prop("head").isSameAs(tailStep)
            prop(StageStep<Double, String>::next).isEmpty()
        }

        // when
        stageStep.addNext(stepAfter1)
        stageStep.addNext(stepAfter2)

        // then
        assertThat(stageStep).all {
            prop("head").isSameAs(tailStep)
            prop(StageStep<Double, String>::next).all {
                hasSize(2)
                index(0).isSameAs(stepAfter1)
                index(1).isSameAs(stepAfter2)
            }
        }
    }

    @Test
    @Timeout(10)
    internal fun `should execute all steps and forward the output`() = testCoroutineDispatcher.runTest {
        val minion = MinionImpl("", "", "", false, false)
        val tailStep: Step<Int, String> = relaxedMockk {
            every { name } returns "<tail>"
            coEvery { execute(refEq(minion), any()) } coAnswers {
                val ctx = secondArg<StepContext<Int, String>>()
                repeat(ctx.receive()) {
                    // Write several times to ensure that all the values are passed to the output.
                    ctx.send((it + 1).toString())
                }
            }
            every { next } returns emptyList()
            every { retryPolicy } returns null
        }

        val headStep: Step<Double, Int> = relaxedMockk {
            every { name } returns "<head>"
            coEvery { execute(refEq(minion), any()) } coAnswers {
                val ctx = secondArg<StepContext<Double, Int>>()
                val input = ctx.receive().toInt()
                // Write twice to ensure that all the values are passed from a step to the next.
                ctx.send(input)
                ctx.send(input)
            }
            every { next } returns listOf<Step<Int, *>>(tailStep)
            every { retryPolicy } returns null
        }

        val step = StageStep<Double, String>("", null, mockk())
        step.addNext(headStep)

        val runner = RunnerImpl(this)
        step.runner = runner
        val ctx = coreStepContext<Double, String>(input = 3.0)
        val results = mutableListOf<String>()

        step.execute(minion, ctx)
        repeat(6) {
            results.add((ctx.output as Channel).receive().value)
        }

        assertTrue((ctx.output as Channel).isEmpty)
        assertFalse(ctx.output.isClosedForReceive)

        assertThat(results).all {
            hasSize(6)
            containsExactlyInAnyOrder("1", "2", "3", "1", "2", "3")
        }
    }

    @Test
    @Timeout(3)
    internal fun `should throw an exception when a wrapped step is in error`() = testCoroutineDispatcher.run {
        val minion = MinionImpl("", "", "", false, false)
        val secondStep: Step<Int, String> = relaxedMockk {
            coEvery { execute(refEq(minion), any()) } coAnswers {
                val ctx = secondArg<StepContext<Int, String>>()
                ctx.receive()
                throw RuntimeException("This is an error")
            }
            every { next } returns listOf(relaxedMockk {
                every { retryPolicy } returns null
                every { next } returns emptyList()
            })
            every { retryPolicy } returns null
        }

        val headStep = PipeStep<Int>("")
        headStep.addNext(secondStep)

        val step = StageStep<Int, String>("", null, mockk())
        step.addNext(headStep)

        val runner = RunnerImpl(this)
        step.runner = runner

        val ctx = coreStepContext<Int, String>(input = 3)
        val exception = assertThrows<StepExecutionException> {
            step.execute(minion, ctx)
        }

        assertTrue((ctx.output as Channel).isEmpty)
        assertFalse(ctx.output.isClosedForReceive)
        assertThat(exception).isInstanceOf(StepExecutionException::class).all {
            prop(StepExecutionException::cause).isNotNull().isInstanceOf(RuntimeException::class)
                .prop(RuntimeException::message).isEqualTo("This is an error")
        }
        assertThat(ctx).all {
            prop(StepContext<Int, String>::isExhausted).isFalse()
            prop(StepContext<Int, String>::errors).all {
                hasSize(1)
                index(0).prop(StepError::message).isEqualTo("This is an error")
            }
        }
    }

    @Test
    @Timeout(3)
    internal fun `should throw an exception when a wrapped step context is exhausted`() =
        testCoroutineDispatcher.runTest {
            val minion = MinionImpl("", "", "", false, false)
            val secondStep: Step<Int, String> = relaxedMockk {
                coEvery { execute(refEq(minion), any()) } coAnswers {
                    val ctx = secondArg<StepContext<Int, String>>()
                    ctx.isExhausted = true
                    ctx.addError(StepError("This is an error", ""))
                }
                every { next } returns listOf(relaxedMockk {
                    every { retryPolicy } returns null
                    every { next } returns emptyList()
                })
                every { retryPolicy } returns null
            }

            val headStep = PipeStep<Int>("")
            headStep.addNext(secondStep)

            val step = StageStep<Int, String>("", null, mockk())
            step.addNext(headStep)

            val runner = RunnerImpl(this)
            step.runner = runner

            val ctx = coreStepContext<Int, String>(input = 3)
            val exception = assertThrows<StepExecutionException> {
                step.execute(minion, ctx)
            }

            assertTrue((ctx.output as Channel).isEmpty)
            assertFalse(ctx.output.isClosedForReceive)
            assertThat(exception).isInstanceOf(StepExecutionException::class).all {
                prop(StepExecutionException::cause).isNotNull().isInstanceOf(RuntimeException::class)
                    .prop(RuntimeException::message).isEqualTo("This is an error")
            }
            assertThat(ctx).all {
                prop(StepContext<Int, String>::isExhausted).isFalse()
                prop(StepContext<Int, String>::errors).all {
                    hasSize(1)
                    index(0).prop(StepError::message).isEqualTo("This is an error")
                }
            }
        }

    @Test
    internal fun `should init all the contained steps`() = testCoroutineDispatcher.runTest {
        // given
        val step1 = relaxedMockk<Step<*, *>>()
        val step2 = relaxedMockk<Step<*, *>> {
            every { next } returns listOf(step1)
        }
        val step3 = relaxedMockk<Step<*, *>>()
        val head = relaxedMockk<Step<*, *>> {
            every { next } returns listOf(step2, step3)
        }
        val stageStep = StageStep<Int, String>("", null, mockk())
        stageStep.addNext(head)

        // when
        stageStep.init()

        // then
        coVerify {
            head.init()
            step1.init()
            step2.init()
            step3.init()
        }
    }

    @Test
    internal fun `should start all the contained steps`() = testCoroutineDispatcher.runTest {
        // given
        val step1 = relaxedMockk<Step<*, *>>()
        val step2 = relaxedMockk<Step<*, *>> {
            every { next } returns listOf(step1)
        }
        val step3 = relaxedMockk<Step<*, *>>()
        val head = relaxedMockk<Step<*, *>> {
            every { next } returns listOf(step2, step3)
        }
        val stageStep = StageStep<Int, String>("", null, mockk())
        stageStep.addNext(head)
        val startStopContext = relaxedMockk<StepStartStopContext>()

        // when
        stageStep.start(startStopContext)

        // then
        coVerify {
            head.start(refEq(startStopContext))
            step1.start(refEq(startStopContext))
            step2.start(refEq(startStopContext))
            step3.start(refEq(startStopContext))
        }
    }

    @Test
    internal fun `should stop all the contained steps`() = testCoroutineDispatcher.runTest {
        // given
        val step1 = relaxedMockk<Step<*, *>>()
        val step2 = relaxedMockk<Step<*, *>> {
            every { next } returns listOf(step1)
        }
        val step3 = relaxedMockk<Step<*, *>>()
        val head = relaxedMockk<Step<*, *>> {
            every { next } returns listOf(step2, step3)
        }
        val stageStep = StageStep<Int, String>("", null, mockk())
        stageStep.addNext(head)
        val startStopContext = relaxedMockk<StepStartStopContext>()

        // when
        stageStep.stop(startStopContext)

        // then
        coVerify {
            head.stop(refEq(startStopContext))
            step1.stop(refEq(startStopContext))
            step2.stop(refEq(startStopContext))
            step3.stop(refEq(startStopContext))
        }
    }

    @Test
    internal fun `should destroy all the contained steps`() = testCoroutineDispatcher.runTest {
        // given
        val step1 = relaxedMockk<Step<*, *>>()
        val step2 = relaxedMockk<Step<*, *>> {
            every { next } returns listOf(step1)
        }
        val step3 = relaxedMockk<Step<*, *>>()
        val head = relaxedMockk<Step<*, *>> {
            every { next } returns listOf(step2, step3)
        }
        val stageStep = StageStep<Int, String>("", null, mockk())
        stageStep.addNext(head)

        // when
        stageStep.destroy()

        // then
        coVerify {
            head.destroy()
            step1.destroy()
            step2.destroy()
            step3.destroy()
        }
    }
}
