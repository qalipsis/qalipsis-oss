package io.evolue.api.steps

import io.evolue.api.exceptions.InvalidSpecificationException
import io.evolue.test.mockk.relaxedMockk
import io.evolue.test.mockk.verifyOnce
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class DelayDecoratorSpecificationTest {

    @Test
    internal fun `should add delay decorator with duration as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.delay(Duration.ofMillis(123))

        assertEquals(DelayDecoratorSpecification<Int>(Duration.ofMillis(123)), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add delay decorator with milliseconds as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.delay(123)

        assertEquals(DelayDecoratorSpecification<Int>(Duration.ofMillis(123)), previousStep.nextSteps[0])
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

    @Test
    internal fun `should add next step as decorator`() {
        val delaySpec = DelayDecoratorSpecification<Int>(Duration.ofMillis(123))
        delaySpec.scenario = relaxedMockk { }
        val decorated = DummyStepSpecification()

        delaySpec.add(decorated)

        verifyOnce { delaySpec.scenario!!.register(decorated) }
        assertTrue(delaySpec.nextSteps.isEmpty())
        assertSame(decorated, delaySpec.decoratedStep)
    }

    @Test
    internal fun `should allow only one step`() {
        val delaySpec = DelayDecoratorSpecification<Int>(Duration.ofMillis(123))
        delaySpec.scenario = relaxedMockk { }
        val decorated = DummyStepSpecification()

        delaySpec.add(decorated)
        assertThrows<InvalidSpecificationException> {
            delaySpec.add(decorated)
        }

        verifyOnce { delaySpec.scenario!!.register(decorated) }
        assertTrue(delaySpec.nextSteps.isEmpty())
        assertSame(decorated, delaySpec.decoratedStep)
    }
}