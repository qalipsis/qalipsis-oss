package io.qalipsis.core.factories.steps

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.test.steps.StepTestHelper
import io.qalipsis.test.time.QalipsisTimeAssertions.assertLongerOrEqualTo
import io.qalipsis.test.time.QalipsisTimeAssertions.assertShorterOrEqualTo
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
    internal fun addIsolatedPaceForDifferentMinions() = runBlocking {
        // Short warm-up to obtain finer precision.
        val warmUpStep = PaceStep<Long>("") { 0 }
        val ctx = StepTestHelper.createStepContext<Long, Long>(input = 1L)
        warmUpStep.execute(ctx)
        (ctx.output as Channel<Any?>).receive()

        // Period increases of 100 seconds until 600 ms is reached.
        val paceStep = 200L
        val paceMax = 1200L
        val step = PaceStep<Long>("") { (it + paceStep).coerceAtMost(paceMax) }
        val errorMargin = Duration.ofMillis(40L)
        var pastExecutionEnd: Long? = null

        // Check that values are individual for each minion.
        repeat(2) { minionIndex ->
            repeat(10) { rep ->
                log.debug("Executing loop $rep of minion $minionIndex")
                val context = StepTestHelper.createStepContext<Long, Long>(input = 1L, minionId = "minion-$minionIndex")
                val start = pastExecutionEnd ?: System.currentTimeMillis()
                step.execute(context)
                val end = System.currentTimeMillis()
                pastExecutionEnd = end
                val executionDuration = Duration.ofMillis(end - start)
                val expectedDuration = Duration.ofMillis(((rep.toLong() + 1) * paceStep).coerceAtMost(paceMax))

                assertLongerOrEqualTo(expectedDuration - errorMargin, executionDuration)
                assertShorterOrEqualTo(expectedDuration + errorMargin, executionDuration)
                Assertions.assertEquals(1L, (context.output as Channel<Any?>).receive())
            }
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
