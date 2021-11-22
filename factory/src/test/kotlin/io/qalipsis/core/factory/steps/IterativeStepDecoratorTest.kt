package io.qalipsis.core.factory.steps

import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.orchestration.factories.Minion
import io.qalipsis.api.steps.Step
import io.qalipsis.core.factory.coreStepContext
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyExactly
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
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
@WithMockk
internal class IterativeStepDecoratorTest {

    @RelaxedMockK
    lateinit var minion: Minion

    @RelaxedMockK
    private lateinit var decorated: Step<Any, Int>

    private val mockedIterativeStepDecorator: IterativeStepDecorator<Any, Int> by lazy(LazyThreadSafetyMode.NONE){
        IterativeStepDecorator(1, relaxedMockk(), decorated)
    }

    @Test
    internal fun `should init the decorated step`() = runBlockingTest {
        // when
        mockedIterativeStepDecorator.init()

        // then
        coVerifyOnce { decorated.init() }
    }

    @Test
    internal fun `should destroy the decorated step`() = runBlockingTest {
        // when
        mockedIterativeStepDecorator.destroy()

        // then
        coVerifyOnce { decorated.destroy() }
    }

    @Test
    internal fun `should add next step to the decorated step`() = runBlockingTest {
        // given
        val next: Step<Any, Any> = relaxedMockk()

        // when
        mockedIterativeStepDecorator.addNext(next)

        // then
        coVerifyOnce { decorated.addNext(refEq(next)) }
    }

    @Test
    internal fun `should start the decorated step`() = runBlockingTest {
        // given
        val startContext: StepStartStopContext = relaxedMockk()

        // when
        mockedIterativeStepDecorator.start(startContext)

        // then
        coVerifyOnce { decorated.start(refEq(startContext)) }
    }

    @Test
    internal fun `should stop the decorated step`() = runBlockingTest {
        // given
        val startContext: StepStartStopContext = relaxedMockk()

        // when
        mockedIterativeStepDecorator.stop(startContext)

        // then
        coVerifyOnce { decorated.stop(refEq(startContext)) }
    }

