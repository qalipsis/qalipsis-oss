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