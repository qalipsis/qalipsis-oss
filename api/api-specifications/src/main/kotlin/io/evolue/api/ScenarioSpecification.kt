package io.evolue.api

import io.evolue.api.context.ScenarioId
import io.evolue.api.context.StepName
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.StepSpecification
import io.evolue.core.factory.orchestration.rampup.RampUpStrategy

/**
 *
 * @author Eric Jessé
 */
class ScenarioSpecificationImplementation(
    internal val name: String
) : ScenarioSpecification, ConfigurableScenarioSpecification, RampUpSpecification, RetrySpecification {

    override var minionsCountFactor = 1

    internal val rootSteps = mutableListOf<StepSpecification<*, *, *>>()

    // Visible for test only.
    internal val registeredSteps = mutableMapOf<String, StepSpecification<*, *, *>>()

    internal var rampUpStrategy: RampUpStrategy? = null

    internal var retryPolicy: RetryPolicy? = null

    override fun add(step: StepSpecification<*, *, *>) {
        rootSteps.add(step)
        register(step)
    }

    override fun register(step: StepSpecification<*, *, *>) {
        step.scenario = this
        if (step.name?.isNotBlank() == true) {
            registeredSteps[step.name!!] = step
        }
    }

    override fun <O> find(stepName: StepName) = registeredSteps[stepName] as StepSpecification<*, O, *>?

    override fun rampUp(specification: RampUpSpecification.() -> Unit) {
        this.specification()
    }

    override fun strategy(rampUpStrategy: RampUpStrategy) {
        this.rampUpStrategy = rampUpStrategy
    }

    override fun strategy(retryPolicy: RetryPolicy) {
        this.retryPolicy = retryPolicy
    }
}

interface ConfigurableScenarioSpecification {

    fun rampUp(specification: RampUpSpecification.() -> Unit)

    /**
     * Default number of minions. This value is multiplied by a runtime factor to provide the total number of minions on the scenario.
     */
    var minionsCountFactor: Int
}

interface RampUpSpecification {

    /**
     * Define the ramp-up strategy to start all the minions on a scenario.
     */
    fun strategy(rampUpStrategy: RampUpStrategy)
}

interface RetrySpecification {

    /**
     * Define the default retry strategy for all the steps of the scenario.
     * The strategy can be redefined individually for each step.
     */
    fun strategy(retryPolicy: RetryPolicy)
}

interface ScenarioSpecification {

    fun add(step: StepSpecification<*, *, *>)

    fun register(step: StepSpecification<*, *, *>)

    fun <O> find(stepName: StepName): StepSpecification<*, O, *>?
}

internal val scenarios = mutableMapOf<ScenarioId, ScenarioSpecification>()

fun scenario(name: ScenarioId,
             configuration: (ConfigurableScenarioSpecification.() -> Unit) = { }): ScenarioSpecification {
    val scenario = ScenarioSpecificationImplementation(name)
    scenario.configuration()
    scenarios[name] = scenario
    return scenario
}