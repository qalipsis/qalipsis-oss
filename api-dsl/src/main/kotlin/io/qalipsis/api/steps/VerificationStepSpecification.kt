/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.api.steps

import io.micronaut.core.annotation.Introspected

/**
 * Specification for a [io.qalipsis.core.factory.steps.VerificationStep].
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
