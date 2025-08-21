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

package io.qalipsis.core.factory.steps.join

import assertk.assertThat
import assertk.assertions.containsExactly
import io.qalipsis.api.context.CorrelationRecord
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.messaging.broadcastTopic
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.core.exceptions.NotInitializedStepException
import io.qalipsis.core.factory.orchestration.MinionImpl
import io.qalipsis.core.factory.steps.join.catadioptre.hasKeyInCache
import io.qalipsis.core.factory.steps.join.catadioptre.isCacheEmpty
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration

internal class InnerJoinStepTest {

    @field:RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    @Timeout(1)
    internal fun `should not execute when not started`() = testCoroutineDispatcher.runTest {
        val step = InnerJoinStep<Long, Unit>("corr", this, { 1 }, emptyList(),
            Duration.ofSeconds(10), { _, _ -> }
        )
        val ctx = StepTestHelper.createStepContext<Long, Unit>()
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", pauseAtStart = false)

        // then
        assertThrows<NotInitializedStepException> {
            step.execute(minion, ctx)
        }
    }

    @Test
    @Timeout(2)
    internal fun `should suspend until all values are received`() = testCoroutineDispatcher.run {
        // given
        val topic1 = broadcastTopic<CorrelationRecord<TestLeftJoinEntity>>(1)
        val topic2 = broadcastTopic<CorrelationRecord<TestLeftJoinEntity>>(1)
        val step = buildInnerJoinStep(this, topic1, topic2)
        step.start(relaxedMockk())
        val entityFromLeft = TestLeftJoinEntity(123, "From Left")
        val entityFromFromRight1 = TestLeftJoinEntity(123, "From Right 1")
        val entityFromFromRight2 = TestLeftJoinEntity(123, "From Right 2")

        val ctx = buildStepContext(entityFromLeft)
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", pauseAtStart = false)

        // when
        val execution = launch {
            step.execute(minion, ctx)
        }
        // Send with delay and in an order different from secondary correlations definitions.
        delay(50)
        topic2.produceValue(CorrelationRecord(ctx.minionId, "secondary-2", entityFromFromRight2))
        delay(50)
        topic1.produceValue(CorrelationRecord(ctx.minionId, "secondary-1", entityFromFromRight1))

        // Wait for the execution to complete.
        execution.join()

        // then
        Assertions.assertFalse(ctx.isExhausted)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        val output = ctx.consumeOutputValue()
        assertThat(output).containsExactly(entityFromLeft.value, entityFromFromRight1.value, entityFromFromRight2.value)
        Assertions.assertFalse(step.hasKeyInCache(123))
    }

    @Test
    @Timeout(1)
    internal fun `should not suspend when all values are already received`() = testCoroutineDispatcher.run {
        // given
        val topic1 = broadcastTopic<CorrelationRecord<TestLeftJoinEntity>>(1)
        val topic2 = broadcastTopic<CorrelationRecord<TestLeftJoinEntity>>(1)
        val step = buildInnerJoinStep(this, topic1, topic2)
        step.start(relaxedMockk())
        val entityFromLeft = TestLeftJoinEntity(123, "From Left")
        val entityFromFromRight1 = TestLeftJoinEntity(123, "From Right 1")
        val entityFromFromRight2 = TestLeftJoinEntity(123, "From Right 2")
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", pauseAtStart = false)

        val ctx = buildStepContext(entityFromLeft)

        // when
        // Send with in an order different from the secondary correlations definitions.
        topic2.produceValue(CorrelationRecord(ctx.minionId, "secondary-2", entityFromFromRight2))
        topic1.produceValue(CorrelationRecord(ctx.minionId, "secondary-1", entityFromFromRight1))
        step.execute(minion, ctx)

        // then
        Assertions.assertFalse(ctx.isExhausted)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        val output = ctx.consumeOutputValue()
        assertThat(output).containsExactly(entityFromLeft.value, entityFromFromRight1.value, entityFromFromRight2.value)
        Assertions.assertFalse(step.hasKeyInCache(123))
    }