    @Test
    @Timeout(3)
    fun `should iterate on decorated step with tail only at end if originally set`() = runBlockingTest {
        val testEntity = TestEntity()
        val capturedContexts = mutableListOf<StepContext<*, *>>()
        val capturedInputs = mutableListOf<Any?>()
        val decoratedStep : Step<TestEntity, Any> = relaxedMockk()

        val innerContextTailFlags = mutableListOf<Boolean>()
        val outerContextTailFlags = mutableListOf<Boolean>()
        val step = spyk(IterativeStepDecorator(10, decorated = decoratedStep))
        val ctx = coreStepContext<TestEntity, Any>(input = testEntity).also { it.isTail = true }
        coEvery { step.executeStep(refEq(minion), refEq(decoratedStep), any())  } coAnswers  {
            val innerContext = thirdArg<StepContext<*, *>>()
            innerContextTailFlags.add(innerContext.isTail)
            capturedInputs.add(innerContext.receive())
            outerContextTailFlags.add(ctx.isTail)
        }

        step.execute(minion, ctx)

        coVerifyExactly(10) {
            step.executeStep(refEq(minion), refEq(decoratedStep), nrefEq(ctx))
        }

        // Tail flag should be false until the latest iteration.
        repeat(9) {
            Assertions.assertFalse(innerContextTailFlags[it])
            Assertions.assertFalse(outerContextTailFlags[it])
        }
        Assertions.assertTrue(innerContextTailFlags[9])
        Assertions.assertTrue(outerContextTailFlags[9])
        Assertions.assertTrue(ctx.isTail)

        Assertions.assertFalse(ctx.isExhausted)
        Assertions.assertEquals(10, capturedInputs.size)
        // The same input is always reused.
        capturedInputs.forEach {
            Assertions.assertSame(testEntity, it)
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        // The same input is always reused.
        capturedContexts.forEach {
            Assertions.assertSame(testEntity, it.receive())
        }
    }

    @Test
    @Timeout(3)
    fun `should iterate on decorated step with tail only at end`() = runBlockingTest {
        val testEntity = TestEntity()
        val capturedContexts = mutableListOf<StepContext<*, *>>()
        val capturedInputs = mutableListOf<Any?>()
        val decoratedStep : Step<TestEntity, Any> = relaxedMockk()

        val innerContextTailFlags = mutableListOf<Boolean>()
        val outerContextTailFlags = mutableListOf<Boolean>()
        val step = spyk(IterativeStepDecorator(10, decorated = decoratedStep))
        val ctx = coreStepContext<TestEntity, Any>(input = testEntity).also { it.isTail = false }
        coEvery { step.executeStep(refEq(minion), refEq(decoratedStep), any())  } coAnswers  {
            val innerContext = thirdArg<StepContext<*, *>>()
            innerContextTailFlags.add(innerContext.isTail)
            capturedInputs.add(innerContext.receive())
            outerContextTailFlags.add(ctx.isTail)
        }

        step.execute(minion, ctx)

        coVerifyExactly(10) {
            step.executeStep(refEq(minion), refEq(decoratedStep), nrefEq(ctx))
        }

        // Tail flag should be always be false.
        repeat(10) {
            Assertions.assertFalse(innerContextTailFlags[it])
            Assertions.assertFalse(outerContextTailFlags[it])
        }
        Assertions.assertFalse(ctx.isTail)
        Assertions.assertFalse(ctx.isExhausted)
        Assertions.assertEquals(10, capturedInputs.size)
        // The same input is always reused.
        capturedInputs.forEach {
            Assertions.assertSame(testEntity, it)
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        // The same input is always reused.
        capturedContexts.forEach {
            Assertions.assertSame(testEntity, it.receive())
        }
    }

    @Test
    @Timeout(10)
    fun `should iterate on decorated step with delay`() = runBlocking {
        val executionTimestamps = mutableListOf<Long>()
        val decoratedStep: Step<TestEntity, Any> = mockk {
            coEvery { execute(any(), any()) } answers { executionTimestamps.add(System.currentTimeMillis()) }
            every { retryPolicy } returns null
            every { next } returns mutableListOf()
        }
        val step = spyk(IterativeStepDecorator(10, delay = Duration.ofMillis(10), decorated = decoratedStep))
        val ctx = coreStepContext<TestEntity, Any>(TestEntity())

        step.execute(minion, ctx)

        coVerifyExactly(10) { step.executeStep(refEq(minion), refEq(decoratedStep), nrefEq(ctx)) }
        Assertions.assertFalse(ctx.isExhausted)
        Assertions.assertEquals(10, executionTimestamps.size)
        // The delay between each step should be at least 10.
        for (i in 1 until executionTimestamps.size) {
            QalipsisTimeAssertions.assertLongerOrEqualTo(
                Duration.ofMillis(10),
                Duration.ofMillis(executionTimestamps[i] - executionTimestamps[i - 1])
            )
        }
    }

    @Test
    @Timeout(3)
    fun `should forward failure and set the tail flag back to true if originally set`() = runBlockingTest {
        val executionCount = AtomicInteger(0)
        val decoratedStep: Step<Any, Any> = mockk {
            coEvery { execute(any(), any()) } answers {
                throw RuntimeException("Error ${executionCount.incrementAndGet()}")
            }
            every { retryPolicy } returns null
            every { next } returns mutableListOf()
        }
        val step = spyk(IterativeStepDecorator(10, decorated = decoratedStep))
        val ctx = coreStepContext<Any, Any>(TestEntity()).also { it.isTail = true }
        assertThrows<RuntimeException> {
            step.execute(minion, ctx)
        }

        Assertions.assertTrue(ctx.isTail)
        coVerifyOnce { step.executeStep(refEq(minion), refEq(decoratedStep), nrefEq(ctx)) }
        // We let the runner take care of the error management.
        Assertions.assertFalse(ctx.isExhausted)
        Assertions.assertEquals(1, executionCount.get())
        Assertions.assertTrue(ctx.errors.isEmpty())
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }

    @Test
    @Timeout(3)
    fun `should forward failure and keep the tail flag back to false if originally unset`() = runBlockingTest {
        val executionCount = AtomicInteger(0)
        val decoratedStep: Step<TestEntity, Any> = mockk {
            coEvery { execute(any(), any()) } answers {
                throw RuntimeException("Error ${executionCount.incrementAndGet()}")
            }
            every { retryPolicy } returns null
            every { next } returns mutableListOf()
        }
        val step = spyk(IterativeStepDecorator(10, decorated = decoratedStep))
        val ctx = coreStepContext<TestEntity, Any>(TestEntity()).also { it.isTail = false }
        assertThrows<RuntimeException> {
            step.execute(minion, ctx)
        }

        Assertions.assertFalse(ctx.isTail)
        coVerifyOnce { step.executeStep(refEq(minion), refEq(decoratedStep), nrefEq(ctx)) }
        // We let the runner take care of the error management.
        Assertions.assertFalse(ctx.isExhausted)
        Assertions.assertEquals(1, executionCount.get())
        Assertions.assertTrue(ctx.errors.isEmpty())
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }

    class TestEntity
}
