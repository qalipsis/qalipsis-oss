package io.qalipsis.api.steps

import io.qalipsis.api.context.StepContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class MapWithContextStepSpecificationTest {

    @Test
    internal fun `should add mapWithContext step as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (context: StepContext<Int, String>, input: Int) -> String = { _, value -> value.toString() }
        previousStep.mapWithContext(specification)

        assertEquals(MapWithContextStepSpecification(specification), previousStep.nextSteps[0])
    }
}