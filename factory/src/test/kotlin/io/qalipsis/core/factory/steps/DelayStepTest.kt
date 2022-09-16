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

import io.qalipsis.api.lang.durationSince
import io.qalipsis.test.steps.StepTestHelper
import io.qalipsis.test.time.QalipsisTimeAssertions
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration
import java.time.Instant

/**
 * @author Eric Jessé
 */
internal class DelayStepTest {

    @Test
    @Timeout(3)
    fun `should execute the decorated step after the delay`() = runBlocking {
        val delay = 20L
        val step = DelayStep<Int>("", Duration.ofMillis(delay))
        val ctx = StepTestHelper.createStepContext<Int, Int>(123)

        val start = Instant.now()
        step.execute(ctx)

        Assertions.assertEquals(123, ctx.consumeOutputValue())
        QalipsisTimeAssertions.assertLongerOrEqualTo(Duration.ofMillis(delay), start.durationSince())
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}
