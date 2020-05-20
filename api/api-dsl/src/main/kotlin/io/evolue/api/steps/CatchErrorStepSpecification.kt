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

fun <INPUT> StepSpecification<*, INPUT, *>.catchError(
    block: (error: Collection<StepError>) -> Unit): CatchErrorStepSpecification<INPUT> {
    val step = CatchErrorStepSpecification<INPUT>(block)
    this.add(step)
    return step
}