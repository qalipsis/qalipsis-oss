package io.qalipsis.api.steps

import io.qalipsis.api.context.DirectedAcyclicGraphId
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

    override var directedAcyclicGraphId: DirectedAcyclicGraphId
        get() = wrappedStepSpec.directedAcyclicGraphId
        set(value) {
            wrappedStepSpec.directedAcyclicGraphId = value
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

    override fun runOn(selectors: Map<String, String>) {
        wrappedStepSpec.runOn(selectors)
    }

    override fun split(block: AbstractPluginStepWrapper<I, O>.() -> Unit): AbstractPluginStepWrapper<I, O> {
        throw InvalidSpecificationException(
            "The split operation is not supported on a wrapped step, call it directly on the source step"
        )
    }
}
