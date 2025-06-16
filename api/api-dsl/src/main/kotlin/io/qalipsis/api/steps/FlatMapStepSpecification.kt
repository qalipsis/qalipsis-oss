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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * Specification for a [io.qalipsis.core.factory.steps.FlatMapStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class FlatMapStepSpecification<INPUT, OUTPUT>(
    val block: (input: INPUT) -> Flow<OUTPUT>
) : AbstractStepSpecification<INPUT, OUTPUT, FlatMapStepSpecification<INPUT, OUTPUT>>()

/**
 * Converts any input into a [Flow] of records provided one by one
 * to the next step.
 *
 * @param block the rule to convert the input into a [Flow].
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.flatMap(
    block: (input: INPUT) -> Flow<OUTPUT>
): FlatMapStepSpecification<INPUT, OUTPUT> {
    val step = FlatMapStepSpecification(block)
    this.add(step)
    return step
}

/**
 * Converts a [Iterable] into a [Flow] of values provided one by one to the next step.
 *
 * @author Eric Jessé
 */
@JvmName("flattenCollection")
fun <INPUT> StepSpecification<*, out Iterable<INPUT>, *>.flatten(): FlatMapStepSpecification<out Iterable<INPUT>, INPUT> {
    return flatMap { it.asFlow() }
}

/**
 * Converts a [Array] into a [Flow] of values provided one by one to the next step.
 *
 * @author Eric Jessé
 */
@JvmName("flattenArray")
fun <INPUT> StepSpecification<*, out Array<INPUT>, *>.flatten(): FlatMapStepSpecification<out Array<INPUT>, INPUT> {
    return flatMap { it.asFlow() }
}

/**
 * Converts a [Sequence] into a [Flow] of values provided one by one to the next step.
 *
 * @author Eric Jessé
 */
@JvmName("flattenSequence")
fun <INPUT> StepSpecification<*, out Sequence<INPUT>, *>.flatten(): FlatMapStepSpecification<out Sequence<INPUT>, INPUT> {
    return flatMap { it.asFlow() }
}

/**
 * Converts a [Map] into a [Flow] of its entries provided one by one to the next step.
 *
 * @author Eric Jessé
 */
@JvmName("flattenMap")
fun <K, V> StepSpecification<*, out Map<K, V>, *>.flatten(): FlatMapStepSpecification<out Map<K, V>, Pair<K, V>> {
    return flatMap { it.entries.map { e -> e.key to e.value }.asFlow() }
}
