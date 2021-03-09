package io.qalipsis.api.scenario

import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.StepName
import io.qalipsis.api.steps.StepSpecification

/**
 * Wrapper of a scenario to introduce the beginning of the "loaded tree": the subpart of the scenario, which will
 * received the load of the minions.
 *
 * @author Eric Jessé
 */
internal class StartScenarioSpecificationWrapper(
    private val delegated: StepSpecificationRegistry,
    private val dagId: DirectedAcyclicGraphId
) : StepSpecificationRegistry {

    override val rootSteps: List<StepSpecification<*, *, *>>
        get() = delegated.rootSteps

    override val dagsUnderLoad: Collection<DirectedAcyclicGraphId>
        get() = delegated.dagsUnderLoad

    override suspend fun <O> find(stepName: StepName): StepSpecification<*, O, *>? {
        return delegated.find(stepName)
    }

    override fun exists(stepName: StepName): Boolean {
        return delegated.exists(stepName)
    }

    override fun buildDagId(parent: DirectedAcyclicGraphId?): DirectedAcyclicGraphId {
        return delegated.buildDagId(parent)
    }

    override fun add(step: StepSpecification<*, *, *>) {
        step.directedAcyclicGraphId = dagId
        delegated.add(step)
    }

    override fun insertRoot(newRoot: StepSpecification<*, *, *>, rootToShift: StepSpecification<*, *, *>) {
        newRoot.directedAcyclicGraphId = dagId
        delegated.insertRoot(newRoot, rootToShift)
    }

    override fun registerNext(previousStep: StepSpecification<*, *, *>, nextStep: StepSpecification<*, *, *>) {
        delegated.registerNext(previousStep, nextStep)
    }

    override fun register(step: StepSpecification<*, *, *>) {
        delegated.register(step)
    }
}
