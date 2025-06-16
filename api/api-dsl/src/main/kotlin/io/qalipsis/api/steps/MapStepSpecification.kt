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
 * Specification for a [io.qalipsis.core.factory.steps.MapStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class MapStepSpecification<INPUT, OUTPUT>(
    val block: (input: INPUT) -> OUTPUT
) : AbstractStepSpecification<INPUT, OUTPUT, MapStepSpecification<INPUT, OUTPUT>>()

/**
 * Converts any input into a different output.
 *
 * @param block the rule to convert the input into the output.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.map(
    @Suppress(
        "UNCHECKED_CAST"
    ) block: (input: INPUT) -> OUTPUT = { value -> value as OUTPUT }
): MapStepSpecification<INPUT, OUTPUT> {
    val step = MapStepSpecification(block)
    this.add(step)
    return step
}
