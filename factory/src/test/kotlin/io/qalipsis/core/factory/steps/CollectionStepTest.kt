/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
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
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.Timeout.ThreadMode.SEPARATE_THREAD
import java.time.Duration

/**
 * The tests run in virtual time: the step works in the [kotlinx.coroutines.test.TestScope.backgroundScope], so that
 * its periodic ticker is cancelled at the end of the test, and the timeouts are enforced in a separate thread,
 * in order to always end the test.
 */
internal class CollectionStepTest {

    @Test
    @Timeout(value = 10, threadMode = SEPARATE_THREAD)
    internal fun `should forward data when the batch size is reached`() = runTest {
        // given
        val step = CollectionStep<String>("", timeout = null, batchSize = 50, coroutineScope = backgroundScope)
        step.start(relaxedMockk())
        val count = 420 // 20 items should stay in the buffer and be never sent.
        val counter = SuspendedCountLatch(count.toLong())
        val batchCaptor = mutableListOf<Pair<String, List<String>>>()

        // when
        repeat(count) { index ->
            val output = Channel<StepContext.StepOutputRecord<List<String>>>(Channel.UNLIMITED)
            val ctx = StepTestHelper.createStepContext(
                minionId = "$index", input = "$index",
                outputChannel = output
            )
            this.launch {
                step.execute(ctx)
                // Only the records sent while the execution scope was active are captured.
                output.drainTo(batchCaptor, index)
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
    @Timeout(value = 5, threadMode = SEPARATE_THREAD)
    internal fun `should forward data when the timeout is reached`() = runTest {
        val step = CollectionStep<String>("", timeout = Duration.ofMillis(100), batchSize = Int.MAX_VALUE,
            coroutineScope = backgroundScope)
        step.start(relaxedMockk())
        val count = 120
        val counter = SuspendedCountLatch(count.toLong())
        val batchCaptor = mutableListOf<Pair<String, List<String>>>()

        // when
        repeat(count) { index ->
            delay(20)
            val output = Channel<StepContext.StepOutputRecord<List<String>>>(Channel.UNLIMITED)
            val ctx = StepTestHelper.createStepContext(
                minionId = "$index", input = "$index",
                outputChannel = output
            )
            this.launch {
                step.execute(ctx)
                // Only the records sent while the execution scope was active are captured.
                output.drainTo(batchCaptor, index)
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
    @Timeout(value = 10, threadMode = SEPARATE_THREAD)
    internal fun `should forward data when either the batch size of the timeout is reached`() = runTest {
        val batchSize = 60
        val step =
            CollectionStep<String>("", timeout = Duration.ofMillis(600), batchSize = batchSize, coroutineScope = backgroundScope)
        step.start(relaxedMockk())
        val count = 1030 // Should not be a multiple of batchSize.
        val counter = SuspendedCountLatch(count.toLong())
        val batchCaptor = mutableListOf<Pair<String, List<String>>>()

        // when
        launch {
            delay(100)
            repeat(count) { index ->
                delay((Math.random() * 10 + 5).toLong()) // Random delay between 5 and 15 ms.
                val output = Channel<StepContext.StepOutputRecord<List<String>>>(Channel.UNLIMITED)
                val ctx = StepTestHelper.createStepContext(
                    minionId = "$index", input = "$index",
                    outputChannel = output
                )
                this.launch {
                    step.execute(ctx)
                    // Only the records sent while the execution scope was active are captured.
                    output.drainTo(batchCaptor, index)
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

    /**
     * Moves the records already sent to the channel into [captor], attached to the index of the context.
     */
    private fun Channel<StepContext.StepOutputRecord<List<String>>>.drainTo(
        captor: MutableList<Pair<String, List<String>>>,
        index: Int
    ) {
        generateSequence { tryReceive().getOrNull() }.forEach { captor.add("$index" to it.value) }
    }
}
