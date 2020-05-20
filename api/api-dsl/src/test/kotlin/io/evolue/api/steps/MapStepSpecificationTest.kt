package io.evolue.api.steps

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class MapStepSpecificationTest {

    @Test
    internal fun `should add map step as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (input: Int) -> String = { value -> value.toString() }
        previousStep.map(specification)

        assertEquals(MapStepSpecification(specification), previousStep.nextSteps[0])
    }
}