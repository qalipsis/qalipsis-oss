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
