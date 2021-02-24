package io.qalipsis.core.factories.steps

import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.orchestration.factories.Minion
import io.qalipsis.api.steps.Step
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyExactly
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.steps.StepTestHelper
import io.qalipsis.test.time.QalipsisTimeAssertions
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
@Suppress("EXPERIMENTAL_API_USAGE")
@WithMockk
internal class IterativeStepDecoratorTest {

    @RelaxedMockK
    lateinit var minion: Minion

    @Test
    @Timeout(3)
    fun shouldIterateOnDecoratedStep() = runBlockingTest {
        val testEntity = TestEntity()
        val capturedContexts = mutableListOf<StepContext<*, *>>()
        val capturedInputs = mutableListOf<Any?>()
        val decoratedStep = MapStep<TestEntity, Any>("", null) {
            capturedInputs.add(it)
        }

        val step = spyk(IterativeStepDecorator(10, decorated = decoratedStep))
        val ctx = StepTestHelper.createStepContext<TestEntity, Any>(input = testEntity)

        step.execute(minion, ctx)

        coVerifyExactly(10) { step.executeStep(refEq(minion), refEq(decoratedStep), nrefEq(ctx)) }
        Assertions.assertFalse(ctx.isExhausted)
        Assertions.assertEquals(10, capturedInputs.size)
        // The same input is always reused.
        capturedInputs.forEach {
            Assertions.assertSame(testEntity, it)
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        // The same input is always reused.
        capturedContexts.forEach {
            Assertions.assertSame(testEntity, it.input.receive())
        }
    }

    @Test
    @Timeout(10)
    fun shouldIterateOnDecoratedStepWithDelay() = runBlocking {
        val executionTimestamps = mutableListOf<Long>()
        val decoratedStep: Step<Any, Any> = mockk {
            coEvery { execute(any(), any()) } answers { executionTimestamps.add(System.currentTimeMillis()) }
            every { retryPolicy } returns null
            every { next } returns mutableListOf()
        }
        val step = spyk(IterativeStepDecorator(10, delay = Duration.ofMillis(10), decorated = decoratedStep))
        val ctx = StepTestHelper.createStepContext<Any, Any>(TestEntity())

        step.execute(minion, ctx)

        coVerifyExactly(10) { step.executeStep(refEq(minion), refEq(decoratedStep), nrefEq(ctx)) }
        Assertions.assertFalse(ctx.isExhausted)
        Assertions.assertEquals(10, executionTimestamps.size)
        // The delay between each step should be at least 10.
        for (i in 1 until executionTimestamps.size) {
            QalipsisTimeAssertions.assertLongerOrEqualTo(Duration.ofMillis(10),
                Duration.ofMillis(executionTimestamps[i] - executionTimestamps[i - 1]))
        }
    }

    @Test
    @Timeout(3)
    fun shouldForwardFailure() = runBlockingTest {
        val executionCount = AtomicInteger(0)
        val decoratedStep: Step<Any, Any> = mockk {
            coEvery { execute(any(), any()) } answers {
                throw RuntimeException("Error ${executionCount.incrementAndGet()}")
            }
            every { retryPolicy } returns null
            every { next } returns mutableListOf()
        }
        val step = spyk(IterativeStepDecorator(10, decorated = decoratedStep))
        val ctx = StepTestHelper.createStepContext<Any, Any>(TestEntity())
        assertThrows<RuntimeException> {
            step.execute(minion, ctx)
        }

        coVerifyOnce { step.executeStep(refEq(minion), refEq(decoratedStep), nrefEq(ctx)) }
        // We let the runner take care of the error management.
        Assertions.assertFalse(ctx.isExhausted)
        Assertions.assertEquals(1, executionCount.get())
        Assertions.assertTrue(ctx.errors.isEmpty())
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }

    class TestEntity
}
