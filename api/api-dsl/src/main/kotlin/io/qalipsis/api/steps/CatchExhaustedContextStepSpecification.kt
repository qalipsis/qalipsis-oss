package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.StepContext

/**
 * Specification for a [io.qalipsis.core.factories.steps.CatchExhaustedContextStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class CatchExhaustedContextStepSpecification<OUTPUT>(
    val block: suspend (context: StepContext<*, OUTPUT>) -> Unit
) : AbstractStepSpecification<Any?, OUTPUT, CatchExhaustedContextStepSpecification<OUTPUT>>()

/**
 * Executes user-defined operations on an exhausted context. The context can be updated to declare it as non exhausted.
 *
 * An exhausted context is any execution context that had an error in an earlier step.
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
        context.output.send(Unit)
    }
    this.add(step)
    return step
}
