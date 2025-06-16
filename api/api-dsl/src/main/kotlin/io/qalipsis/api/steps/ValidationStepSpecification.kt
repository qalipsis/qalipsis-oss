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
import io.qalipsis.api.context.StepError

/**
 * Specification for a [io.qalipsis.core.factory.steps.ValidationStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class ValidationStepSpecification<INPUT>(
    val specification: (input: INPUT) -> List<StepError>
) : AbstractStepSpecification<INPUT, INPUT, ValidationStepSpecification<INPUT>>()

/**
 * Generates validation errors on the records not matching [specification].
 *
 * @param specification the conditions to match to forward the input.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.validate(
    specification: ((input: INPUT) -> List<StepError>)
): ValidationStepSpecification<INPUT> {
    val step = ValidationStepSpecification(specification)
    this.add(step)
    return step
}
