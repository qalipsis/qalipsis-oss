package io.evolue.api.steps

/**
 * Specification for a [io.evolue.core.factory.steps.AssertionStep].
 *
 * @author Eric Jessé
 */
data class AssertionStepSpecification<INPUT, OUTPUT>(
    val assertionBlock: suspend (input: INPUT) -> OUTPUT
) : AbstractStepSpecification<INPUT, OUTPUT, AssertionStepSpecification<INPUT, OUTPUT>>()

fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.assertAndMap(
    assertionBlock: (suspend (input: INPUT) -> OUTPUT)): AssertionStepSpecification<INPUT, OUTPUT> {
    val step = AssertionStepSpecification(assertionBlock)
    this.add(step)
    return step
}

fun <INPUT> StepSpecification<*, INPUT, *>.assert(
    assertionBlock: (suspend (input: INPUT) -> Unit)): AssertionStepSpecification<INPUT, INPUT> {
    val step = AssertionStepSpecification<INPUT, INPUT>({ value ->
        assertionBlock(value)
        value
    })
    this.add(step)
    return step
}