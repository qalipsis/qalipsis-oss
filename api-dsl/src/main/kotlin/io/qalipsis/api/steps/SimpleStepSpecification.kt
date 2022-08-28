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
