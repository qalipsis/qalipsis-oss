package io.evolue.core.factories.steps.decorators

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.evolue.api.context.StepContext
import io.evolue.api.messaging.unicastTopic
import io.evolue.api.steps.Step
import io.evolue.test.assertk.prop
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
import org.junit.jupiter.api.assertThrows

@WithMockk
internal class OutputTopicStepDecoratorTest {

    @RelaxedMockK
    lateinit var decoratedStep: Step<Any, String>

    @Test
    @Timeout(3)
    fun shouldForwardDataOfDecoratedStep() {
        // given
        val topic = unicastTopic<String>()
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
        val step = spyk(
                OutputTopicStepDecorator(decoratedStep,
                        topic))
        val topicSubscription = runBlocking {
            topic.subscribe("any")
        }

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
            Assertions.assertEquals("my-value-1", topicSubscription.pollValue())
            Assertions.assertEquals("my-value-2", topicSubscription.pollValue())
            Assertions.assertEquals("my-value-3", topicSubscription.pollValue())
        }
    }

    @Test
    internal fun shouldNotExecuteTwice() {
        val topic = unicastTopic<String>()
        val step = spyk(
                OutputTopicStepDecorator(decoratedStep,
                        topic))
        val ctx = StepTestHelper.createStepContext<Any, String>()

        // when
        runBlocking {
            step.execute(ctx)
        }

        assertThrows<IllegalStateException> {
            runBlocking {
                step.execute(ctx)
            }
        }
    }

    @Test
    internal fun shouldCloseAllChannels() {
        // given
        val topic = unicastTopic<String>()
        every { decoratedStep.id } answers { " " }
        val step =
            OutputTopicStepDecorator(decoratedStep, topic)

        // when
        runBlocking {
            step.destroy()
        }

        // then
        coVerifyOnce { decoratedStep.destroy() }
        assertThat(topic).prop("open").isEqualTo(false)
    }
}
