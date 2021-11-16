package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected

/**
 * Specification for a [io.qalipsis.core.factories.steps.OnSeachStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class OnEachStepSpecification<INPUT>(
    val statement: (input: INPUT) -> Unit
) : AbstractStepSpecification<INPUT, INPUT, OnEachStepSpecification<INPUT>>()

/**
 * Executes a statement on each received value.
 *
 * @param block the statement to execute.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.onEach(
    block: (input: INPUT) -> Unit = { }
): OnEachStepSpecification<INPUT> {
    val step = OnEachStepSpecification(block)
    this.add(step)
    return step
}
