package io.qalipsis.core.factories.steps

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
@Suppress("EXPERIMENTAL_API_USAGE")
internal class DelayStepTest {

    @Test
    @Timeout(3)
    fun shouldExecuteTheDecoratedStepAfterTheDelay() {
        val delay = 20L
        val step = DelayStep<Int>("", Duration.ofMillis(delay))
        val ctx = StepTestHelper.createStepContext<Int, Int>(123)

        val start = Instant.now()
        runBlocking {
            step.execute(ctx)

            Assertions.assertEquals(123, (ctx.output as Channel).receive())
        }
        QalipsisTimeAssertions.assertLongerOrEqualTo(Duration.ofMillis(delay), start.durationSince())
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}
