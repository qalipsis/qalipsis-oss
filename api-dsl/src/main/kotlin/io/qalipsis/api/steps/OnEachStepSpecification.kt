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
