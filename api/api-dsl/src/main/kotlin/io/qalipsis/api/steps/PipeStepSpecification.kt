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
import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry

/**
 * Specification for a [io.qalipsis.core.factory.steps.PipeStep].
 *
 * @author Eric Jessé
 */
@Introspected
class PipeStepSpecification<INPUT> : AbstractStepSpecification<INPUT, INPUT, PipeStepSpecification<INPUT>>()

/**
 * Do nothing, just consumes the input and sends it to the output. This step does not bring any logic, but is used
 * to support special workflows (joins, splits...)
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.pipe(): PipeStepSpecification<INPUT> {
    val step = PipeStepSpecification<INPUT>()
    this.add(step)
    return step
}

/**
 * Do nothing, just consumes the input and sends it to the output. This step does not bring any logic, but is used
 * to support special workflows (joins, splits...)
 *
 * @author Eric Jessé
 */
fun <INPUT> ScenarioSpecification.pipe(): PipeStepSpecification<INPUT> {
    val step = PipeStepSpecification<INPUT>()
    (this as StepSpecificationRegistry).add(step)
    return step
}

/**
 * Specification for a [io.qalipsis.core.factory.steps.PipeStep], but acting as a singleton.
 *
 * @author Eric Jessé
 */
@Introspected
class SingletonPipeStepSpecification<INPUT> :
    SingletonStepSpecification,
    AbstractStepSpecification<INPUT, INPUT, SingletonPipeStepSpecification<INPUT>>() {

    override val singletonConfiguration = SingletonConfiguration(SingletonType.UNICAST)
}

/**
 * Do nothing, just consumes the input and sends it to the output. This step does not bring any logic, but is used
 * to support special workflows (joins, splits...)
 *
 * Contrary to [pipe], this function creates a tube as a singleton, meaning that it is visited to be executed only once.
 *
 * @see [pipe]
 *
 * @author Eric Jessé
 */
fun <INPUT> StepSpecification<*, INPUT, *>.singletonPipe(): SingletonPipeStepSpecification<INPUT> {
    val step = SingletonPipeStepSpecification<INPUT>()
    this.add(step)
    return step
}
