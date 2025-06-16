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
import io.qalipsis.api.context.StepError
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Specification for a [io.qalipsis.core.factory.steps.CatchErrorStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class CatchErrorsStepSpecification<OUTPUT>(
    val block: (errors: Collection<StepError>) -> Unit
) : AbstractStepSpecification<OUTPUT, OUTPUT, CatchErrorsStepSpecification<OUTPUT>>()

/**
 * Processes the errors previously generated on the execution context.
 * Whether there are errors or not, the potential value in the input is forwarded to the output.
 *
 * @param block operations to execute on the collection of errors
 *
 * @author Eric Jessé
 */
fun <OUTPUT> StepSpecification<*, OUTPUT, *>.catchErrors(
    block: (errors: Collection<StepError>) -> Unit
): CatchErrorsStepSpecification<OUTPUT> {
    val step = CatchErrorsStepSpecification<OUTPUT>(block)
    this.add(step)
    return step
}

/**
 * Logs the potential errors emitted while executing any previous step.
 * Whether there are errors or not, the potential value in the input is forwarded to the output.
 *
 * @param logger the logger to use, defaults to a [Logger] for ERRORS.
 *
 * @author Eric Jessé
 */
fun <OUTPUT> StepSpecification<*, OUTPUT, *>.logErrors(
    logger: Logger = LoggerFactory.getLogger("ERRORS")
): CatchErrorsStepSpecification<OUTPUT> {
    val step = CatchErrorsStepSpecification<OUTPUT> { errors ->
        errors.forEach { error -> logger.error(error.message) }
    }
    this.add(step)
    return step
}