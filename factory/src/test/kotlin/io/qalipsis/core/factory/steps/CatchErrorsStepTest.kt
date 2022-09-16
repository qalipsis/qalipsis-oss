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

import io.qalipsis.api.context.StepError
import io.qalipsis.test.steps.StepTestHelper.createStepContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicInteger

/**
 *
 * @author Eric Jessé
 */
internal class CatchErrorsStepTest {

    @Test
    @Timeout(3)
    fun `should ignore block when no error`() = runBlockingTest {
        val reference = AtomicInteger(0)
        val step = CatchErrorsStep<Int>("") { _ -> reference.incrementAndGet() }
        val ctx = createStepContext<Int, Int>(input = 123)

        step.execute(ctx)
        val result = ctx.consumeOutputValue()

        Assertions.assertEquals(0, reference.get())
        Assertions.assertEquals(123, result)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }

    @Test
    @Timeout(3)
    fun `should execute block when errors`() = runBlockingTest {
        val reference = AtomicInteger(0)
        val step = CatchErrorsStep<Int>("") { reference.incrementAndGet() }
        val ctx = createStepContext<Int, Int>(input = 456, errors = mutableListOf(StepError(RuntimeException(""))))

        step.execute(ctx)
        val result = ctx.consumeOutputValue()

        Assertions.assertEquals(1, reference.get())
        Assertions.assertEquals(456, result)
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }

}
