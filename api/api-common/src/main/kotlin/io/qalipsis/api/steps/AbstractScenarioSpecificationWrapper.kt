package io.qalipsis.api.steps

import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.StepName
import io.qalipsis.api.scenario.MutableScenarioSpecification
import io.qalipsis.api.scenario.ScenarioSpecification

/**
 * Wrapper designed to call the steps of a plugin only on typed scenarios.
 *
 * For instance, having a plugin called `foo` with a step called `bar`, the step can be called using:
 *
 * ```
 * scenario()
 *     .foo() # Returns an instance of FooScenarioWrapper extending AbstractScenarioSpecificationWrapper and implementing FooScenario.
 *     .bar(...) # Is only callable on steps of type FooSteps, which allows using the same step name in different plugins.
 * ```
 *
 *
 * @author Eric Jessé
 */
abstract class AbstractScenarioSpecificationWrapper(scenario: ScenarioSpecification) : MutableScenarioSpecification,
    ScenarioSpecification {

    private val wrappedScenario = scenario as MutableScenarioSpecification

    override fun add(step: StepSpecification<*, *, *>) {
        wrappedScenario.add(step)
    }

    override fun register(step: StepSpecification<*, *, *>) {
        wrappedScenario.register(step)
    }

    override fun registerNext(previousStep: StepSpecification<*, *, *>, nextStep: StepSpecification<*, *, *>) {
        wrappedScenario.registerNext(previousStep, nextStep)
    }

    override suspend fun <O> find(stepName: StepName): StepSpecification<*, O, *>? {
        return wrappedScenario.find(stepName)
    }

    override fun exists(stepName: StepName): Boolean {
        return wrappedScenario.exists(stepName)
    }

    override fun buildDagId(): DirectedAcyclicGraphId {
        return wrappedScenario.buildDagId()
    }
}