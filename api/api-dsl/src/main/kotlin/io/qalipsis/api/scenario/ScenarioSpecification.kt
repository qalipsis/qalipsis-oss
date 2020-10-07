package io.qalipsis.api.scenario

import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.context.StepName
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.SingletonStepSpecification
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.sync.ImmutableSlot
import io.qalipsis.core.factories.orchestration.rampup.RampUpStrategy
import kotlinx.coroutines.runBlocking

/**
 *
 * @author Eric Jessé
 */
class ScenarioSpecificationImplementation(
        internal val name: String
) : MutableScenarioSpecification, ConfigurableScenarioSpecification, ReadableScenarioSpecification,
    RampUpSpecification {

    override var minionsCount = 1

    override val rootSteps = mutableListOf<StepSpecification<*, *, *>>()

    // Visible for test only.
    internal val registeredSteps = mutableMapOf<String, ImmutableSlot<StepSpecification<*, *, *>>>()

    override var rampUpStrategy: RampUpStrategy? = null

    override var retryPolicy: RetryPolicy? = null

    override var dagsCount = 0

    override fun add(step: StepSpecification<*, *, *>) {
        rootSteps.add(step)
        register(step)
        step.directedAcyclicGraphId = this.buildDagId()
    }

    override fun registerNext(previousStep: StepSpecification<*, *, *>, nextStep: StepSpecification<*, *, *>) {
        register(nextStep)
        // If any step is a singleton, a new DAG is built.
        if (nextStep.directedAcyclicGraphId.isNullOrBlank()) {
            if (previousStep is SingletonStepSpecification<*, *, *> || nextStep is SingletonStepSpecification<*, *, *>) {
                nextStep.directedAcyclicGraphId = buildDagId()
            } else {
                nextStep.directedAcyclicGraphId = previousStep.directedAcyclicGraphId
            }
        }
    }

    override fun register(step: StepSpecification<*, *, *>) {
        step.scenario = this
        if (!step.name.isNullOrBlank()) {
            runBlocking {
                registeredSteps.computeIfAbsent(step.name!!) { ImmutableSlot() }.also {
                    if (it.isEmpty()) {
                        it.set(step)
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <O> find(stepName: StepName) =
        registeredSteps.computeIfAbsent(stepName) { ImmutableSlot() }.get() as StepSpecification<*, O, *>?

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

    override fun buildDagId(): DirectedAcyclicGraphId {
        return "dag-${++dagsCount}"
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

    val dagsCount: Int
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
     * Defines the ramp-up strategy to start all the minions on a scenario.
     */
    fun strategy(rampUpStrategy: RampUpStrategy)
}

interface RetrySpecification {

    /**
     * Defines the default retry strategy for all the steps of the scenario.
     * The strategy can be redefined individually for each step.
     */
    fun retryPolicy(retryPolicy: RetryPolicy)
}

/**
 * Interface of a scenario as seen by the steps.
 */
interface MutableScenarioSpecification : ScenarioSpecification {

    suspend fun <O> find(stepName: StepName): StepSpecification<*, O, *>?

    fun exists(stepName: StepName): Boolean

    /**
     * Provides a predictive unique [DirectedAcyclicGraphId].
     */
    fun buildDagId(): DirectedAcyclicGraphId

    /**
     * Adds the step as root of the scenario and assign a relevant [StepSpecification.directedAcyclicGraphId].
     */
    fun add(step: StepSpecification<*, *, *>)

    /**
     * [register] [nextStep] in the scenario and assigns it a relevant [StepSpecification.directedAcyclicGraphId].
     *
     * This does not add [nextStep] to the list of [previousStep]'s next steps.
     */
    fun registerNext(previousStep: StepSpecification<*, *, *>, nextStep: StepSpecification<*, *, *>)

    /**
     * Adds the step to the scenario registry for later use.
     */
    fun register(step: StepSpecification<*, *, *>)
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
