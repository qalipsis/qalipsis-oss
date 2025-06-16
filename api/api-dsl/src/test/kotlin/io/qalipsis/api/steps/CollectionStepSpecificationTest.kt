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
