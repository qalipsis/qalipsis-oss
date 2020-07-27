package io.evolue.core.factory.steps.singleton

import io.evolue.api.context.StepContext
import io.evolue.api.steps.Step
import io.evolue.test.mockk.WithMockk
import io.evolue.test.mockk.coVerifyOnce
import io.evolue.test.steps.StepTestHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@WithMockk
internal class SingletonOutputDecoratorTest {

    @RelaxedMockK
    lateinit var decoratedStep: Step<Any, String>

    @Test
    @Timeout(3)
    fun shouldForwardDataOfDecoratedStep() {
        // given
        coEvery { decoratedStep.execute(any()) } coAnswers {
            ((firstArg() as StepContext<Any, String>).output).apply {
                send("my-value-1")
                send("my-value-2")
                send("my-value-3")
            }
        }
        every { decoratedStep.id } answers { " " }
        every { decoratedStep.retryPolicy } returns null
        val ctx = StepTestHelper.createStepContext<Any, String>()
        val step = spyk(SingletonOutputDecorator(decoratedStep))

        val subscription = step.subscribe()

        // when
        runBlocking {
            step.execute(ctx)
        }

        // then
        coVerifyOnce { step.executeStep(refEq(decoratedStep), refEq(ctx)) }
        Assertions.assertFalse(ctx.exhausted)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        coVerify {
            decoratedStep.execute(ctx)
        }
        runBlocking {
            Assertions.assertEquals("my-value-1", subscription.receive())
            Assertions.assertEquals("my-value-2", subscription.receive())
            Assertions.assertEquals("my-value-3", subscription.receive())
        }
    }

    @Test
    internal fun shouldCloseAllChannels() {
        // given
        every { decoratedStep.id } answers { " " }
        val step = SingletonOutputDecorator(decoratedStep)

        // when
        runBlocking {
            step.destroy()
        }

        // then
        coVerifyOnce { decoratedStep.destroy() }
        Assertions.assertTrue(step.broadcastChannel.isClosedForSend)
    }
}
