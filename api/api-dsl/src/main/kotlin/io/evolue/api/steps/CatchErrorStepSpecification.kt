package io.evolue.api.steps

import io.evolue.api.context.StepError

/**
 * Specification for a [io.evolue.core.factory.steps.CatchErrorStep].
 *
 * @author Eric Jessé
 */
data class CatchErrorStepSpecification<INPUT>(
    val block: (error: Collection<StepError>) -> Unit
) : AbstractStepSpecification<INPUT, INPUT, CatchErrorStepSpecification<INPUT>>()

/**
 * Processes the errors previously generated on the execution context.
 *
 * @param block operations to execute on the collection of errors
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.catchError(
        block: (error: Collection<StepError>) -> Unit): CatchErrorStepSpecification<INPUT> {
    val step = CatchErrorStepSpecification<INPUT>(block)
    this.add(step)
    return step
}