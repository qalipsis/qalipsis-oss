package io.qalipsis.api.scenario

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.StepName
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.rampup.RampUpStrategy
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.SingletonStepSpecification
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.sync.ImmutableSlot
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/**
 * Concrete implementation of all the interfaces relevant to define and use a scenario specification.
 *
 * @author Eric Jessé
 */
internal class ScenarioSpecificationImplementation(
    internal val name: String
) : StepSpecificationRegistry, ConfigurableScenarioSpecification, ConfiguredScenarioSpecification,
    RampUpSpecification, StartScenarioSpecification {

    override var minionsCount = 1

    override val rootSteps = concurrentList<StepSpecification<*, *, *>>()

    @KTestable
    private val registeredSteps = ConcurrentHashMap<String, ImmutableSlot<StepSpecification<*, *, *>>>()

    override var rampUpStrategy: RampUpStrategy? = null

    override var retryPolicy: RetryPolicy? = null

    override var dagsCount = 0

    override var dagsUnderLoad = concurrentSet<DirectedAcyclicGraphId>()

    override fun add(step: StepSpecification<*, *, *>) {
        step.scenario = this
        rootSteps.add(step)
        register(step)
        if (step.directedAcyclicGraphId.isBlank()) {
            step.directedAcyclicGraphId = this.buildDagId()
        }
    }

    override fun insertRoot(newRoot: StepSpecification<*, *, *>, rootToShift: StepSpecification<*, *, *>) {
        rootSteps.removeIf { it === rootToShift }
        newRoot.directedAcyclicGraphId = rootToShift.directedAcyclicGraphId
        add(newRoot)
        newRoot.add(rootToShift)
    }

    override fun registerNext(previousStep: StepSpecification<*, *, *>, nextStep: StepSpecification<*, *, *>) {
        register(nextStep)
        // If any step is a singleton, a new DAG is built.
        if (nextStep.directedAcyclicGraphId.isBlank()) {
            if (previousStep is SingletonStepSpecification || nextStep is SingletonStepSpecification) {
                nextStep.directedAcyclicGraphId = buildDagId(previousStep.directedAcyclicGraphId)
            } else {
                nextStep.directedAcyclicGraphId = previousStep.directedAcyclicGraphId
            }
        }
    }

    override fun register(step: StepSpecification<*, *, *>) {
        step.scenario = this
        if (step.name.isNotBlank()) {
            runBlocking {
                registeredSteps.computeIfAbsent(step.name) { ImmutableSlot() }.also {
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

    override fun buildDagId(parent: DirectedAcyclicGraphId?): DirectedAcyclicGraphId {
        val newDag = "dag-${++dagsCount}"
        // If the parent DAG is part of the loaded branch, the new one also.
        parent?.let {
            if (it in dagsUnderLoad) {
                dagsUnderLoad.add(newDag)
            }
        }
        return newDag
    }

    override fun start(): ScenarioSpecification {
        require(dagsUnderLoad.isEmpty()) { "start() can only be used once" }

        val dagId = buildDagId()
        dagsUnderLoad.add(dagId)
        return StartScenarioSpecificationWrapper(this, dagId)
    }
}
