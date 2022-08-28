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

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

internal class CollectionStepSpecificationTest {

    @Test
    internal fun `should add collection step as next with timeout only`() {
        val previousStep = DummyStepSpecification()
        previousStep.collect(timeout = Duration.ofSeconds(1))

        assertThat(previousStep.nextSteps[0]).isDataClassEqualTo(
            CollectionStepSpecification<Int>(batchTimeout = Duration.ofSeconds(1), batchSize = 0)
        )
    }

    @Test
    internal fun `should add collection step as next with batch size only`() {
        val previousStep = DummyStepSpecification()
        previousStep.collect(batchSize = 1)

        assertThat(previousStep.nextSteps[0]).isDataClassEqualTo(
            CollectionStepSpecification<Int>(batchTimeout = null, batchSize = 1)
        )
    }

    @Test
    internal fun `should add collection step as next with batch size and timeout`() {
        val previousStep = DummyStepSpecification()
        previousStep.collect(Duration.ofSeconds(1), 1)

        assertThat(previousStep.nextSteps[0]).isDataClassEqualTo(
            CollectionStepSpecification<Int>(Duration.ofSeconds(1), 1)
        )
    }

    @Test
    internal fun `should not add collection step as next without timeout and positive batch size`() {
        val previousStep = DummyStepSpecification()

        assertThrows<IllegalArgumentException> {
            previousStep.collect()
        }
    }

    @Test
    internal fun `should not add collection step as next with negative timeout and batch size`() {
        val previousStep = DummyStepSpecification()

        assertThrows<IllegalArgumentException> {
            previousStep.collect(Duration.ofMillis(-1), -1)
        }
    }
}
