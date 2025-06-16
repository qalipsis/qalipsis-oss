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

import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.scenario.StepSpecificationRegistry
import java.time.Duration

/**
 * Wrapper designed to call the steps of a plugin only on ones of a given type.
 *
 * For instance, having a plugin called `foo` with a step called `bar`, the step can be called using:
 *
 * ```
 * mypreviousStep(...)
 *     .foo() # Returns an instance of FooStepWrapper extending AbstractPluginStepWrapper and implementing FooStep.
 *     .bar(...) # Is only callable on steps of type FooSteps, which allows using the same step name in different plugins.
 * ```
 *
 * @author Eric Jessé
 */
abstract class AbstractPluginStepWrapper<I, O>(private val wrappedStepSpec: StepSpecification<I, O, *>) :
    StepSpecification<I, O, AbstractPluginStepWrapper<I, O>> {

    override var name: StepName
        get() = wrappedStepSpec.name
        set(value) {
            wrappedStepSpec.name = value
        }

    override var scenario: StepSpecificationRegistry
        get() = wrappedStepSpec.scenario
        set(value) {
            wrappedStepSpec.scenario = value
        }

    override var directedAcyclicGraphName: DirectedAcyclicGraphName
        get() = wrappedStepSpec.directedAcyclicGraphName
        set(value) {
            wrappedStepSpec.directedAcyclicGraphName = value
        }

    override val timeout: Duration?
        get() = wrappedStepSpec.timeout

    override val iterations: Long
        get() = wrappedStepSpec.iterations

    override val iterationPeriods: Duration
        get() = wrappedStepSpec.iterationPeriods

    override val retryPolicy: RetryPolicy?
        get() = wrappedStepSpec.retryPolicy

    override val nextSteps: MutableList<StepSpecification<*, *, *>>
        get() = wrappedStepSpec.nextSteps

    override var reporting: StepReportingSpecification = wrappedStepSpec.reporting

    override val stopIterationsOnError: Boolean
        get() = wrappedStepSpec.stopIterationsOnError

    override fun add(step: StepSpecification<*, *, *>) {
        wrappedStepSpec.add(step)
    }

    override fun tag(tags: Map<String, String>) {
        wrappedStepSpec.tag(tags)
    }

    override fun split(block: StepSpecification<I, O, *>.() -> Unit): AbstractPluginStepWrapper<I, O> {
        throw InvalidSpecificationException(
            "The split operation is not supported on a wrapped step, call it directly on the source step"
        )
    }

    override val tags: Map<String, String> = wrappedStepSpec.tags
}
