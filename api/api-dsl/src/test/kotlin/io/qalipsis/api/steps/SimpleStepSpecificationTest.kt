package io.qalipsis.api.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.scenario
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class SimpleStepSpecificationTest {

    @Test
    internal fun `should add simple step as next`() {
        val previousStep = DummyStepSpecification()
        val specification: suspend (context: StepContext<Int, String>) -> Unit = { _ -> }
        previousStep.execute(specification)

        assertEquals(SimpleStepSpecification(specification), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add simple step to scenario`() {
        val scenario = scenario(
            "my-scenario") as StepSpecificationRegistry
        val specification: suspend (context: StepContext<Unit, String>) -> Unit = { _ -> }
        scenario.execute(specification)

        assertEquals(SimpleStepSpecification(specification), scenario.rootSteps[0])
    }

}
