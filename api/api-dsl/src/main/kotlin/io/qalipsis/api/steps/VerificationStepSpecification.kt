/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
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
