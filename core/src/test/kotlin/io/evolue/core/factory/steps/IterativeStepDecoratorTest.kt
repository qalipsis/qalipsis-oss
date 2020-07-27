package io.evolue.core.factory.steps

import io.evolue.api.context.StepContext
import io.evolue.api.steps.Step
import io.evolue.test.mockk.coVerifyExactly
import io.evolue.test.mockk.coVerifyOnce
import io.evolue.test.steps.StepTestHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
internal class IterativeStepDecoratorTest {

    @Test
    @Timeout(3)
    fun shouldIterateOnDecoratedStep() {
        val testEntity = TestEntity()
        val capturedContexts = mutableListOf<StepContext<*, *>>()
        val capturedInputs = mutableListOf<Any?>()
        val decoratedStep = MapStep<TestEntity, Any>("", null) {
            capturedInputs.add(it)
        }

        val step = spyk(IterativeStepDecorator(10, decorated = decoratedStep))
        val ctx = StepTestHelper.createStepContext<TestEntity, Any>(input = testEntity)

        runBlocking {
            step.execute(ctx)
        }

        coVerifyExactly(10) { step.executeStep(refEq(decoratedStep), nrefEq(ctx)) }
        Assertions.assertFalse(ctx.exhausted)
        Assertions.assertEquals(10, capturedInputs.size)
        // The same input is always reused.
        capturedInputs.forEach {
            Assertions.assertSame(testEntity, it)
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        // The same input is always reused.
        capturedContexts.forEach {
            runBlocking {
                Assertions.assertSame(testEntity, it.input.receive())
            }
        }
    }

    @Test
    @Timeout(10)
    fun shouldIterateOnDecoratedStepWithDelay() {
        val executionTimestamps = mutableListOf<Long>()
        val decoratedStep: Step<Any, Any> = mockk {
            coEvery { execute(any()) } answers { executionTimestamps.add(System.currentTimeMillis()) }
            every { retryPolicy } returns null
            every { next } returns mutableListOf()
        }
        val step = spyk(IterativeStepDecorator(10, delay = Duration.ofMillis(10), decorated = decoratedStep))
        val ctx = StepTestHelper.createStepContext<Any, Any>(TestEntity())

        runBlocking {
            step.execute(ctx)
        }

        coVerifyExactly(10) { step.executeStep(refEq(decoratedStep), nrefEq(ctx)) }
        Assertions.assertFalse(ctx.exhausted)
        Assertions.assertEquals(10, executionTimestamps.size)
        // The delay between each step should be at least 10.
        for (i in 1 until executionTimestamps.size) {
            Assertions.assertTrue((executionTimestamps[i] - executionTimestamps[i - 1]) >= 10)
        }
    }

    @Test
    @Timeout(3)
    fun shouldForwardFailure() {
        val executionCount = AtomicInteger(0)
        val decoratedStep: Step<Any, Any> = mockk {
            coEvery { execute(any()) } answers {
                throw RuntimeException("Error ${executionCount.incrementAndGet()}")
            }
            every { retryPolicy } returns null
            every { next } returns mutableListOf()
        }
        val step = spyk(IterativeStepDecorator(10, decorated = decoratedStep))
        val ctx = StepTestHelper.createStepContext<Any, Any>(TestEntity())
        assertThrows<RuntimeException> {
            runBlocking {
                step.execute(ctx)
            }
        }

        coVerifyOnce { step.executeStep(refEq(decoratedStep), nrefEq(ctx)) }
        // We let the runner take care of the error management.
        Assertions.assertFalse(ctx.exhausted)
        Assertions.assertEquals(1, executionCount.get())
        Assertions.assertTrue(ctx.errors.isEmpty())
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }

    class TestEntity
}