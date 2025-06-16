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
import assertk.assertions.index
import assertk.assertions.isEqualTo
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class SimpleStepSpecificationTest {

    @Test
    internal fun `should add simple step as next`() {
        val previousStep = DummyStepSpecification()
        val specification: suspend (context: StepContext<Int, String>) -> Unit = { _ -> }
        previousStep.execute(specification)

        assertEquals(SimpleStepSpecification(specification), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add simple step to scenario`() {
        val scenario = TestScenarioFactory.scenario() as StepSpecificationRegistry
        val specification: suspend (context: StepContext<Unit, String>) -> Unit = { _ -> }
        scenario.execute(specification)

        assertThat(scenario.rootSteps).index(0)
            .isEqualTo(SimpleStepSpecification(specification))
    }

}
