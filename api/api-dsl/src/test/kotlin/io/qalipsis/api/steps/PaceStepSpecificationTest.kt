package io.qalipsis.api.steps

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class PaceStepSpecificationTest {

    @Test
    internal fun `should add pace step as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (pastPeriodMs: Long) -> Long = { _ -> 10 }
        previousStep.pace(specification)

        assertEquals(PaceStepSpecification<Int>(specification), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add accelerating pace step as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.acceleratingPace(20, 2.0, 4)

        assertTrue(previousStep.nextSteps[0] is PaceStepSpecification)
        val paceStepSpecification = (previousStep.nextSteps[0] as PaceStepSpecification).specification

        // At start it should be 20.
        assertEquals(20, paceStepSpecification(0))
        // Each next value should be half of the previous one.
        assertEquals(5, paceStepSpecification(10))
        // The smallest value should be 4.
        assertEquals(4, paceStepSpecification(1))
    }

    @Test
    internal fun `should add constant pace step as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.constantPace(20)

        assertTrue(previousStep.nextSteps[0] is PaceStepSpecification)
        val paceStepSpecification = (previousStep.nextSteps[0] as PaceStepSpecification).specification

        assertEquals(20, paceStepSpecification(0))
        assertEquals(20, paceStepSpecification(10))
        assertEquals(20, paceStepSpecification(1))
    }
}