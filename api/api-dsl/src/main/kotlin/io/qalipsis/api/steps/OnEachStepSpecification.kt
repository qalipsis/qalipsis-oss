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

/**
 * Specification for a [io.qalipsis.core.factory.steps.OnSeachStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class OnEachStepSpecification<INPUT>(
    val statement: (input: INPUT) -> Unit
) : AbstractStepSpecification<INPUT, INPUT, OnEachStepSpecification<INPUT>>()

/**
 * Executes a statement on each received value.
 *
 * @param block the statement to execute.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.onEach(
    block: (input: INPUT) -> Unit = { }
): OnEachStepSpecification<INPUT> {
    val step = OnEachStepSpecification(block)
    this.add(step)
    return step
}
