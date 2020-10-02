package io.evolue.api.steps

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 *
 * @author Eric Jessé
 */
internal class OnEachStepSpecificationTest {

    @Test
    internal fun `should add on each step as next`() {
        val previousStep = DummyStepSpecification()
        val statement: (input: Int) -> Unit = { println(it) }
        previousStep.onEach(statement)

        assertEquals(OnEachStepSpecification(statement), previousStep.nextSteps[0])
    }

}