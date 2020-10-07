package io.qalipsis.api.steps

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class UnshelveStepSpecificationTest {

    @Test
    internal fun `should add unshelve step as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.unshelve("value-1", "value-2", "value-3")

        assertEquals(
            UnshelveStepSpecification<Int, Pair<Int, Map<String, Any?>>>(listOf("value-1", "value-2", "value-3"),
                false, false),
            previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add unshelve step with deletion as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.unshelveAndDelete("value-1", "value-2", "value-3")

        assertEquals(
            UnshelveStepSpecification<Int, Pair<Int, Map<String, Any?>>>(listOf("value-1", "value-2", "value-3"),
                true, false),
            previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add unshelve step with unique name as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.unshelve<Int, Double>("value-1")

        assertEquals(UnshelveStepSpecification<Int, Pair<Int, Double>>(listOf("value-1"), true, true),
            previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add unshelve step with unique name and deletion as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.unshelve<Int, Double>("value-1", false)

        assertEquals(UnshelveStepSpecification<Int, Pair<Int, Double>>(listOf("value-1"), false, true),
            previousStep.nextSteps[0])
    }
}
