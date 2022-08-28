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
import io.qalipsis.api.context.StepContext

/**
 * Specification for a [io.qalipsis.core.factory.steps.MapWithContextStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class MapWithContextStepSpecification<INPUT, OUTPUT>(
    val block: (context: StepContext<INPUT, OUTPUT>, input: INPUT) -> OUTPUT
) : AbstractStepSpecification<INPUT, OUTPUT, MapWithContextStepSpecification<INPUT, OUTPUT>>()

/**
 * Converts any input into a different output, also considering the context.
 *
 * @param block the rule to convert the input and the context into the output.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.mapWithContext(
    @Suppress(
        "UNCHECKED_CAST"
    ) block: (context: StepContext<INPUT, OUTPUT>, input: INPUT) -> OUTPUT = { _, value -> value as OUTPUT }
): MapWithContextStepSpecification<INPUT, OUTPUT> {
    val step = MapWithContextStepSpecification(block)
    this.add(step)
    return step
}
