package io.qalipsis.core.factory.context

import io.qalipsis.api.context.StepContext
import io.qalipsis.test.coroutines.TestDispatcherProvider
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

internal class StepContextImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    internal fun `should confirm no input when none was added`() {
        // given
        val context = StepContextImpl<String, Int>(minionId = "", scenarioName = "", stepName = "")

        // when+then
        assertFalse(context.hasInput)
    }

    @Test
    internal fun `should confirm input existence when one was added`() {
        // given
        val context = StepContextImpl<String, Int>(
            input = Channel<String>(1).also { it.trySend("Test") },
            minionId = "",
            scenarioName = "",
            stepName = ""
        )

        // when+then
        assertTrue(context.hasInput)
    }

    @Test
    @Timeout(2)
    internal fun `should confirm output existence only when at least one is added, event when consumed`() =
        testDispatcherProvider.runTest {
            // given
            val output = Channel<StepContext.StepOutputRecord<Int>>(1)
            val context = StepContextImpl<String, Int>(output = output, minionId = "", scenarioName = "", stepName = "")

            // when+then
            assertFalse(context.generatedOutput)

            // when
            context.send(123)

            // then
            assertTrue(context.generatedOutput)

            //when
            output.receive()

            // then
            assertTrue(context.generatedOutput)
        }

}
