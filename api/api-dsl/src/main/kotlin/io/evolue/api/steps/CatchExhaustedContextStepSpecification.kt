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

/**
 * Executes user-defined operations on an exhausted context. The context can be updated to declare it as non exhausted.
 *
 * An exhausted context is any execution context that had an error in an earlier step.
 *
 * @param block operations to execute on the exhausted context to analyze and update it
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.catchExhaustedContext(
        block: suspend (context: StepContext<INPUT, OUTPUT>) -> Unit): CatchExhaustedContextStepSpecification<INPUT, OUTPUT> {
    val step = CatchExhaustedContextStepSpecification(block)
    this.add(step)
    return step
}