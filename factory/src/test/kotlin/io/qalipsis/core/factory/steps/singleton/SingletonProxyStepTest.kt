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

package io.qalipsis.core.factory.steps.singleton

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.mockk
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.messaging.subscriptions.TopicSubscription
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * @author Eric Jessé
 */
internal class SingletonProxyStepTest {

    @Test
    @Timeout(5)
    internal fun `should use record from topic`() = runBlockingTest {
        val subscription = mockk<TopicSubscription<Long>> {
            coEvery { pollValue() } returns 123L
        }
        val ctx = StepTestHelper.createStepContext<Long, Long>()
        val topic = mockk<Topic<Long>>(relaxed = true) {
            coEvery { subscribe("${ctx.minionId}-${ctx.stepName}") } returns subscription
        }
        val step = SingletonProxyStep("", topic)

        // when
        step.execute(ctx)

        // then
        Assertions.assertEquals(123L, ctx.consumeOutputValue())
        coVerifyOrder {
            topic.subscribe("${ctx.minionId}-${ctx.stepName}")
            subscription.pollValue()
        }
        confirmVerified(topic, subscription)
    }
}
