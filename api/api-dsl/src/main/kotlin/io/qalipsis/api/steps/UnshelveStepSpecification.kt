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
 * Specification for a [io.qalipsis.core.factory.steps.UnshelveStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class UnshelveStepSpecification<INPUT, OUTPUT>(
    val names: List<@NotBlank String>,
    val delete: Boolean,
    val singular: Boolean
) : AbstractStepSpecification<INPUT, Pair<INPUT, OUTPUT?>, UnshelveStepSpecification<INPUT, OUTPUT>>()

/**
 * Retrieves a unique value previously cached with [shelve] and joins it to the input.
 *
 * @param name the name of the value as used in [shelve]
 * @param delete when set to true, the value is deleted from the cache once retrieved.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.unshelve(
    name: String,
    delete: Boolean = true
): UnshelveStepSpecification<INPUT, OUTPUT> {
    val step = UnshelveStepSpecification<INPUT, OUTPUT>(listOf(name), delete, true)
    this.add(step)
    return step
}

/**
 * Retrieves a list of values previously cached with [shelve] and joins them to the input as a map of name / value.
 *
 * @param names the names of the value as used in [shelve]
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.unshelve(
    vararg names: String
): UnshelveStepSpecification<INPUT, Map<String, Any?>> {
    val step = UnshelveStepSpecification<INPUT, Map<String, Any?>>(names.asList(), false, false)
    this.add(step)
    return step
}

/**
 * Retrieves and removes a list of values previously cached with [shelve] and joins them to the input as a map of name / value.
 *
 * @param names the names of the value as used in [shelve]
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.unshelveAndDelete(
    vararg names: String
): UnshelveStepSpecification<INPUT, Map<String, Any?>> {
    val step = UnshelveStepSpecification<INPUT, Map<String, Any?>>(names.asList(), true, false)
    this.add(step)
    return step
}
