package io.evolue.core.factories.steps

import io.evolue.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * @author Eric Jessé
 */
internal class FlatMapStepTest {

    @Test
    @Timeout(1)
    fun shouldConvertArrayWithDefaultStep() {
        val step = FlatMapStep<Array<Int>, Int>("", null)
        val ctx = StepTestHelper.createStepContext<Array<Int>, Int>(
            input = IntArray(10) { it }.toTypedArray(), outputChannel = Channel(Channel.UNLIMITED)
        )

        runBlocking {
            step.execute(ctx)
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        val result = mutableListOf<Int?>()
        runBlocking {
            ctx.output.close()
            (ctx.output as Channel).consumeAsFlow().collect { value -> result.add(value) }
        }

        Assertions.assertEquals(result, IntArray(10) { it }.toList())
    }

    @Test
    @Timeout(1)
    fun shouldConvertCollectionWithDefaultStep() {
        val step = FlatMapStep<Collection<Int>, Int>("", null)
        val ctx = StepTestHelper.createStepContext<Collection<Int>, Int>(
            input = IntArray(10) { it }.toList(), outputChannel = Channel(Channel.UNLIMITED)
        )

        runBlocking {
            step.execute(ctx)
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        val result = mutableListOf<Int?>()
        runBlocking {
            ctx.output.close()
            (ctx.output as Channel).consumeAsFlow().collect { value -> result.add(value) }
        }

        Assertions.assertEquals(result, IntArray(10) { it }.toList())
    }

    @Test
    @Timeout(1)
    fun shouldConvertMapWithDefaultStep() {
        val step = FlatMapStep<Map<Int, String>, Pair<Int, String>>("", null)
        val ctx = StepTestHelper.createStepContext<Map<Int, String>, Pair<Int, String>>(
            input = mapOf(1 to "1", 2 to "2", 3 to "3"), outputChannel = Channel(Channel.UNLIMITED)
        )

        runBlocking {
            step.execute(ctx)
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        val result = mutableListOf<Pair<Int, String>?>()
        runBlocking {
            ctx.output.close()
            (ctx.output as Channel).consumeAsFlow().collect { value -> result.add(value) }
        }

        Assertions.assertEquals(result, listOf(1 to "1", 2 to "2", 3 to "3"))
    }

    @Test
    @Timeout(1)
    fun shouldConvertNullToEmptyWithDefaultStep() {
        val step = FlatMapStep<Collection<Int>?, Int>("", null)
        val ctx = StepTestHelper.createStepContext<Collection<Int>?, Int>(outputChannel = Channel(Channel.UNLIMITED))

        runBlocking {
            (ctx.input as Channel).send(null)
            step.execute(ctx)
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        val result = mutableListOf<Int?>()
        runBlocking {
            ctx.output.close()
            (ctx.output as Channel).consumeAsFlow().collect { value -> result.add(value) }
        }

        Assertions.assertTrue(result.isEmpty())
    }

    @Test
    @Timeout(1)
    fun shouldSimplyForwardNonIterableWithDefaultStep() {
        val step = FlatMapStep<Int, Int>("", null)
        val ctx = StepTestHelper.createStepContext<Int, Int>(input = 1, outputChannel = Channel(Channel.UNLIMITED))

        runBlocking {
            step.execute(ctx)
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)


        val result = mutableListOf<Int?>()
        runBlocking {
            ctx.output.close()
            (ctx.output as Channel).consumeAsFlow().collect { value -> result.add(value) }
        }

        Assertions.assertEquals(result, listOf(1))
    }

    @Test
    @Timeout(1)
    fun shouldConvertInputToSequence() {
        val step = FlatMapStep<Int, Int>("", null) { value -> IntArray(10) { it }.asFlow() }
        val ctx = StepTestHelper.createStepContext<Int, Int>(input = 1, outputChannel = Channel(Channel.UNLIMITED))

        runBlocking {
            step.execute(ctx)
        }
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        val result = mutableListOf<Int?>()
        runBlocking {
            ctx.output.close()
            (ctx.output as Channel).consumeAsFlow().collect { value -> result.add(value) }
        }
        Assertions.assertEquals(result, IntArray(10) { it }.toList())
    }
}
