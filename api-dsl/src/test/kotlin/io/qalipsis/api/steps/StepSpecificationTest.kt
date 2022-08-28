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

import io.mockk.every
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
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
        val previousStep = DummyStepSpecification()
        previousStep.split {
            dummy().add(relaxedMockk {
                every { name } returns "last-step"
            })

            dummy()
        }

        assertEquals(2, previousStep.nextSteps.size)
        assertTrue(previousStep.nextSteps[0] is DummyStepSpecification)
        assertTrue(previousStep.nextSteps[0] is DummyStepSpecification)

        assertNotSame(previousStep, previousStep.nextSteps[0])
        assertNotSame(previousStep, previousStep.nextSteps[1])
        assertNotSame(previousStep.nextSteps[0], previousStep.nextSteps[1])

        assertEquals("last-step", previousStep.nextSteps[0].nextSteps[0].name)
    }
}
