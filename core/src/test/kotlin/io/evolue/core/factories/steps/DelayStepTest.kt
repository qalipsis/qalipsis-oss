package io.evolue.core.factories.steps

import io.evolue.api.lang.durationSince
import io.evolue.test.steps.StepTestHelper
import io.evolue.test.time.EvolueTimeAssertions
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
    fun shouldExecuteTheDecoratedStepAfterTheDelay() {
        val delay = 20L
        val step = DelayStep<Int>("", Duration.ofMillis(delay))
        val ctx = StepTestHelper.createStepContext<Int, Int>(123)

        val start = Instant.now()
        runBlocking {
            step.execute(ctx)

            Assertions.assertEquals(123, (ctx.output as Channel).receive())
        }
        EvolueTimeAssertions.assertLongerOrEqualTo(Duration.ofMillis(delay), start.durationSince())
        Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
    }
}