    /**
     * This test validates the concurrency behavior when running the unit tests massively.
     */
    @Timeout(2)
    @RepeatedTest(100)
    internal fun `should succeed when sending messages concurrently before the execution`() =
        testCoroutineDispatcher.run {
            // given
            val topic1 = broadcastTopic<CorrelationRecord<TestLeftJoinEntity>>(1)
            val topic2 = broadcastTopic<CorrelationRecord<TestLeftJoinEntity>>(1)
            val step = buildInnerJoinStep(this, topic1, topic2)
            step.start(relaxedMockk())
            val entityFromLeft = TestLeftJoinEntity(123, "From Left")
            val entityFromFromRight1 = TestLeftJoinEntity(123, "From Right 1")
            val entityFromFromRight2 = TestLeftJoinEntity(123, "From Right 2")
            val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", pauseAtStart = false)

            val ctx = buildStepContext(entityFromLeft)

            // when sending two messages concurrently
            val startLatch = SuspendedCountLatch(1)
            launch {
                startLatch.await()
                topic1.produceValue(CorrelationRecord(ctx.minionId, "secondary-1", entityFromFromRight1))
            }
            launch {
                startLatch.await()
                topic2.produceValue(CorrelationRecord(ctx.minionId, "secondary-2", entityFromFromRight2))
            }
            startLatch.release()
            step.execute(minion, ctx)

            // then
            Assertions.assertFalse(ctx.isExhausted)
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
            val output = ctx.consumeOutputValue()
            assertThat(output).containsExactly(
                entityFromLeft.value,
                entityFromFromRight1.value,
                entityFromFromRight2.value
            )
            Assertions.assertFalse(step.hasKeyInCache(123))
        }

    @Test
    @Timeout(2)
    internal fun `should not cache null key`() = testCoroutineDispatcher.run {
        // given
        val topic1 = broadcastTopic<CorrelationRecord<TestLeftJoinEntity>>(1)
        // The conversion from the right record to key always returns null.
        val step = InnerJoinStep<TestLeftJoinEntity, List<Any?>>(
            "corr",
            this,
            { record -> record.value.key },
            listOf(RightCorrelation(
                "secondary-1",
                // The conversion from record to key always returns null.
                topic1
            ) { null }
            ),
            Duration.ofSeconds(10)
        ) { leftValue, rightValues ->
            listOf(leftValue.value) + rightValues.values.toList()
        }

        val entityFromFromRight1 = TestLeftJoinEntity(123, "From Secondary 1")

        // when
        step.start(relaxedMockk())
        topic1.produceValue(CorrelationRecord("any", "secondary-1", entityFromFromRight1))
        // Wait for the data to be consumed.
        delay(20)

        // then
        Assertions.assertTrue(step.isCacheEmpty())
    }

    @Test
    @Timeout(3)
    internal fun `should not execute null key`() = testCoroutineDispatcher.runTest {
        // given
        // The conversion from the left record to key always returns null.
        val step = InnerJoinStep<TestLeftJoinEntity, List<Any?>>(
            "corr",
            this,
            { null },
            emptyList(),
            Duration.ofSeconds(10)
        ) { leftValue, rightValues ->
            listOf(leftValue.value) + rightValues.values.toList()
        }
        val ctx = buildStepContext(relaxedMockk { })
        step.start(relaxedMockk())
        val minion = MinionImpl("my-minion", "my-campaign", "my-scenario", pauseAtStart = false)

        // when
        step.execute(minion, ctx)

        // then
        Assertions.assertFalse(ctx.isExhausted)
        (ctx.output as Channel).apply {
            Assertions.assertFalse(isClosedForReceive)
            Assertions.assertTrue(isEmpty)
        }
        Assertions.assertTrue(step.isCacheEmpty())
    }

    private fun buildInnerJoinStep(
        coroutineScope: CoroutineScope,
        vararg topics: Topic<CorrelationRecord<TestLeftJoinEntity>>
    ): InnerJoinStep<TestLeftJoinEntity, List<Any?>> {
        val secondaryCorrelations = topics.mapIndexed { index, topic ->
            RightCorrelation("secondary-${index + 1}", topic) { record -> (record.value).key }
        }

        return InnerJoinStep(
            "corr",
            coroutineScope,
            { record -> (record.value).key },
            secondaryCorrelations,
            Duration.ZERO
        ) { leftValue, rightValues ->
            // Extract the string values only, for assertion purpose.
            val rv = rightValues.mapValues { (it.value as TestLeftJoinEntity).value }
            listOf(leftValue.value) + topics.mapIndexed { index, _ -> rv["secondary-${index + 1}"] }
        }
    }

    private fun buildStepContext(entityFromLeft: TestLeftJoinEntity) =
        StepTestHelper.createStepContext<TestLeftJoinEntity, List<Any?>>(entityFromLeft)

    private class TestLeftJoinEntity(val key: Int, val value: Any)
}
