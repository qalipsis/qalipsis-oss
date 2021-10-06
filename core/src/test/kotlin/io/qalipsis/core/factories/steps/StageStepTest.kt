package io.qalipsis.core.factories.steps

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.steps.Step
import io.qalipsis.core.exceptions.StepExecutionException
import io.qalipsis.core.factories.coreStepContext
import io.qalipsis.core.factories.orchestration.MinionImpl
import io.qalipsis.core.factories.orchestration.RunnerImpl
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.CleanMockkRecordedCalls
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.utils.setProperty
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows


@CleanMockkRecordedCalls
internal class StageStepTest {

    private val meterRegistry: MeterRegistry = relaxedMockk {
        every { gauge(eq("minion-running-steps"), any<Iterable<Tag>>(), any<Number>()) } returnsArgument 2
    }

    @Test
    internal fun `should first add a head, then a next`() {
        // given
        val stageStep = StageStep<Double, String>("", null)
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
    internal fun `should execute all steps and forward the output`() = runBlocking {
        val minion = MinionImpl("", "", "", "", false, relaxedMockk(), meterRegistry)
        val tailStep: Step<Int, String> = relaxedMockk {
            every { id } returns "<tail>"
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
            every { id } returns "<head>"
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

        val step = StageStep<Double, String>("", null)
        step.addNext(headStep)

        val runner = RunnerImpl(relaxedMockk(), relaxedMockk {
            every { gauge(any(), any()) } returnsArgument 1
        }, relaxedMockk())
        runner.setProperty("executionScope", this)
        step.runner = runner
        val ctx = coreStepContext<Double, String>(input = 3.0)
        val results = mutableListOf<String>()

        step.execute(minion, ctx)
        repeat(6) {
            results.add((ctx.output as Channel).receive())
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
    internal fun `should throw an exception when a wrapped step is in error`() = runBlocking {
        val minion = MinionImpl("", "", "", "", false, relaxedMockk(), meterRegistry)
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

        val headStep = TubeStep<Int>("")
        headStep.addNext(secondStep)

        val step = StageStep<Int, String>("", null)
        step.addNext(headStep)

        val runner = RunnerImpl(relaxedMockk(), relaxedMockk {
            every { gauge(any(), any()) } returnsArgument 1
        }, relaxedMockk())
        runner.setProperty("executionScope", this)
        step.runner = runner

        val ctx = coreStepContext<Int, String>(input = 3)
        val exception = assertThrows<StepExecutionException> {
            step.execute(minion, ctx)
        }

        assertTrue((ctx.output as Channel).isEmpty)
        assertFalse(ctx.output.isClosedForReceive)
        assertThat(exception).isInstanceOf(StepExecutionException::class).all {
            prop(StepExecutionException::cause).isNull()
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
    internal fun `should throw an exception when a wrapped step context is exhausted`() = runBlocking {
        val minion = MinionImpl("", "", "", "", false, relaxedMockk(), meterRegistry)
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

        val headStep = TubeStep<Int>("")
        headStep.addNext(secondStep)

        val step = StageStep<Int, String>("", null)
        step.addNext(headStep)

        val runner = RunnerImpl(relaxedMockk(), relaxedMockk {
            every { gauge(any(), any()) } returnsArgument 1
        }, relaxedMockk())
        runner.setProperty("executionScope", this)
        step.runner = runner

        val ctx = coreStepContext<Int, String>(input = 3)
        val exception = assertThrows<StepExecutionException> {
            step.execute(minion, ctx)
        }

        assertTrue((ctx.output as Channel).isEmpty)
        assertFalse(ctx.output.isClosedForReceive)
        assertThat(exception).isInstanceOf(StepExecutionException::class).all {
            prop(StepExecutionException::cause).isNull()
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
    internal fun `should init all the contained steps`() = runBlocking {
        // given
        val step1 = relaxedMockk<Step<*, *>>()
        val step2 = relaxedMockk<Step<*, *>> {
            every { next } returns listOf(step1)
        }
        val step3 = relaxedMockk<Step<*, *>>()
        val head = relaxedMockk<Step<*, *>> {
            every { next } returns listOf(step2, step3)
        }
        val stageStep = StageStep<Int, String>("", null)
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
    internal fun `should start all the contained steps`() = runBlocking {
        // given
        val step1 = relaxedMockk<Step<*, *>>()
        val step2 = relaxedMockk<Step<*, *>> {
            every { next } returns listOf(step1)
        }
        val step3 = relaxedMockk<Step<*, *>>()
        val head = relaxedMockk<Step<*, *>> {
            every { next } returns listOf(step2, step3)
        }
        val stageStep = StageStep<Int, String>("", null)
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
    internal fun `should stop all the contained steps`() = runBlocking {
        // given
        val step1 = relaxedMockk<Step<*, *>>()
        val step2 = relaxedMockk<Step<*, *>> {
            every { next } returns listOf(step1)
        }
        val step3 = relaxedMockk<Step<*, *>>()
        val head = relaxedMockk<Step<*, *>> {
            every { next } returns listOf(step2, step3)
        }
        val stageStep = StageStep<Int, String>("", null)
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
    internal fun `should destroy all the contained steps`() = runBlocking {
        // given
        val step1 = relaxedMockk<Step<*, *>>()
        val step2 = relaxedMockk<Step<*, *>> {
            every { next } returns listOf(step1)
        }
        val step3 = relaxedMockk<Step<*, *>>()
        val head = relaxedMockk<Step<*, *>> {
            every { next } returns listOf(step2, step3)
        }
        val stageStep = StageStep<Int, String>("", null)
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
