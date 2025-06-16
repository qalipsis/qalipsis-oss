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
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class FlowDatasourceStepSpecificationTest {

    @Test
    internal fun `should add datasource to the scenario`() {
        val scenario = TestScenarioFactory.scenario() as StepSpecificationRegistry

        val specification: suspend () -> Flow<Int> = { (1..10).asFlow() }
        scenario.datasource(specification)

        assertThat(scenario.rootSteps).index(0)
            .isEqualTo(DatasourceStepSpecification(specification))
    }

}