package io.evolue.core.factories.steps.singleton

import io.evolue.api.context.StepContext
import io.evolue.api.steps.Step
import io.evolue.test.mockk.WithMockk
import io.evolue.test.mockk.coVerifyOnce
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.mockk.verifyOnce
import io.evolue.test.steps.StepTestHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 *
 * @author Eric Jessé
 */
@Suppress("EXPERIMENTAL_API_USAGE")
@WithMockk
internal class NoMoreNextStepDecoratorTest {

    @RelaxedMockK
    lateinit var decoratedStep: Step<Int, Int>

    @Test
    @Timeout(1)
    internal fun `should execute decorated`() {
        coEvery { decoratedStep.execute(any()) } coAnswers {
            (firstArg() as StepContext<Int, Int>).also {
                it.output.send(it.input.receive())
            }
        }
        val step = NoMoreNextStepDecorator(decoratedStep)
        val ctx = StepTestHelper.createStepContext<Int, Int>(input = 1)

        runBlocking {
            step.execute(ctx)
            val output = (ctx.output as Channel).receive()
            assertEquals(1, output)
        }
        coVerifyOnce {
            decoratedStep.execute(refEq(ctx))
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }

    @Test
    @Timeout(1)
    internal fun `should provide decorated's next steps`() {
        val nextSteps = listOf<Step<Int, *>>()
        every { decoratedStep.next } returns nextSteps

        val step = NoMoreNextStepDecorator(decoratedStep)

        val result = step.decorated.next

        assertSame(nextSteps, result)
        verifyOnce {
            decoratedStep.next
        }
    }

    @Test
    @Timeout(1)
    internal fun `should init decorated`() {
        val step = NoMoreNextStepDecorator(decoratedStep)

        runBlocking {
            step.init()
        }
        coVerifyOnce {
            decoratedStep.init()
        }
    }

    @Test
    @Timeout(1)
    internal fun `should destroy decorated`() {
        val step = NoMoreNextStepDecorator(decoratedStep)

        runBlocking {
            step.destroy()
        }
        coVerifyOnce {
            decoratedStep.destroy()
        }
    }

    @Test
    @Timeout(1)
    internal fun `should start decorated`() {
        val step = NoMoreNextStepDecorator(decoratedStep)

        runBlocking {
            step.start()
        }
        coVerifyOnce {
            decoratedStep.start()
        }
    }

    @Test
    @Timeout(1)
    internal fun `should stop decorated`() {
        val step = NoMoreNextStepDecorator(decoratedStep)

        runBlocking {
            step.stop()
        }
        coVerifyOnce {
            decoratedStep.stop()
        }
    }

    @Test
    @Timeout(1)
    internal fun `should not add next decorated`() {
        val step = NoMoreNextStepDecorator(decoratedStep)

        runBlocking {
            step.addNext(relaxedMockk { })
        }
    }
}