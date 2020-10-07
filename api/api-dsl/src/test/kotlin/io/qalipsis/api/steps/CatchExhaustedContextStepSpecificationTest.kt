package io.qalipsis.api.steps

import io.qalipsis.api.context.StepContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class CatchExhaustedContextStepSpecificationTest {

    @Test
    internal fun `should add exhausted context catcher as next`() {
        val previousStep = DummyStepSpecification()
        val specification: suspend (context: StepContext<Int, String>) -> Unit = { _ -> }
        previousStep.catchExhaustedContext(specification)

        assertEquals(CatchExhaustedContextStepSpecification(specification), previousStep.nextSteps[0])
    }
}