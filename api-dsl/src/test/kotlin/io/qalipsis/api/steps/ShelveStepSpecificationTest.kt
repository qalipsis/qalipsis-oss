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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class ShelveStepSpecificationTest {

    @Test
    internal fun `should add shelve step as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (input: Int) -> Map<String, Any?> = { mapOf("value-1" to it + 1) }
        previousStep.shelve(specification)

        assertEquals(ShelveStepSpecification(specification), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add shelve step with unique name as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.shelve("value-1")

        assertTrue(previousStep.nextSteps[0] is ShelveStepSpecification)
    }

    @Test
    internal fun `should add shelve step with unique name and specification as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.shelve("value-1") { input -> input.toString() }

        assertTrue(previousStep.nextSteps[0] is ShelveStepSpecification)
    }
}
