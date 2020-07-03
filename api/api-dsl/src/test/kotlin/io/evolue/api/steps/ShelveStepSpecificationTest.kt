package io.evolue.api.steps

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class ShelveStepSpecificationTest {

    @Test
    internal fun `should add shelve step as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (input: Int) -> Map<String, Any?> = { mapOf("value-1" to it + 1) }
        previousStep.shelve(specification)

        assertEquals(ShelveStepSpecification(specification), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add shelve step with unique name as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.shelve("value-1")

        assertTrue(previousStep.nextSteps[0] is ShelveStepSpecification)
    }
}
