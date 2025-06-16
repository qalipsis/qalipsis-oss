/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

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
        previousStep.innerJoin().using(primaryKeyExtractor).on(specification).having(secondaryKeyExtractor)
            .configure {
                cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
            }

        val expected = InnerJoinStepSpecification<Unit, Pair<Int, Long>>(
            primaryKeyExtractor as (CorrelationRecord<out Any?>) -> Any?,
            secondaryKeyExtractor as (CorrelationRecord<out Any?>) -> Any?, "my-other-step"
        )
        expected.cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
        assertEquals(expected, previousStep.nextSteps[0])
        assertThat(previousStep.scenario.exists("my-other-step")).isTrue()
    }

    @Test
    internal fun `should add left join step with defined secondary step and generated name as next and register it`() {
        val previousStep = DummyStepSpecification()
        val primaryKeyExtractor: (CorrelationRecord<Int>) -> Any? = { it.value * 2 }
        val specification: (ScenarioSpecification) -> StepSpecification<Unit, Int, *> = {
            it.returns(123)
        }
        val secondaryKeyExtractor: (CorrelationRecord<Int>) -> Any? = { it.value * 3 }
        previousStep.innerJoin().using(primaryKeyExtractor).on(specification).having(secondaryKeyExtractor)
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
        assertThat(
            previousStep.scenario.exists(
                (nextStep as InnerJoinStepSpecification<*, *>).secondaryStepName
            )
        ).isTrue()
    }

    @Test
    internal fun `should add left join step with name of secondary step as next`() {
        val previousStep = DummyStepSpecification()
        val primaryKeyExtractor: (CorrelationRecord<Int>) -> Any? = { it.value * 2 }
        val secondaryKeyExtractor: (CorrelationRecord<Long>) -> Any? = { it.value.toInt() * 3 }
        previousStep.innerJoin().using(primaryKeyExtractor).on<Long>("my-other-step")
            .having(secondaryKeyExtractor)
            .configure {
                cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
            }

        val expected = InnerJoinStepSpecification<Unit, Pair<Int, Long>>(
            primaryKeyExtractor as (CorrelationRecord<out Any?>) -> Any?,
            secondaryKeyExtractor as (CorrelationRecord<out Any?>) -> Any?, "my-other-step"
        )
        expected.cacheTimeout = Duration.ofMillis(Long.MAX_VALUE)
        assertEquals(expected, previousStep.nextSteps[0])
    }
}
