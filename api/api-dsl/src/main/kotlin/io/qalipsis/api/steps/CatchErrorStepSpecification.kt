package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected
import io.qalipsis.api.context.StepError

/**
 * Specification for a [io.qalipsis.core.factories.steps.CatchErrorStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class CatchErrorStepSpecification(
    val block: (error: Collection<StepError>) -> Unit
) : AbstractStepSpecification<Any?, Any?, CatchErrorStepSpecification>()

/**
 * Processes the errors previously generated on the execution context.
 *
 * @param block operations to execute on the collection of errors
 *
 * @author Eric Jessé
 */
fun StepSpecification<*, *, *>.catchError(
    block: (error: Collection<StepError>) -> Unit
): CatchErrorStepSpecification {
    val step = CatchErrorStepSpecification(block)
    this.add(step)
    return step
}
