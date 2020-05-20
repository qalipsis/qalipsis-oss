package io.evolue.api.steps

import io.evolue.api.context.StepContext

/**
 * Specification for a [io.evolue.core.factory.steps.CatchExhaustedContextStep].
 *
 * @author Eric Jessé
 */
data class CatchExhaustedContextStepSpecification<INPUT, OUTPUT>(
    val block: suspend (context: StepContext<INPUT, OUTPUT>) -> Unit
) : AbstractStepSpecification<INPUT, OUTPUT, CatchExhaustedContextStepSpecification<INPUT, OUTPUT>>()

fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.catchExhaustedContext(
    block: suspend (context: StepContext<INPUT, OUTPUT>) -> Unit): CatchExhaustedContextStepSpecification<INPUT, OUTPUT> {
    val step = CatchExhaustedContextStepSpecification(block)
    this.add(step)
    return step
}