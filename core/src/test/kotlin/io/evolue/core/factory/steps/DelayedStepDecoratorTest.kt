package io.evolue.core.factory.steps

import io.evolue.api.steps.Step
import io.evolue.test.mockk.coVerifyOnce
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * @author Eric Jessé
 */
internal class DelayedStepDecoratorTest {

    @Test
    @Timeout(5)
    fun shouldExecuteTheDecoratedStepAfterTheDelay() {
        val decoratedStepExecutionTimestamp = AtomicLong(0)
        val delay = 20L
        val decoratedStep: Step<Any, Any> = mockk {
            coEvery { execute(any()) } answers { decoratedStepExecutionTimestamp.set(System.currentTimeMillis()) }
            every { retryPolicy } returns null
        }
        val step = spyk(DelayedStepDecorator(Duration.ofMillis(delay), decoratedStep))
        val ctx = StepTestHelper.createStepContext<Any, Any>()

        val start = System.currentTimeMillis()
        runBlocking {
            step.execute(ctx)
        }

        coVerifyOnce { step.executeStep(refEq(decoratedStep), refEq(ctx)) }
        Assertions.assertTrue(decoratedStepExecutionTimestamp.get() - start >= delay)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}