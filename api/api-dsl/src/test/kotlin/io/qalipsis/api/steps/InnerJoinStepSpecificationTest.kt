package io.qalipsis.api.steps

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.api.context.CorrelationRecord
import io.qalipsis.api.scenario.ScenarioSpecification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
internal class InnerJoinStepSpecificationTest {

    @Test
    internal fun `should add left join step with defined secondary step with a name as next and register it`() {
        val previousStep = DummyStepSpecification()
        val primaryKeyExtractor: (CorrelationRecord<Int>) -> Any? = { it.value * 2 }
        val specification: (ScenarioSpecification) -> StepSpecification<Unit, Long, *> = {
            it.returns(123L).configure {
                name = "my-other-step"
            }
        }
        val secondaryKeyExtractor: (CorrelationRecord<Long>) -> Any? = { it.value.toInt() * 3 }
        previousStep.innerJoin(using = primaryKeyExtractor, on = specification, having = secondaryKeyExtractor)
            .configure {
                cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
            }

        val expected = InnerJoinStepSpecification<Unit, Pair<Int, Long>>(
                primaryKeyExtractor as (CorrelationRecord<out Any?>) -> Any?,
                secondaryKeyExtractor as (CorrelationRecord<out Any?>) -> Any?, "my-other-step")
        expected.cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
        assertEquals(expected, previousStep.nextSteps[0])
        assertThat(previousStep.scenario!!.exists("my-other-step")).isTrue()
    }

    @Test
    internal fun `should add left join step with defined secondary step and generated name as next and register it`() {
        val previousStep = DummyStepSpecification()
        val primaryKeyExtractor: (CorrelationRecord<Int>) -> Any? = { it.value * 2 }
        val specification: (ScenarioSpecification) -> StepSpecification<Unit, Int, *> = {
            it.returns(123)
        }
        val secondaryKeyExtractor: (CorrelationRecord<Int>) -> Any? = { it.value * 3 }
        previousStep.innerJoin(using = primaryKeyExtractor, on = specification, having = secondaryKeyExtractor)
            .configure {
                cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
            }

        val nextStep = previousStep.nextSteps[0]
        assertThat(nextStep).isInstanceOf(InnerJoinStepSpecification::class).all {
            prop(InnerJoinStepSpecification<*, *>::primaryKeyExtractor).isEqualTo(primaryKeyExtractor)
            prop(InnerJoinStepSpecification<*, *>::secondaryKeyExtractor).isEqualTo(secondaryKeyExtractor)
            prop(InnerJoinStepSpecification<*, *>::cacheTimeout).isEqualTo(Duration.ofMillis(Long.MAX_VALUE))
            prop(InnerJoinStepSpecification<*, *>::secondaryStepName).isNotNull()
        }
        assertThat(previousStep.scenario!!.exists(
                (nextStep as InnerJoinStepSpecification<*, *>).secondaryStepName)).isTrue()
    }

    @Test
    internal fun `should add left join step with name of secondary step as next`() {
        val previousStep = DummyStepSpecification()
        val primaryKeyExtractor: (CorrelationRecord<Int>) -> Any? = { it.value * 2 }
        val secondaryKeyExtractor: (CorrelationRecord<Long>) -> Any? = { it.value.toInt() * 3 }
        previousStep.innerJoin(using = primaryKeyExtractor, on = "my-other-step", having = secondaryKeyExtractor)
            .configure {
                cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
            }

        val expected = InnerJoinStepSpecification<Unit, Pair<Int, Long>>(
                primaryKeyExtractor as (CorrelationRecord<out Any?>) -> Any?,
                secondaryKeyExtractor as (CorrelationRecord<out Any?>) -> Any?, "my-other-step")
        expected.cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
        assertEquals(expected, previousStep.nextSteps[0])
    }
}
