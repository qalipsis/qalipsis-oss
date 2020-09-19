package io.evolue.api.steps

/**
 * Specification for a [io.evolue.core.factories.steps.FilterStep].
 *
 * @author Eric Jessé
 */
data class FilterStepSpecification<INPUT>(
    val specification: (input: INPUT) -> Boolean
) : AbstractStepSpecification<INPUT, INPUT, FilterStepSpecification<INPUT>>()

/**
 * Only forwards the records matching [specification].
 *
 * @param specification the conditions to match to forward the input.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.filter(
        specification: ((input: INPUT) -> Boolean)): FilterStepSpecification<INPUT> {
    val step = FilterStepSpecification(specification)
    this.add(step)
    return step
}
