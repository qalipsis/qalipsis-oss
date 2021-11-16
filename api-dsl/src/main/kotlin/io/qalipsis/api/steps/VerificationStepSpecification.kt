package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected

/**
 * Specification for a [io.qalipsis.core.factories.steps.VerificationStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class VerificationStepSpecification<INPUT, OUTPUT>(
    val verificationBlock: suspend (input: INPUT) -> OUTPUT
) : AbstractStepSpecification<INPUT, OUTPUT, VerificationStepSpecification<INPUT, OUTPUT>>()

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
fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.verifyAndMap(
    assertionBlock: (suspend (input: INPUT) -> OUTPUT)
): VerificationStepSpecification<INPUT, OUTPUT> {
    val step = VerificationStepSpecification(assertionBlock)
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
fun <INPUT> StepSpecification<*, INPUT, *>.verify(
    assertionBlock: (suspend (input: INPUT) -> Unit)
): VerificationStepSpecification<INPUT, INPUT> {
    val step = VerificationStepSpecification<INPUT, INPUT> { value ->
        assertionBlock(value)
        value
    }
    this.add(step)
    return step
}
