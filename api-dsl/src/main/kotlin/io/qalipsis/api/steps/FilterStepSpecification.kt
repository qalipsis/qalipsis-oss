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

/**
 * Specification for a [io.qalipsis.core.factory.steps.FilterStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class FilterStepSpecification<INPUT>(
    val specification: (input: INPUT) -> Boolean
) : AbstractStepSpecification<INPUT, INPUT, FilterStepSpecification<INPUT>>()

/**
 * Only forwards the records matching [specification].
 *
 * @param specification the conditions to match to forward the input.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.filter(
    specification: ((input: INPUT) -> Boolean)
): FilterStepSpecification<INPUT> {
    val step = FilterStepSpecification(specification)
    this.add(step)
    return step
}

/**
 * Only forwards the not null elements.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT?, *>.filterNotNull(): FilterStepSpecification<INPUT> {
    val step = FilterStepSpecification<INPUT> { it != null }
    this.add(step)
    return step
}
