package io.qalipsis.core.factories.steps

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.orchestration.factories.Minion
import io.qalipsis.api.steps.Step
import io.qalipsis.core.factories.orchestration.RunnerImpl
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout


@WithMockk
internal class StageStepTest {

    @RelaxedMockK
    lateinit var minion: Minion

    @Test
    internal fun `should first add a head, then a next`() {
        // given
        val groupStep = StageStep<Double, String>("", null)
        val tailStep: Step<Double, String> = relaxedMockk()
        val stepAfter1: Step<String, Double> = relaxedMockk()
        val stepAfter2: Step<String, Unit> = relaxedMockk()

        // when
        groupStep.addNext(tailStep)

        // then
        assertThat(groupStep).all {
            prop("head").isSameAs(tailStep)
            prop(StageStep<Double, String>::next).isEmpty()
        }

        // when
        groupStep.addNext(stepAfter1)
        groupStep.addNext(stepAfter2)

        // then
        assertThat(groupStep).all {
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
    internal fun `should execute all steps and forward the output`() = runBlockingTest {
        val tailStep: Step<Int, String> = relaxedMockk {
            coEvery { execute(refEq(minion), any()) } coAnswers {
                val ctx = secondArg<StepContext<Int, String>>()
                repeat(ctx.input.receive()) {
                    // Write several times to ensure that all the values are passed to the output.
                    ctx.output.send((it + 1).toString())
                }
            }
            every { next } returns emptyList()
            every { retryPolicy } returns null
        }

        val headStep: Step<Double, Int> = relaxedMockk {
            coEvery { execute(refEq(minion), any()) } coAnswers {
                val ctx = secondArg<StepContext<Double, Int>>()
                val input = ctx.input.receive().toInt()
                // Write twice to ensure that all the values are passed from a step to the next.
                ctx.output.send(input)
                ctx.output.send(input)
            }
            every { next } returns listOf<Step<Int, *>>(tailStep)
            every { retryPolicy } returns null
        }

        val step = StageStep<Double, String>("", null)
        step.addNext(headStep)

        val runner = RunnerImpl(relaxedMockk(), relaxedMockk {
            every { gauge(any(), any()) } returnsArgument 1
        })
        runner.coroutineScope = this
        step.runner = runner

        val ctx = StepTestHelper.createStepContext<Double, String>(input = 3.0)

        val results = mutableListOf<String>()

        step.execute(minion, ctx)
        repeat(6) {
            results.add((ctx.output as Channel).receive())
        }

        assertTrue((ctx.output as Channel).isEmpty)
        assertFalse((ctx.output as Channel).isClosedForReceive)

        assertThat(results).all {
            hasSize(6)
            containsExactlyInAnyOrder("1", "2", "3", "1", "2", "3")
        }
    }

    @Test
    @Timeout(3)
    internal fun `should mark the context as exhausted when a wrapped step is in error`() = runBlockingTest {
        val exception = RuntimeException("This is an error")
        val secondStep: Step<Int, String> = relaxedMockk {
            coEvery { execute(refEq(minion), any()) } coAnswers {
                val ctx = secondArg<StepContext<Int, String>>()
                ctx.input.receive()
                throw exception
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
        })
        runner.coroutineScope = this
        step.runner = runner

        val ctx = StepTestHelper.createStepContext<Int, String>(input = 3)
        step.execute(minion, ctx)

        assertTrue((ctx.output as Channel).isEmpty)
        assertFalse((ctx.output as Channel).isClosedForReceive)
        assertThat(ctx).all {
            prop(StepContext<Int, String>::isExhausted).isTrue()
            prop(StepContext<Int, String>::errors).all {
                hasSize(1)
                index(0).prop(StepError::cause).isSameAs(exception)
            }
        }
    }
}
