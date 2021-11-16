package io.qalipsis.api.steps

import io.qalipsis.api.context.StepError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class ValidationStepSpecificationTest {

    @Test
    internal fun `should add validation step as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (input: Int) -> List<StepError> = { _ -> emptyList() }
        previousStep.validate(specification)

        assertEquals(ValidationStepSpecification(specification), previousStep.nextSteps[0])
    }

}