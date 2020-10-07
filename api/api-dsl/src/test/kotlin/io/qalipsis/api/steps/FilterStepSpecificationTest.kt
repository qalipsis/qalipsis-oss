package io.qalipsis.api.steps

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class FilterStepSpecificationTest {

    @Test
    internal fun `should add filter step as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (input: Int) -> Boolean = { _ -> true }
        previousStep.filter(specification)

        assertEquals(FilterStepSpecification(specification), previousStep.nextSteps[0])
    }

}