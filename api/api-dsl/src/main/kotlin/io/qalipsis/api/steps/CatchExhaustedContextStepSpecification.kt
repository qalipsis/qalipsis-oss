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
