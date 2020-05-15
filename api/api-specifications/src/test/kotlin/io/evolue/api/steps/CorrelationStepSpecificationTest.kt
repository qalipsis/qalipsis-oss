package io.evolue.api.steps

import io.evolue.api.ScenarioSpecification
import io.evolue.api.context.CorrelationRecord
import io.evolue.test.mockk.relaxedMockk
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class CorrelationStepSpecificationTest {

    @Test
    internal fun `should add correlation step with defined secondary step with a name as next and register it`() {
        val previousStep = DummyStepSpecification()
        val primaryKeyExtractor: CorrelationRecord<Int>.() -> Any? = { this.value * 2 }
        val secondaryStep: StepSpecification<*, Long, *> = relaxedMockk {
            every { name } returns "my-other-step"
        }
        val specification: ScenarioSpecification.() -> StepSpecification<*, Long, *> = { secondaryStep }
        val secondaryKeyExtractor: CorrelationRecord<Long>.() -> Any? = { this.value.toInt() * 3 }
        previousStep.correlate(on = primaryKeyExtractor, with = specification, having = secondaryKeyExtractor)
            .configure {
                cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
            }

        val expected = CorrelationStepSpecification<Unit, Pair<Int, Long>>(
            primaryKeyExtractor as CorrelationRecord<out Any?>.() -> Any?,
            secondaryKeyExtractor as CorrelationRecord<out Any?>.() -> Any?, "my-other-step")
        expected.cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
        assertEquals(expected, previousStep.nextSteps[0])

        assertSame(secondaryStep, previousStep.scenario!!.find<Long>("my-other-step"))
    }

    @Test
    internal fun `should add correlation step with defined secondary step and generated name as next and register it`() {
        val previousStep = DummyStepSpecification()
        val primaryKeyExtractor: CorrelationRecord<Int>.() -> Any? = { this.value * 2 }
        val secondaryStep = DummyStepSpecification()
        val specification: ScenarioSpecification.() -> StepSpecification<*, Int, *> = { secondaryStep }
        val secondaryKeyExtractor: CorrelationRecord<Int>.() -> Any? = { this.value * 3 }
        previousStep.correlate(on = primaryKeyExtractor, with = specification, having = secondaryKeyExtractor)
            .configure {
                cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
            }

        (previousStep.nextSteps[0] as CorrelationStepSpecification).apply {
            assertEquals(primaryKeyExtractor, this.primaryKeyExtractor)
            assertEquals(secondaryKeyExtractor, this.secondaryKeyExtractor)
            assertTrue(this.secondaryStepName.isNotBlank())
            assertEquals(Duration.ofMillis(Long.MAX_VALUE), this.cacheTimeout)

            assertSame(secondaryStep, previousStep.scenario!!.find<Long>(this.secondaryStepName))
        }
    }

    @Test
    internal fun `should add correlation step with name of secondary step as next`() {
        val previousStep = DummyStepSpecification()
        val primaryKeyExtractor: CorrelationRecord<Int>.() -> Any? = { this.value * 2 }
        val secondaryKeyExtractor: CorrelationRecord<Long>.() -> Any? = { this.value.toInt() * 3 }
        previousStep.correlate(on = primaryKeyExtractor, with = "my-other-step", having = secondaryKeyExtractor)
            .configure {
                cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
            }

        val expected = CorrelationStepSpecification<Unit, Pair<Int, Long>>(
            primaryKeyExtractor as CorrelationRecord<out Any?>.() -> Any?,
            secondaryKeyExtractor as CorrelationRecord<out Any?>.() -> Any?, "my-other-step")
        expected.cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
        assertEquals(expected, previousStep.nextSteps[0])
    }
}