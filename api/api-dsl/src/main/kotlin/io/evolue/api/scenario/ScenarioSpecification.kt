package io.evolue.api.scenario

import io.evolue.api.context.DirectedAcyclicGraphId
import io.evolue.api.context.ScenarioId
import io.evolue.api.context.StepName
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.StepSpecification
import io.evolue.core.factory.orchestration.rampup.RampUpStrategy

/**
 *
 * @author Eric Jessé
 */
internal class ScenarioSpecificationImplementation(
    internal val name: String
) : MutableScenarioSpecification, ConfigurableScenarioSpecification, ReadableScenarioSpecification,
    RampUpSpecification {

    override var minionsCount = 1

    override val rootSteps = mutableListOf<StepSpecification<*, *, *>>()

    // Visible for test only.
    internal val registeredSteps = mutableMapOf<String, StepSpecification<*, *, *>>()

    override var rampUpStrategy: RampUpStrategy? = null

    override var retryPolicy: RetryPolicy? = null

    private var dagCount = 0

    override fun add(step: StepSpecification<*, *, *>) {
        rootSteps.add(step)
        register(step)
        step.directedAcyclicGraphId = this.getDagId()
    }

    override fun register(step: StepSpecification<*, *, *>) {
        step.scenario = this
        if (step.name?.isNotBlank() == true) {
            registeredSteps[step.name!!] = step
        }
    }

    override fun <O> find(stepName: StepName) = registeredSteps[stepName] as StepSpecification<*, O, *>?

    override fun exists(stepName: StepName) = registeredSteps.containsKey(stepName)

    override fun rampUp(specification: RampUpSpecification.() -> Unit) {
        this.specification()
    }

    override fun strategy(rampUpStrategy: RampUpStrategy) {
        this.rampUpStrategy = rampUpStrategy
    }

    override fun retryPolicy(retryPolicy: RetryPolicy) {
        this.retryPolicy = retryPolicy
    }

    override fun getDagId(): DirectedAcyclicGraphId {
        return "dag-${++dagCount}"
    }
}

interface ReadableScenarioSpecification {

    /**
     * Default minions count to run the scenario when runtime factor is 1.
     */
    val minionsCount: Int

    val rampUpStrategy: RampUpStrategy?

    val retryPolicy: RetryPolicy?

    val rootSteps: List<StepSpecification<*, *, *>>
}

interface ConfigurableScenarioSpecification : RetrySpecification {

    fun rampUp(specification: RampUpSpecification.() -> Unit)

    /**
     * Default number of minions. This value is multiplied by a runtime factor to provide the total number of minions on the scenario.
     */
    var minionsCount: Int
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
    fun retryPolicy(retryPolicy: RetryPolicy)
}

/**
 * Interface of a scenario as seen by the steps.
 */
interface MutableScenarioSpecification : ScenarioSpecification {

    /**
     * Add the step as root of the scenario and assign a relevant [StepSpecification.directedAcyclicGraphId].
     */
    fun add(step: StepSpecification<*, *, *>)

    fun register(step: StepSpecification<*, *, *>)

    fun <O> find(stepName: StepName): StepSpecification<*, O, *>?

    fun exists(stepName: StepName): Boolean

    /**
     * Provide a predictive unique [DirectedAcyclicGraphId].
     */
    fun getDagId(): DirectedAcyclicGraphId
}

/**
 *
 */
interface ScenarioSpecification

internal val scenariosSpecifications = mutableMapOf<ScenarioId, ReadableScenarioSpecification>()

fun scenario(name: ScenarioId,
             configuration: (ConfigurableScenarioSpecification.() -> Unit) = { }): ScenarioSpecification {
    val scenario = ScenarioSpecificationImplementation(name)
    scenario.configuration()
    scenariosSpecifications[name] = scenario

    return scenario
}
