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
import io.qalipsis.api.scenario.ScenarioSpecification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Polina Bril
 */
@Suppress("UNCHECKED_CAST")
internal class ZipStepSpecificationTest {

    @Test
    internal fun `should add zip step with defined secondary step with a name as next and register it`() {
        val previousStep = DummyStepSpecification()
        val specification: (ScenarioSpecification) -> StepSpecification<Unit, Long, *> = {
            it.returns(123L).configure {
                name = "my-other-step"
            }
        }
        previousStep.zip(on = specification)

        val expected = ZipStepSpecification<Unit, Pair<Int, Long>>("my-other-step")
        assertEquals(expected, previousStep.nextSteps[0])
        assertThat(previousStep.scenario.exists("my-other-step")).isTrue()
    }

    @Test
    internal fun `should add zip step with defined secondary step and generated name as next and register it`() {
        val previousStep = DummyStepSpecification()
        val specification: (ScenarioSpecification) -> StepSpecification<Unit, Int, *> = {
            it.returns(123)
        }
        previousStep.zip(on = specification)

        val nextStep = previousStep.nextSteps[0]
        assertThat(nextStep).isInstanceOf(ZipStepSpecification::class).all {
            prop(ZipStepSpecification<*, *>::secondaryStepName).isNotNull()
        }
        assertThat(
            previousStep.scenario.exists((nextStep as ZipStepSpecification<*, *>).secondaryStepName)
        ).isTrue()
    }
}
