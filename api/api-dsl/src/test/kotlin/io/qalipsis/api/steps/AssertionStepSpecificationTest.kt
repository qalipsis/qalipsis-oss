package io.qalipsis.api.steps

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class AssertionStepSpecificationTest {

    @Test
    internal fun `should add simple assert as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (suspend (input: Int) -> Unit) = { throw RuntimeException() }
        previousStep.assert(specification)

        assertTrue(previousStep.nextSteps[0] is AssertionStepSpecification)
    }

    @Test
    internal fun `should add mapped assert as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (suspend (input: Int) -> String) = { input: Int -> input.toString() }
        previousStep.assertAndMap(specification)

        assertEquals(AssertionStepSpecification(specification), previousStep.nextSteps[0])
    }

}