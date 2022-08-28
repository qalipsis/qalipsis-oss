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

    override fun add(step: StepSpecification<*, *, *>) {
        wrappedStepSpec.add(step)
    }

    override fun tag(tags: Map<String, String>) {
        wrappedStepSpec.tag(tags)
    }

    override fun split(block: AbstractPluginStepWrapper<I, O>.() -> Unit): AbstractPluginStepWrapper<I, O> {
        throw InvalidSpecificationException(
            "The split operation is not supported on a wrapped step, call it directly on the source step"
        )
    }

    override val tags: Map<String, String> = wrappedStepSpec.tags
}
