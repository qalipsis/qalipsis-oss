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
import javax.validation.constraints.NotBlank

/**
 * Specification for a [io.qalipsis.core.factory.steps.ShelveStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class ShelveStepSpecification<INPUT>(
    val specification: (input: INPUT) -> Map<@NotBlank String, Any?>
) : AbstractStepSpecification<INPUT, INPUT, ShelveStepSpecification<INPUT>>()

/**
 * Shelves the result of [specification] into a cache for later use.
 *
 * @param specification the rule to extract from the input the set of key/values to shelve. The keys of the map are used as name to later [unshelve] the value.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.shelve(
    specification: (input: INPUT) -> Map<String, Any?>
): ShelveStepSpecification<INPUT> {
    val step = ShelveStepSpecification(specification)
    this.add(step)
    return step
}

/**
 * Shelves the input into a cache for later use with the given name.
 *
 * @param name name of the value to later [unshelve] in the cache.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.shelve(name: String): ShelveStepSpecification<INPUT> {
    return this.shelve { input -> mapOf(name to input) }
}


/**
 * Shelves the input into a cache for later use with the given name.
 *
 * @param name name of the value to later [unshelve] in the cache.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.shelve(
    name: String,
    specification: (input: INPUT) -> Any?
): ShelveStepSpecification<INPUT> {
    return this.shelve { input -> mapOf(name to specification(input)) }
}
