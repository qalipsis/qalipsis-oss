package io.qalipsis.api.steps

import io.qalipsis.api.exceptions.InvalidSpecificationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class DelayStepSpecificationTest {

    @Test
    internal fun `should add delay decorator with duration as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.delay(Duration.ofMillis(123))

        assertEquals(DelayStepSpecification<Int>(Duration.ofMillis(123)), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add delay decorator with milliseconds as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.delay(123)

        assertEquals(DelayStepSpecification<Int>(Duration.ofMillis(123)), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should generate error when the duration is zero`() {
        val previousStep = DummyStepSpecification()

        assertThrows<InvalidSpecificationException> {
            previousStep.delay(Duration.ZERO)
        }
    }

    @Test
    internal fun `should generate error when the duration is negative`() {
        val previousStep = DummyStepSpecification()

        assertThrows<InvalidSpecificationException> {
            previousStep.delay(Duration.ofMillis(-1))
        }
    }

    @Test
    internal fun `should generate error when the milliseconds are zero`() {
        val previousStep = DummyStepSpecification()

        assertThrows<InvalidSpecificationException> {
            previousStep.delay(0)
        }
    }

    @Test
    internal fun `should generate error when the milliseconds are negative`() {
        val previousStep = DummyStepSpecification()

        assertThrows<InvalidSpecificationException> {
            previousStep.delay(-1)
        }
    }

}