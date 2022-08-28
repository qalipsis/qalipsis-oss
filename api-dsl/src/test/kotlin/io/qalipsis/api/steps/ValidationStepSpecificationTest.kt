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

import io.qalipsis.api.context.StepError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class ValidationStepSpecificationTest {

    @Test
    internal fun `should add validation step as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (input: Int) -> List<StepError> = { _ -> emptyList() }
        previousStep.validate(specification)

        assertEquals(ValidationStepSpecification(specification), previousStep.nextSteps[0])
    }

}