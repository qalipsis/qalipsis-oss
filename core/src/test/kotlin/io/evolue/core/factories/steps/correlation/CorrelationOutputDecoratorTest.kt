package io.evolue.core.factories.steps.correlation

import io.evolue.api.context.CorrelationRecord
import io.evolue.api.context.StepContext
import io.evolue.api.messaging.Topic
import io.evolue.api.steps.Step
import io.evolue.test.mockk.WithMockk
import io.evolue.test.mockk.coVerifyOnce
import io.evolue.test.steps.StepTestHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@WithMockk
internal class CorrelationOutputDecoratorTest {

    @RelaxedMockK
    lateinit var decoratedStep: Step<Any?, String>

    @RelaxedMockK
    lateinit var topic: Topic<CorrelationRecord<String>>

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
        every { decoratedStep.id } answers { "my-step" }
        every { decoratedStep.retryPolicy } returns null
        every { decoratedStep.next } returns mutableListOf()
        val ctx = StepTestHelper.createStepContext<Any?, String>()
        val step = spyk(CorrelationOutputDecorator(decoratedStep, topic))

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

        coVerifyOrder {
            topic.produceValue(CorrelationRecord("my-minion", "my-step", "my-value-1"))
            topic.produceValue(CorrelationRecord("my-minion", "my-step", "my-value-2"))
            topic.produceValue(CorrelationRecord("my-minion", "my-step", "my-value-3"))
        }
    }

}
