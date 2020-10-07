package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected

/**
 * Specification for a [io.qalipsis.core.factories.steps.FilterStep].
 *
 * @author Eric Jessé
 */
@Introspected
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

/**
 * Only forwards the not null elements.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT?, *>.filterNotNull(): FilterStepSpecification<INPUT> {
    val step = FilterStepSpecification<INPUT> { it != null }
    this.add(step)
    return step
}
