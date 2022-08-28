/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.api.steps

import assertk.all
import assertk.assertThat
import assertk.assertions.*
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
        previousStep.innerJoin(using = primaryKeyExtractor, on = "my-other-step", having = secondaryKeyExtractor)
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
