package io.qalipsis.core.factories.steps

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.test.steps.StepTestHelper
import io.qalipsis.test.time.QalipsisTimeAssertions.assertLongerOrEqualTo
import io.qalipsis.test.time.QalipsisTimeAssertions.assertShorterThan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.time.Duration

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
internal class PaceStepTest {

    @Test
    @Timeout(20)
    internal fun addIsolatedPaceForDifferentMinions() {
        // Short warm-up to obtain finer precision.
        val warmUpStep = PaceStep<Long>("") { (it + 1).coerceAtMost(1) }
        runBlocking {
            val ctx = StepTestHelper.createStepContext<Long, Long>(input = 1L)
            warmUpStep.execute(ctx)
            (ctx.output as Channel<Any?>).receive()
        }

        // Period increases of 100 seconds until 600 ms is reached.
        val paceStep = 100L
        val paceMax = 600L
        val step = PaceStep<Long>("") { (it + paceStep).coerceAtMost(paceMax) }
        val errorMargin = Duration.ofMillis(20L)
        var pastExecutionEnd: Long? = null

        runBlocking {
            // Check that values are individual for each minion.
            repeat(2) { minionIndex ->
                repeat(10) { rep ->
                    logger().debug("Executing loop $rep of minion $minionIndex")
                    val ctx = StepTestHelper.createStepContext<Long, Long>(input = 1L, minionId = "minion-$minionIndex")
                    val start = pastExecutionEnd ?: System.nanoTime()
                    step.execute(ctx)
                    // Concat to the milliseconds, because nano precision raises assertion errors.
                    val end = System.nanoTime()
                    pastExecutionEnd = end
                    val executionDuration = Duration.ofMillis((end - start) / 1_000_000)
                    val expectedDuration = Duration.ofMillis(((rep.toLong() + 1) * paceStep).coerceAtMost(paceMax))

                    assertLongerOrEqualTo(expectedDuration.minus(errorMargin), executionDuration)
                    assertShorterThan(expectedDuration.plus(errorMargin), executionDuration)

                    Assertions.assertEquals(1L, (ctx.output as Channel<Any?>).receive())
                }
            }
        }
    }
}