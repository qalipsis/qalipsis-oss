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
 * Specification for a [io.qalipsis.core.factory.steps.CatchExhaustedContextStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class CatchExhaustedContextStepSpecification<OUTPUT>(
    val block: suspend (context: StepContext<*, OUTPUT>) -> Unit
) : AbstractStepSpecification<Any?, OUTPUT, CatchExhaustedContextStepSpecification<OUTPUT>>()

/**
 * Executes user-defined operations on an exhausted context. The context can be updated to declare it as non exhausted.
 * An exhausted context is any execution context that had an error in an earlier step.
 *
 * If the context is not exhausted, the potential value in the input is forwarded to the output.
 *
 * @param block operations to execute on the exhausted context to analyze and update it
 *
 * @author Eric Jessé
 */
fun <OUTPUT> StepSpecification<*, *, *>.catchExhaustedContext(
    block: suspend (context: StepContext<*, OUTPUT>) -> Unit
): CatchExhaustedContextStepSpecification<OUTPUT> {
    val step = CatchExhaustedContextStepSpecification(block)
    this.add(step)
    return step
}

/**
 * Recovers the context and enables it for the next steps.
 *
 * @author Eric Jessé
 */
fun StepSpecification<*, *, *>.recover(): CatchExhaustedContextStepSpecification<Unit> {
    val step = CatchExhaustedContextStepSpecification<Unit> { context ->
        context.isExhausted = false
        context.send(Unit)
    }
    this.add(step)
    return step
}
