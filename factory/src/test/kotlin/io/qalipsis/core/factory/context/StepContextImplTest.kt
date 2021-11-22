package io.qalipsis.core.factory.context

import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class StepContextImplTest {

    @Test
    internal fun `should return empty when there is no input`() {
        val context =
            StepContextImpl<String, Int>(minionId = "", scenarioId = "", directedAcyclicGraphId = "", stepId = "")

        assertFalse(context.hasInput)
    }

    @Test
    internal fun `should return not empty when there is an input`() {
        val channel = Channel<String>(1).also { it.trySend("Test").isSuccess }
        val context = StepContextImpl<String, Int>(
            input = channel,
            minionId = "",
            scenarioId = "",
            directedAcyclicGraphId = "",
            stepId = ""
        )

        assertTrue(context.hasInput)
    }
}
