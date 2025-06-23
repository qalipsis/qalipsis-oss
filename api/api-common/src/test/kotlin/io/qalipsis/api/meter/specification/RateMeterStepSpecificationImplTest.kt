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

package io.qalipsis.api.meter.specification

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.meters.steps.RateMeterStepSpecificationImpl
import io.qalipsis.api.meters.steps.TrackedThresholdRatio
import io.qalipsis.api.meters.steps.rate
import org.junit.jupiter.api.Test

/**
 * @author Francisca Eze
 */
internal class RateMeterStepSpecificationImplTest {

    @Test
    internal fun `should add rate step without failure specification as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (context: StepContext<Unit, Unit>, input: Unit) -> TrackedThresholdRatio =
            { _, _ -> TrackedThresholdRatio(12.0, 15.0) }
        previousStep.rate(name = "test-rate", specification)

        assertThat(
            RateMeterStepSpecificationImpl(
                "test-rate",
                specification
            )
        ).isEqualTo(previousStep.nextSteps[0])
    }


    @Test
    internal fun `should add rate step with failure specification as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (context: StepContext<Unit, Unit>, input: Unit) -> TrackedThresholdRatio =
            { _, _ -> TrackedThresholdRatio(12.0, 15.0) }
        previousStep.rate(name = "test-rate", specification)
            .shouldFailWhen {
                current.isLessThan(7.0)
            }

        assertThat(
            RateMeterStepSpecificationImpl(
                "test-rate",
                specification
            )
        ).isEqualTo(previousStep.nextSteps[0])
    }

}
