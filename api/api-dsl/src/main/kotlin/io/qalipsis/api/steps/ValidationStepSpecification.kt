package io.qalipsis.api.steps

import io.qalipsis.api.context.StepError
import io.micronaut.core.annotation.Introspected

/**
 * Specification for a [io.qalipsis.core.factories.steps.ValidationStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class ValidationStepSpecification<INPUT>(
    val specification: (input: INPUT) -> List<StepError>
) : AbstractStepSpecification<INPUT, INPUT, ValidationStepSpecification<INPUT>>()

/**
 * Generates validation errors on the records not matching [specification].
 *
 * @param specification the conditions to match to forward the input.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.validate(
        specification: ((input: INPUT) -> List<StepError>)): ValidationStepSpecification<INPUT> {
    val step = ValidationStepSpecification(specification)
    this.add(step)
    return step
}
