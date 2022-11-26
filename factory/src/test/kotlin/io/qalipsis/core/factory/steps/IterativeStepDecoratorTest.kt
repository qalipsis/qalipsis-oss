/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.steps

import assertk.assertThat
import assertk.assertions.containsExactly
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.spyk
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepContext.StepOutputRecord
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.runtime.Minion
import io.qalipsis.api.steps.Step
import io.qalipsis.core.factory.coreStepContext
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyExactly
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.time.QalipsisTimeAssertions
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
@WithMockk
internal class IterativeStepDecoratorTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    lateinit var minion: Minion

    @RelaxedMockK
    private lateinit var decorated: Step<Any, Int>

    private val mockedIterativeStepDecorator: IterativeStepDecorator<Any, Int> by lazy(LazyThreadSafetyMode.NONE) {
        IterativeStepDecorator(1, relaxedMockk(), decorated)
    }

    @Test
    internal fun `should init the decorated step`() = testDispatcherProvider.runTest {
        // when
        mockedIterativeStepDecorator.init()

        // then
        coVerifyOnce { decorated.init() }
    }

    @Test
    internal fun `should destroy the decorated step`() = testDispatcherProvider.runTest {
        // when
        mockedIterativeStepDecorator.destroy()

        // then
        coVerifyOnce { decorated.destroy() }
    }

    @Test
    internal fun `should add next step to the decorated step`() = testDispatcherProvider.runTest {
        // given
        val next: Step<Any, Any> = relaxedMockk()

        // when
        mockedIterativeStepDecorator.addNext(next)

        // then
        coVerifyOnce { decorated.addNext(refEq(next)) }
    }

    @Test
    internal fun `should start the decorated step`() = testDispatcherProvider.runTest {
        // given
        val startContext: StepStartStopContext = relaxedMockk()

        // when
        mockedIterativeStepDecorator.start(startContext)

        // then
        coVerifyOnce { decorated.start(refEq(startContext)) }
    }

    @Test
    internal fun `should stop the decorated step`() = testDispatcherProvider.runTest {
        // given
        val startContext: StepStartStopContext = relaxedMockk()

        // when
        mockedIterativeStepDecorator.stop(startContext)

        // then
        coVerifyOnce { decorated.stop(refEq(startContext)) }
    }

    @Test
    @Timeout(3)
    fun `should iterate on decorated step with tail only at end if originally set`() = testDispatcherProvider.runTest {
        val testEntity = TestEntity()
        val capturedContexts = mutableListOf<StepContext<*, *>>()
        val capturedInputs = mutableListOf<Any?>()
        val decoratedStep: Step<TestEntity, Int> = relaxedMockk()

        val innerContextTailFlags = mutableListOf<Boolean>()
        val outerContextTailFlags = mutableListOf<Boolean>()

        val step = spyk(IterativeStepDecorator(10, decorated = decoratedStep))
        val outputChannel = Channel<StepOutputRecord<Int>?>(Channel.UNLIMITED)
        val outerContext = coreStepContext(input = testEntity, outputChannel = outputChannel).also { it.isTail = true }
        var counter = 0
        coEvery { step.executeStep(refEq(minion), refEq(decoratedStep), any()) } coAnswers {
            val innerContext = thirdArg<StepContext<TestEntity, Int>>()
            innerContextTailFlags.add(innerContext.isTail)
            capturedInputs.add(innerContext.receive())
            outerContextTailFlags.add(outerContext.isTail)

            // Two values are sent each for each call.
            innerContext.send(counter++)
            innerContext.send(counter++)
        }

        // when
        step.execute(minion, outerContext)

        // then
        coVerifyExactly(10) {
            step.executeStep(refEq(minion), refEq(decoratedStep), nrefEq(outerContext))
        }

        // Tail flag should be false until the latest iteration.
        repeat(9) {
            Assertions.assertFalse(innerContextTailFlags[it])
            Assertions.assertFalse(outerContextTailFlags[it])
        }
        // The latest inner context has the tail flag.
        Assertions.assertTrue(innerContextTailFlags[9])
        // The outer context still does not has the tail during the execution of the decorated step.
        Assertions.assertFalse(outerContextTailFlags[9])
        // At the end of the step, the outer context also gets the tail back.
        Assertions.assertTrue(outerContext.isTail)

        Assertions.assertFalse(outerContext.isExhausted)
        Assertions.assertEquals(10, capturedInputs.size)
        // The same input is always reused.
        capturedInputs.forEach {
            Assertions.assertSame(testEntity, it)
        }
        Assertions.assertFalse((outerContext.output as Channel).isClosedForReceive)

        // The same input is always reused.
        capturedContexts.forEach {
            Assertions.assertSame(testEntity, it.receive())
        }

        // Close the output channel to read it all.
        outputChannel.close()
        // Verifies that only the very latest record is marked with "isTail".
        val outputTails = mutableListOf<Boolean>()
        outputChannel.consumeEach { outputTails.add(it!!.isTail) }
        assertThat(outputTails).containsExactly(*((0..18).map { false }.toTypedArray() + true))
    }

    @Test
    @Timeout(3)
    fun `should iterate on decorated step with tail only at end`() = testDispatcherProvider.runTest {
        val testEntity = TestEntity()
        val capturedContexts = mutableListOf<StepContext<*, *>>()
        val capturedInputs = mutableListOf<Any?>()
        val decoratedStep: Step<TestEntity, Any> = relaxedMockk()

        val innerContextTailFlags = mutableListOf<Boolean>()
        val outerContextTailFlags = mutableListOf<Boolean>()
        val step = spyk(IterativeStepDecorator(10, decorated = decoratedStep))
        val ctx = coreStepContext<TestEntity, Any>(input = testEntity).also { it.isTail = false }
        coEvery { step.executeStep(refEq(minion), refEq(decoratedStep), any()) } coAnswers {
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
    fun `should iterate on decorated step with delay even when the step does not consume the input`() =
        testDispatcherProvider.run {
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
    @Timeout(10)
    fun `should iterate on decorated step until no more data is generated and keep isTail false`() =
        testDispatcherProvider.run {
            val decoratedStep: Step<TestEntity, Any> = mockk {
                coEvery { execute(any(), any()) } coAnswers {
                    val context = secondArg<StepContext<TestEntity, Any>>()
                    context.send("1")
                } coAndThen {
                    val context = secondArg<StepContext<TestEntity, Any>>()
                    context.send("2")
                } coAndThen {
                    val context = secondArg<StepContext<TestEntity, Any>>()
                    context.isTail = true
                }
                every { retryPolicy } returns null
                every { next } returns emptyList()
            }
            val step = spyk(IterativeStepDecorator(10, delay = Duration.ofMillis(10), decorated = decoratedStep))
            val ctx = coreStepContext<TestEntity, Any>(TestEntity())
            ctx.isTail = false

            step.execute(minion, ctx)

            coVerifyExactly(3) { step.executeStep(refEq(minion), refEq(decoratedStep), nrefEq(ctx)) }
            val receivedValues = (ctx.output as ReceiveChannel<StepContext.StepOutputRecord<Any>>).let {
                listOf(it.receive(), it.receive()).map { record -> record.value }
            }
            assertThat(receivedValues).containsExactly("1", "2")
            Assertions.assertFalse(ctx.isExhausted)
            Assertions.assertFalse(ctx.isTail)
        }

    @Test
    @Timeout(10)
    fun `should iterate on decorated step until no more data is generated and keep isTail true`() =
        testDispatcherProvider.run {
            val decoratedStep: Step<TestEntity, Any> = mockk {
                coEvery { execute(any(), any()) } coAnswers {
                    val context = secondArg<StepContext<TestEntity, Any>>()
                    context.send("1")
                } coAndThen {
                    val context = secondArg<StepContext<TestEntity, Any>>()
                    context.send("2")
                } coAndThen {
                    val context = secondArg<StepContext<TestEntity, Any>>()
                    context.isTail = true
                }
                every { retryPolicy } returns null
                every { next } returns emptyList()
            }
            val step = spyk(IterativeStepDecorator(10, delay = Duration.ofMillis(10), decorated = decoratedStep))
            val ctx = coreStepContext<TestEntity, Any>(TestEntity())
            ctx.isTail = true

            step.execute(minion, ctx)

            coVerifyExactly(3) { step.executeStep(refEq(minion), refEq(decoratedStep), nrefEq(ctx)) }
            val receivedValues = (ctx.output as ReceiveChannel<StepContext.StepOutputRecord<Any>>).let {
                listOf(it.receive(), it.receive()).map { record -> record.value }
            }
            assertThat(receivedValues).containsExactly("1", "2")
            Assertions.assertFalse(ctx.isExhausted)
            Assertions.assertTrue(ctx.isTail)
        }

    @Test
    @Timeout(3)
    fun `should forward failure and set the tail flag back to true if originally set`() =
        testDispatcherProvider.runTest {
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
    fun `should forward failure and keep the tail flag back to false if originally unset`() =
        testDispatcherProvider.runTest {
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
