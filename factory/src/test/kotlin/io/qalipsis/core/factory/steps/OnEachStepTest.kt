package io.qalipsis.core.factory.steps

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEqualTo
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
internal class OnEachStepTest {

    @Test
    @Timeout(1)
    fun `should execute statement`() = runBlockingTest {
        val collected = mutableListOf<Int>()
        val step = OnEachStep<Int>("", null) { collected.add(it) }
        val ctx = StepTestHelper.createStepContext<Int, Int>(input = 123)

        step.execute(ctx)
        val output = (ctx.output as Channel).receive()
        assertEquals(output, 123)
        assertThat(collected).all {
            hasSize(1)
            index(0).isEqualTo(123)
        }
        assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}
