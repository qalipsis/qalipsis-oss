package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected

/**
 * Specification for a [io.qalipsis.core.factories.steps.AssertionStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class AssertionStepSpecification<INPUT, OUTPUT>(
        val assertionBlock: suspend (input: INPUT) -> OUTPUT
) : AbstractStepSpecification<INPUT, OUTPUT, AssertionStepSpecification<INPUT, OUTPUT>>()

/**
 * Executes assertions on the input and transforms it.
 * Any common assertion library can be used: JUnit, Assertk, your own one...
 *
 * If any assertion fails, the step fails and the context is set exhausted.
 *
 * @param assertionBlock set of assertions and conversions to perform on the input.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.assertAndMap(
        assertionBlock: (suspend (input: INPUT) -> OUTPUT)): AssertionStepSpecification<INPUT, OUTPUT> {
    val step = AssertionStepSpecification(assertionBlock)
    this.add(step)
    return step
}

/**
 * Executes assertions on the input and forwards it to next step.
 * Any common assertion library can be used: JUnit, Assertk, your own one...
 *
 * If any assertion fails, the step fails and the context is set exhausted.
 *
 * @param assertionBlock set of assertions to perform on the input.
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.assert(
        assertionBlock: (suspend (input: INPUT) -> Unit)): AssertionStepSpecification<INPUT, INPUT> {
    val step = AssertionStepSpecification<INPUT, INPUT>({ value ->
        assertionBlock(value)
        value
    })
    this.add(step)
    return step
}
