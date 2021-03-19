package io.qalipsis.core.factories.steps

import assertk.assertThat
import assertk.assertions.*
import io.mockk.clearMocks
import io.mockk.coEvery
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.RepeatedTest
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
            val output = relaxedMockk<SendChannel<List<String>?>> {
                coEvery { send(any()) } answers { batchCaptor.add("$index" to firstArg()) }
            }
            val ctx = StepTestHelper.createStepContext<String, List<String>>(minionId = "$index", input = "$index",
                outputChannel = output)
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
            val output = relaxedMockk<SendChannel<List<String>?>> {
                coEvery { send(any()) } answers { batchCaptor.add("$index" to firstArg()) }
            }
            val ctx = StepTestHelper.createStepContext<String, List<String>>(minionId = "$index", input = "$index",
                outputChannel = output)
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
                val output = relaxedMockk<SendChannel<List<String>?>> {
                    coEvery { send(any()) } answers { batchCaptor.add("$index" to firstArg()) }
                }
                val ctx = StepTestHelper.createStepContext<String, List<String>>(minionId = "$index", input = "$index",
                    outputChannel = output)
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
