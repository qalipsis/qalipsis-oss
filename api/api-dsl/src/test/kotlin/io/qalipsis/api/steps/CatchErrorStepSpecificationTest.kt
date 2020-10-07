package io.qalipsis.api.steps

import io.qalipsis.api.context.StepError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class CatchErrorStepSpecificationTest {

    @Test
    internal fun `should add error catcher as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (error: Collection<StepError>) -> Unit = { _ -> }
        previousStep.catchError(specification)

        assertEquals(CatchErrorStepSpecification<Int>(specification), previousStep.nextSteps[0])
    }

}