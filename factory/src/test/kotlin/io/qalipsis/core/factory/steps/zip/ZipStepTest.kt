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

package io.qalipsis.core.factory.steps.zip

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.qalipsis.api.context.CorrelationRecord
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.messaging.broadcastTopic
import io.qalipsis.core.exceptions.NotInitializedStepException
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

internal class ZipStepTest {

    @field:RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    @Timeout(3)
    internal fun `should not execute when not started`() = testCoroutineDispatcher.runTest {
        val step = ZipStep<Long, Unit>("corr", this, emptyList())
        val ctx = StepTestHelper.createStepContext<Long, Unit>()

        // then
        assertThrows<NotInitializedStepException> {
            step.execute(ctx)
        }
    }

    @Test
    @Timeout(3)
    internal fun `should suspend until all values are received`() = testCoroutineDispatcher.run {
        // given
        val topic = broadcastTopic<CorrelationRecord<TestLeftJoinEntity>>(1)
        val step = buildZipStep(this, topic)
        step.start(relaxedMockk())
        val entityFromLeft = TestLeftJoinEntity(123, "From Left")
        val entityFromFromRight1 = TestLeftJoinEntity(567, "From Right 1")
        val entityFromFromRight2 = TestLeftJoinEntity(841, "From Right 2")

        val ctx = buildStepContext(entityFromLeft)

        // when
        val execution = launch {
            step.execute(ctx)
        }
        // Send with delay.
        delay(50)
        topic.produceValue(CorrelationRecord(ctx.minionId, "secondary-1", entityFromFromRight1))
        topic.produceValue(CorrelationRecord(ctx.minionId, "secondary-2", entityFromFromRight2))
        // Wait for the execution to complete.
        execution.join()

        // then
        Assertions.assertFalse(ctx.isExhausted)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        val output = ctx.consumeOutputValue()
        assertThat(output.first).isEqualTo(entityFromLeft)
        assertThat(output.second).isEqualTo(entityFromFromRight1)
    }

    @Test
    @Timeout(3)
    internal fun `should not suspend when all values are already received`() = testCoroutineDispatcher.run {
        // given
        val topic = broadcastTopic<CorrelationRecord<TestLeftJoinEntity>>(1)
        val step = buildZipStep(this, topic)
        step.start(relaxedMockk())
        val entityFromLeft = TestLeftJoinEntity(123, "From Left")
        val entityFromFromRight = TestLeftJoinEntity(123, "From Right")

        val ctx = buildStepContext(entityFromLeft)

        // when
        topic.produceValue(CorrelationRecord(ctx.minionId, "secondary-1", entityFromFromRight))
        step.execute(ctx)

        // then
        Assertions.assertFalse(ctx.isExhausted)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        val output = ctx.consumeOutputValue()
        assertThat(output.first).isEqualTo(entityFromLeft)
        assertThat(output.second).isEqualTo(entityFromFromRight)
    }

    private fun buildZipStep(
        coroutineScope: CoroutineScope,
        vararg topics: Topic<CorrelationRecord<TestLeftJoinEntity>>
    ): ZipStep<TestLeftJoinEntity, Pair<TestLeftJoinEntity, Any?>> {
        val secondaryCorrelations = topics.mapIndexed { index, topic ->
            RightSource("secondary-${index + 1}", topic)
        }
        return ZipStep("corr", coroutineScope, secondaryCorrelations)
    }

    private fun buildStepContext(entityFromLeft: TestLeftJoinEntity) =
        StepTestHelper.createStepContext<TestLeftJoinEntity, Pair<TestLeftJoinEntity, Any?>>(entityFromLeft)

    private class TestLeftJoinEntity(val key: Int, val value: Any)
}
