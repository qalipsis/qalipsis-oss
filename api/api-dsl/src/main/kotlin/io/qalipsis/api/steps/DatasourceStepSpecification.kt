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

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import kotlinx.coroutines.flow.Flow

/**
 * Specification for a [io.qalipsis.api.steps.DatasourceStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class DatasourceStepSpecification<OUTPUT>(
    val specification: suspend () -> Flow<OUTPUT>
) : AbstractStepSpecification<Unit, OUTPUT?, DatasourceStepSpecification<OUTPUT>>()

/**
 * Simple datasource used to generate data for later steps. This step is not a singleton and executes once per minion.
 *
 * @author Eric Jessé
 */
fun <OUTPUT> ScenarioSpecification.datasource(
    specification: (suspend () -> Flow<OUTPUT>)
): DatasourceStepSpecification<OUTPUT> {
    val step = DatasourceStepSpecification(specification)
    (this as StepSpecificationRegistry).add(step)
    return step
}
