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
import assertk.assertions.hasSize
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThanOrEqualTo
import io.mockk.clearMocks
import io.mockk.coEvery
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration

internal class CollectionStepTest {

    @Test
    @Timeout(10)
    internal fun `should forward data when the batch size is reached`() = runBlockingTest {
        // given
        val step = CollectionStep<String>("", timeout = null, batchSize = 50, coroutineScope = this)
        step.start(relaxedMockk())
        val count = 420 // 20 items should stay in the buffer and be never sent.
        val counter = SuspendedCountLatch(count.toLong())
        val batchCaptor = mutableListOf<Pair<String, List<String>>>()

        // when
        repeat(count) { index ->
            val output = relaxedMockk<SendChannel<StepContext.StepOutputRecord<List<String>>?>> {
                coEvery { send(any()) } answers { batchCaptor.add("$index" to firstArg<StepContext.StepOutputRecord<List<String>>>().value) }
            }
            val ctx = StepTestHelper.createStepContext(
                minionId = "$index", input = "$index",
                outputChannel = output
            )
            this.launch {
                step.execute(ctx)
                // Clears the mock to ensure that no call can be found after the execution scope was left.
                clearMocks(output)
                counter.decrement()
            }
        }

        // then
        counter.await()

        assertThat(batchCaptor).hasSize(count / 50)
        batchCaptor.forEach { (index, batch) ->
            assertThat(batch).hasSize(50)
            // Verifies that the latest item of the batch always belongs to the context it was sent with.
            assertThat(batch.last()).isEqualTo(index)
        }
    }

    @Test
    @Timeout(5)
    internal fun `should forward data when the timeout is reached`() = runBlockingTest {
        val step = CollectionStep<String>("", timeout = Duration.ofMillis(100), batchSize = Int.MAX_VALUE,
            coroutineScope = this)
        step.start(relaxedMockk())
        val count = 120
        val counter = SuspendedCountLatch(count.toLong())
        val batchCaptor = mutableListOf<Pair<String, List<String>>>()

        // when
        repeat(count) { index ->
            delay(20)
            val output = relaxedMockk<SendChannel<StepContext.StepOutputRecord<List<String>>>> {
                coEvery { send(any()) } answers { batchCaptor.add("$index" to firstArg<StepContext.StepOutputRecord<List<String>>>().value) }
            }
            val ctx = StepTestHelper.createStepContext(
                minionId = "$index", input = "$index",
                outputChannel = output
            )
            this.launch {
                step.execute(ctx)
                // Clears the mock to ensure that no call can be found after the execution scope was left.
                clearMocks(output)
                counter.decrement()
            }
        }

        // then
        counter.await()
        step.stop(relaxedMockk())

        assertThat(batchCaptor.size).isGreaterThanOrEqualTo(count / 5)
        batchCaptor.forEach { (index, batch) ->
            assertThat(batch.size).isBetween(1, 5)
            // Verifies that the latest item of the batch always belongs to the context it was sent with.
            assertThat(batch.last()).isEqualTo(index)
        }
    }

    @Test
    @Timeout(10)
    internal fun `should forward data when either the batch size of the timeout is reached`() = runBlockingTest {
        val batchSize = 60
        val step =
            CollectionStep<String>("", timeout = Duration.ofMillis(600), batchSize = batchSize, coroutineScope = this)
        step.start(relaxedMockk())
        val count = 1030 // Should not be a multiple of batchSize.
        val counter = SuspendedCountLatch(count.toLong())
        val batchCaptor = mutableListOf<Pair<String, List<String>>>()

        // when
        launch {
            delay(100)
            repeat(count) { index ->
                delay((Math.random() * 10 + 5).toLong()) // Random delay between 5 and 15 ms.
                val output = relaxedMockk<SendChannel<StepContext.StepOutputRecord<List<String>>>> {
                    coEvery { send(any()) } answers { batchCaptor.add("$index" to firstArg<StepContext.StepOutputRecord<List<String>>>().value) }
                }
                val ctx = StepTestHelper.createStepContext(
                    minionId = "$index", input = "$index",
                    outputChannel = output
                )
                this.launch {
                    step.execute(ctx)
                    // Clears the mock to ensure that no call can be found after the execution scope was left.
                    clearMocks(output)
                    counter.decrement()
                }
            }
        }

        // then
        counter.await()
        step.stop(relaxedMockk())

        assertThat(batchCaptor.size).isGreaterThan(count / batchSize)
        assertThat(batchCaptor.map { it.second }.flatten()).hasSize(count)
        batchCaptor.forEach { (index, batch) ->
            assertThat(batch.size).isLessThanOrEqualTo(batchSize)
            // Verifies that the latest item of the batch always belongs to the context it was sent with.
            assertThat(batch.last()).isEqualTo(index)
        }
    }
}
