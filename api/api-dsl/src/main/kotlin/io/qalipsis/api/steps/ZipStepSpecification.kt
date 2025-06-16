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

import cool.graph.cuid.Cuid
import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.StepName
import io.qalipsis.api.scenario.ScenarioSpecification
import javax.validation.constraints.NotBlank

/**
 * Specification for a [io.qalipsis.core.factory.steps.ZipStep].
 *
 * @author Polina Bril
 */
@Introspected
data class ZipStepSpecification<INPUT, OUTPUT>(
    val secondaryStepName: @NotBlank StepName
) : AbstractStepSpecification<INPUT, Pair<INPUT, OUTPUT>, ZipStepSpecification<INPUT, OUTPUT>>()

/**
 * Joins parallel sources that are not correlated.
 *
 * @param on specification of the step that should generate the remote records.
 */
fun <INPUT, OTHER_INPUT> StepSpecification<*, INPUT, *>.zip(
    on: (scenario: ScenarioSpecification) -> StepSpecification<*, OTHER_INPUT, *>
): ZipStepSpecification<INPUT, Pair<INPUT, OTHER_INPUT>> {

    // Since the relationship is performed in the step name, we generate one in case it is not specified by the user.
    val secondaryStep = on(scenario)
    if (secondaryStep.name.isBlank()) {
        secondaryStep.name = Cuid.createCuid()
    }
    // We force the step to be known by the scenario.
    scenario.register(secondaryStep)

    @Suppress("UNCHECKED_CAST")
    val step = ZipStepSpecification<INPUT, Pair<INPUT, OTHER_INPUT>>(secondaryStep.name)
    this.add(step)
    return step
}