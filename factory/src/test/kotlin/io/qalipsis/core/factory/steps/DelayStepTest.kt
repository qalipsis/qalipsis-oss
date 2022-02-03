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
