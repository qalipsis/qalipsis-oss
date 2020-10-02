package io.evolue.api.steps

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class FlatMapStepSpecificationTest {

    @Test
    internal fun `should add flat map step as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (input: Int) -> Flow<Int> = { _ -> emptyFlow() }
        previousStep.flatMap(specification)

        assertEquals(FlatMapStepSpecification(specification), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add default flat map step as next`() {
        val previousStep = DummyStepSpecification().map { listOf(it) }
        previousStep.flatten()

        assertTrue(previousStep.nextSteps[0] is FlatMapStepSpecification)
    }
}