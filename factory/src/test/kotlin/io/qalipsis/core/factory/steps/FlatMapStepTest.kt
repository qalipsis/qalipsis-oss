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
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.consumeAsFlow
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Eric Jessé
 */
internal class FlatMapStepTest {

    @field:RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    @Timeout(1)
    fun `should convert array with default step`() = testCoroutineDispatcher.runTest {
        val step = FlatMapStep<Array<Int>, Int>("", null)
        val ctx = StepTestHelper.createStepContext<Array<Int>, Int>(
            input = IntArray(10) { it }.toTypedArray(), outputChannel = Channel(Channel.UNLIMITED)
        )

        step.execute(ctx)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        val result = mutableListOf<Int?>()
        ctx.output.close()
        (ctx.output as Channel).consumeAsFlow().collect { record -> result.add(record.value) }

        Assertions.assertEquals(result, IntArray(10) { it }.toList())
    }

    @Test
    @Timeout(1)
    fun `should convert collection with default step`() = testCoroutineDispatcher.runTest {
        val step = FlatMapStep<Collection<Int>, Int>("", null)
        val ctx = StepTestHelper.createStepContext<Collection<Int>, Int>(
            input = IntArray(10) { it }.toList(), outputChannel = Channel(Channel.UNLIMITED)
        )

        step.execute(ctx)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        val result = mutableListOf<Int?>()
        ctx.output.close()
        (ctx.output as Channel).consumeAsFlow().collect { record -> result.add(record.value) }

        Assertions.assertEquals(result, IntArray(10) { it }.toList())
    }

    @Test
    @Timeout(1)
    fun `should convert map with default step`() = testCoroutineDispatcher.runTest {
        val step = FlatMapStep<Map<Int, String>, Pair<Int, String>>("", null)
        val ctx = StepTestHelper.createStepContext<Map<Int, String>, Pair<Int, String>>(
            input = mapOf(1 to "1", 2 to "2", 3 to "3"), outputChannel = Channel(Channel.UNLIMITED)
        )

        step.execute(ctx)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        val result = mutableListOf<Pair<Int, String>?>()
        ctx.output.close()
        (ctx.output as Channel).consumeAsFlow().collect { record -> result.add(record.value) }

        Assertions.assertEquals(result, listOf(1 to "1", 2 to "2", 3 to "3"))
    }

    @Test
    @Timeout(1)
    fun `should convert null to empty with default step`() = testCoroutineDispatcher.runTest {
        val step = FlatMapStep<Collection<Int>?, Int>("", null)
        val ctx = StepTestHelper.createStepContext<Collection<Int>?, Int>(outputChannel = Channel(Channel.UNLIMITED))

        (ctx.input as Channel).send(null)
        step.execute(ctx)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        val result = mutableListOf<Int?>()
        ctx.output.close()
        (ctx.output as Channel).consumeAsFlow().collect { record -> result.add(record.value) }

        Assertions.assertTrue(result.isEmpty())
    }

    @Test
    @Timeout(1)
    fun `should simply forward non iterable with default step`() = testCoroutineDispatcher.runTest {
        val step = FlatMapStep<Int, Int>("", null)
        val ctx = StepTestHelper.createStepContext<Int, Int>(input = 1, outputChannel = Channel(Channel.UNLIMITED))

        step.execute(ctx)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)


        val result = mutableListOf<Int?>()
        ctx.output.close()
        (ctx.output as Channel).consumeAsFlow().collect { record -> result.add(record.value) }

        Assertions.assertEquals(result, listOf(1))
    }

    @Test
    @Timeout(1)
    fun `should convert input to sequence`() = testCoroutineDispatcher.runTest {
        val step = FlatMapStep<Int, Int>("", null) { IntArray(10) { it }.asFlow() }
        val ctx = StepTestHelper.createStepContext<Int, Int>(input = 1, outputChannel = Channel(Channel.UNLIMITED))

        step.execute(ctx)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        val result = mutableListOf<Int?>()
        ctx.output.close()
        (ctx.output as Channel).consumeAsFlow().collect { record -> result.add(record.value) }
        Assertions.assertEquals(result, IntArray(10) { it }.toList())
    }

    @Test
    @Timeout(1)
    fun `only the very latest record should receive the tail flag enabled`() = testCoroutineDispatcher.runTest {
        val step = FlatMapStep<Collection<Int>, Int>("", null)
        val ctx = StepTestHelper.createStepContext<Collection<Int>, Int>(
            input = IntArray(10) { it }.toList(), outputChannel = Channel(Channel.UNLIMITED)
        ).also { it.isTail = true }

        step.execute(ctx)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)

        val values = mutableListOf<Int?>()
        val tailFlags = mutableListOf<Boolean>()
        ctx.output.close()
        (ctx.output as Channel).consumeAsFlow().collect { record ->
            values.add(record.value)
            tailFlags.add(record.isTail)
        }

        Assertions.assertEquals(IntArray(10) { it }.toList(), values)
        assertThat(tailFlags).containsExactly(*((0..8).map { false }.toTypedArray() + true))
    }
}
