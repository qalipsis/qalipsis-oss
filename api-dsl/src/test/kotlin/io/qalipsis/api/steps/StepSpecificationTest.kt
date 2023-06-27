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
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.mockk.every
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class StepSpecificationTest {

    @Test
    internal fun `should add the steps as next`() {
        val previousStep = DummyStepSpecification()
        val nextStep1 = DummyStepSpecification()
        val nextStep2 = DummyStepSpecification()
        previousStep.add(nextStep1)
        previousStep.add(nextStep2)

        assertEquals(2, previousStep.nextSteps.size)
        assertEquals(nextStep1, previousStep.nextSteps[0])
        assertEquals(nextStep2, previousStep.nextSteps[1])
    }

    @Test
    internal fun `should add the steps as next at once`() {
        val previousStep = DummyStepSpecification().apply {
            name = "root"
        }
        previousStep.directedAcyclicGraphName = "root-dag"
        previousStep.split {
            dummy("step-1").add(relaxedMockk {
                every { name } returns "last-step"
            })

            dummy("step-2")
        }
            .dummy("step-3")

        assertThat(previousStep.nextSteps).all {
            hasSize(3)
            index(0).isInstanceOf<DummyStepSpecification>().all {
                prop(StepSpecification<*, *, *>::name).isEqualTo("step-1")
                prop(StepSpecification<*, *, *>::directedAcyclicGraphName).isEqualTo("dag-1")
                prop(StepSpecification<*, *, *>::nextSteps).all {
                    hasSize(1)
                    index(0).all {
                        prop(StepSpecification<*, *, *>::name).isEqualTo("last-step")
                        prop(StepSpecification<*, *, *>::directedAcyclicGraphName).isEmpty()
                    }
                }
            }
            index(1).isInstanceOf<DummyStepSpecification>().all {
                prop(StepSpecification<*, *, *>::name).isEqualTo("step-2")
                prop(StepSpecification<*, *, *>::directedAcyclicGraphName).isEqualTo("dag-2")
            }
            index(2).isInstanceOf<DummyStepSpecification>().all {
                prop(StepSpecification<*, *, *>::name).isEqualTo("step-3")
                prop(StepSpecification<*, *, *>::directedAcyclicGraphName).isEqualTo("root-dag")
            }
        }

    }
}
