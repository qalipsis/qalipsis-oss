package io.qalipsis.api.steps

import assertk.assertThat
import assertk.assertions.isInstanceOf
import io.qalipsis.api.context.StepContext
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class CatchExhaustedContextStepSpecificationTest {

    @Test
    internal fun `should add exhausted context catcher as next`() {
        val previousStep = DummyStepSpecification()
        val specification: suspend (context: StepContext<*, String>) -> Unit = { _ -> }
        previousStep.catchExhaustedContext(specification)

        assertEquals(CatchExhaustedContextStepSpecification(specification), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add recover step as next`() = runBlockingTest {
        val previousStep = DummyStepSpecification()
        previousStep.recover()

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CatchExhaustedContextStepSpecification::class)

        @Suppress("UNCHECKED_CAST") val stepSpecification =
            (previousStep.nextSteps[0] as CatchExhaustedContextStepSpecification).block as (suspend (context: StepContext<*, Unit>) -> Unit)
        val stepContext = StepTestHelper.createStepContext<Any?, Unit>(
            minionId = "",
            scenarioId = "",
            directedAcyclicGraphId = "",
            stepId = "",
            isExhausted = true
        )
        stepSpecification(stepContext as StepContext<*, Unit>)

        assertFalse(stepContext.isExhausted)
    }
}
