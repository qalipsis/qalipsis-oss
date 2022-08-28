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