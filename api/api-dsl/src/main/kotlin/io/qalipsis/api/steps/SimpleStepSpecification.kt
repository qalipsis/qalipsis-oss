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
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry

/**
 * Specification for a [io.qalipsis.core.factory.steps.SimpleStep].
 *
 * @author Eric Jessé
 */
@Introspected
data class SimpleStepSpecification<INPUT, OUTPUT>(
    val specification: suspend (context: StepContext<INPUT, OUTPUT>) -> Unit
) : AbstractStepSpecification<INPUT, OUTPUT, SimpleStepSpecification<INPUT, OUTPUT>>()

/**
 * Executes [specification] on the provided context.
 *
 * @param specification statements to perform on the context.
 *
 * @author Eric Jessé
 */
fun <OUTPUT> ScenarioSpecification.execute(
    specification: suspend (context: StepContext<Unit, OUTPUT>) -> Unit
): SimpleStepSpecification<Unit, OUTPUT> {
    val step = SimpleStepSpecification(specification)
    (this as StepSpecificationRegistry).add(step)
    return step
}

/**
 * Forwards a constant value to next steps.
 *
 * @param value the constant value to forward.
 *
 * @author Eric Jessé
 */
fun <OUTPUT> ScenarioSpecification.returns(value: OUTPUT): SimpleStepSpecification<Unit, OUTPUT> {
    val step = SimpleStepSpecification<Unit, OUTPUT> {
        it.send(value)
    }
    (this as StepSpecificationRegistry).add(step)
    return step
}

/**
 * Forwards a value to next steps.
 *
 * @param specification the statements to generate the value to forward.
 *
 * @author Eric Jessé
 */
fun <OUTPUT> ScenarioSpecification.returns(
    specification: suspend (context: StepContext<Unit, OUTPUT>) -> OUTPUT
): SimpleStepSpecification<Unit, OUTPUT> {
    val step = SimpleStepSpecification<Unit, OUTPUT> {
        it.send(specification(it))
    }
    (this as StepSpecificationRegistry).add(step)
    return step
}

/**
 * Executes [specification] on the provided context.
 *
 * @param specification statements to perform on the context.
 *
 * @author Eric Jessé
 */
fun <INPUT, OUTPUT> StepSpecification<*, INPUT, *>.execute(
    specification: suspend (context: StepContext<INPUT, OUTPUT>) -> Unit
): SimpleStepSpecification<INPUT, OUTPUT> {
    val step = SimpleStepSpecification(specification)
    this.add(step)
    return step
}
