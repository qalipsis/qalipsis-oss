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

package io.qalipsis.core.factory.steps.zipLast

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.qalipsis.api.context.CorrelationRecord
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.messaging.broadcastTopic
import io.qalipsis.core.exceptions.NotInitializedStepException
import io.qalipsis.core.factory.steps.zip.RightSource
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

internal class ZipLastStepTest {

    @field:RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @Test
    @Timeout(3)
    internal fun `should not execute when not started`() = testCoroutineDispatcher.runTest {
        val step = ZipLastStep<Long, Unit>("corr", this, emptyList())
        val ctx = StepTestHelper.createStepContext<Long, Unit>()

        // then
        assertThrows<NotInitializedStepException> {
            step.execute(ctx)
        }
    }

    @Test
    @Timeout(3)
    internal fun `should unite last received value from the right`() = testCoroutineDispatcher.run {
        // given
        val topic = broadcastTopic<CorrelationRecord<TestLeftJoinEntity>>(2)
        val step = buildZipLastStep(this, topic)
        step.start(relaxedMockk())
        val entityFromLeft = TestLeftJoinEntity(123, "From Left")
        val entityFromFromRight1 = TestLeftJoinEntity(859, "From Right 1")
        val entityFromFromRight2 = TestLeftJoinEntity(111, "From Right 2")

        val ctx = buildStepContext(entityFromLeft)

        // when
        // Send with delay and in an order different from secondary correlations definitions.
        delay(100)
        topic.produceValue(CorrelationRecord(ctx.minionId, "secondary-2", entityFromFromRight2))
        topic.produceValue(CorrelationRecord(ctx.minionId, "secondary-1", entityFromFromRight1))
        delay(100) // Add a delay to let the message being processed in the topic.
        step.execute(ctx)

        // then
        Assertions.assertFalse(ctx.isExhausted)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        val output = ctx.consumeOutputValue()
        assertThat(output.first).isEqualTo(entityFromLeft)
        assertThat(output.second).isEqualTo(entityFromFromRight1)
    }

    @Test
    @Timeout(3)
    internal fun `should suspend until the value is received`() = testCoroutineDispatcher.run {
        // given
        val topic = broadcastTopic<CorrelationRecord<TestLeftJoinEntity>>(1)
        val step = buildZipLastStep(this, topic)
        step.start(relaxedMockk())
        val entityFromLeft = TestLeftJoinEntity(123, "From Left")
        val entityFromFromRight = TestLeftJoinEntity(859, "From Right 1")

        val ctx = buildStepContext(entityFromLeft)

        // when
        // Send with delay and in an order different from secondary correlations definitions.
        delay(100)
        topic.produceValue(CorrelationRecord(ctx.minionId, "secondary-1", entityFromFromRight))
        step.execute(ctx)

        // then
        Assertions.assertFalse(ctx.isExhausted)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
        val output = ctx.consumeOutputValue()
        assertThat(output.first).isEqualTo(entityFromLeft)
        assertThat(output.second).isEqualTo(entityFromFromRight)
    }

    private fun buildZipLastStep(
        coroutineScope: CoroutineScope,
        vararg topics: Topic<CorrelationRecord<TestLeftJoinEntity>>
    ): ZipLastStep<TestLeftJoinEntity, Pair<TestLeftJoinEntity, Any?>> {
        val secondaryCorrelations = topics.mapIndexed { index, topic ->
            RightSource("secondary-${index + 1}", topic)
        }
        return ZipLastStep("corr", coroutineScope, secondaryCorrelations)
    }

    private fun buildStepContext(entityFromLeft: TestLeftJoinEntity) =
        StepTestHelper.createStepContext<TestLeftJoinEntity, Pair<TestLeftJoinEntity, Any?>>(entityFromLeft)

    private class TestLeftJoinEntity(val key: Int, val value: Any)
}
