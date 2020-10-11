package io.qalipsis.api.steps

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.mockk.every
import io.qalipsis.api.context.CorrelationRecord
import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
internal class LeftJoinStepSpecificationTest {

    @Test
    internal fun `should add left join step with defined secondary step with a name as next and register it`() {
        val previousStep = DummyStepSpecification()
        val primaryKeyExtractor: (CorrelationRecord<Int>) -> Any? = { it.value * 2 }
        val secondaryStep: AbstractStepSpecification<*, Long, *> = relaxedMockk {
            every { name } returns "my-other-step"
        }
        val specification: (ScenarioSpecification) -> AbstractStepSpecification<*, Long, *> = { secondaryStep }
        val secondaryKeyExtractor: (CorrelationRecord<Long>) -> Any? = { it.value.toInt() * 3 }
        previousStep.leftJoin(using = primaryKeyExtractor, on = specification, having = secondaryKeyExtractor)
            .configure {
                cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
            }

        val expected = LeftJoinStepSpecification<Unit, Pair<Int, Long>>(
                primaryKeyExtractor as (CorrelationRecord<out Any?>) -> Any?,
                secondaryKeyExtractor as (CorrelationRecord<out Any?>) -> Any?, "my-other-step")
        expected.cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
        assertEquals(expected, previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add left join step with defined secondary step and generated name as next and register it`() {
        val previousStep = DummyStepSpecification()
        val primaryKeyExtractor: (CorrelationRecord<Int>) -> Any? = { it.value * 2 }
        val secondaryStep = DummyStepSpecification()
        val specification: (ScenarioSpecification) -> AbstractStepSpecification<*, Int, *> = { secondaryStep }
        val secondaryKeyExtractor: (CorrelationRecord<Int>) -> Any? = { it.value * 3 }
        previousStep.leftJoin(using = primaryKeyExtractor, on = specification, having = secondaryKeyExtractor)
            .configure {
                cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
            }

        val nextStep = previousStep.nextSteps[0]
        assertThat(nextStep).isInstanceOf(LeftJoinStepSpecification::class).all {
            prop(LeftJoinStepSpecification<*, *>::primaryKeyExtractor).isEqualTo(primaryKeyExtractor)
            prop(LeftJoinStepSpecification<*, *>::secondaryKeyExtractor).isEqualTo(secondaryKeyExtractor)
            prop(LeftJoinStepSpecification<*, *>::cacheTimeout).isEqualTo(Duration.ofMillis(Long.MAX_VALUE))
            prop(LeftJoinStepSpecification<*, *>::secondaryStepName).isNotNull()
        }
    }

    @Test
    internal fun `should add left join step with name of secondary step as next`() {
        val previousStep = DummyStepSpecification()
        val primaryKeyExtractor: (CorrelationRecord<Int>) -> Any? = { it.value * 2 }
        val secondaryKeyExtractor: (CorrelationRecord<Long>) -> Any? = { it.value.toInt() * 3 }
        previousStep.leftJoin(using = primaryKeyExtractor, on = "my-other-step", having = secondaryKeyExtractor)
            .configure {
                cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
            }

        val expected = LeftJoinStepSpecification<Unit, Pair<Int, Long>>(
                primaryKeyExtractor as (CorrelationRecord<out Any?>) -> Any?,
                secondaryKeyExtractor as (CorrelationRecord<out Any?>) -> Any?, "my-other-step")
        expected.cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
        assertEquals(expected, previousStep.nextSteps[0])
    }
}