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

import io.qalipsis.api.exceptions.InvalidSpecificationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

/**
 * @author Eric Jessé
 */
internal class DelayStepSpecificationTest {

    @Test
    internal fun `should add delay decorator with duration as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.delay(Duration.ofMillis(123))

        assertEquals(DelayStepSpecification<Int>(Duration.ofMillis(123)), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add delay decorator with milliseconds as next`() {
        val previousStep = DummyStepSpecification()
        previousStep.delay(123)

        assertEquals(DelayStepSpecification<Int>(Duration.ofMillis(123)), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should generate error when the duration is zero`() {
        val previousStep = DummyStepSpecification()

        assertThrows<InvalidSpecificationException> {
            previousStep.delay(Duration.ZERO)
        }
    }

    @Test
    internal fun `should generate error when the duration is negative`() {
        val previousStep = DummyStepSpecification()

        assertThrows<InvalidSpecificationException> {
            previousStep.delay(Duration.ofMillis(-1))
        }
    }

    @Test
    internal fun `should generate error when the milliseconds are zero`() {
        val previousStep = DummyStepSpecification()

        assertThrows<InvalidSpecificationException> {
            previousStep.delay(0)
        }
    }

    @Test
    internal fun `should generate error when the milliseconds are negative`() {
        val previousStep = DummyStepSpecification()

        assertThrows<InvalidSpecificationException> {
            previousStep.delay(-1)
        }
    }

}