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

package io.qalipsis.core.factory.steps.topicrelatedsteps

import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.DefaultCompletionContext
import io.qalipsis.api.messaging.Topic
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 *
 * @author Eric Jessé
 */
@WithMockk
internal class TopicMirrorStepTest {

    @RelaxedMockK
    lateinit var dataTransferTopic: Topic<String>

    @Test
    @Timeout(3)
    fun `should forward data to channel and topic`() = runBlockingTest {
        // given
        val step = TopicMirrorStep<String, String>("", dataTransferTopic)
        val ctx = StepTestHelper.createStepContext<String, String>("This is a test").also { it.isTail = false }

        // when
        step.execute(ctx)
        val output = ctx.consumeOutputValue()
        assertEquals("This is a test", output)

        // then
        coVerifyOnce { dataTransferTopic.produceValue(eq("This is a test")) }
        assertFalse(ctx.isExhausted)
        assertFalse((ctx.output as Channel).isClosedForReceive)
    }

    @Test
    internal fun `should complete the topic`() = runBlockingTest {
        // given
        val step = TopicMirrorStep<String, String>("", dataTransferTopic)
        val ctx = DefaultCompletionContext(
            campaignKey = "my-campaign",
            scenarioName = "my-scenario",
            minionId = "my-minion",
            lastExecutedStepName = "step-1",
            errors = emptyList()
        )

        // when
        step.complete(ctx)

        // then
        coVerifyOnce { dataTransferTopic.complete() }
    }
}
