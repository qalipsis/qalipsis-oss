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

package io.qalipsis.api.scenario

import io.aerisconsulting.catadioptre.KTestable
import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.executionprofile.ExecutionProfile
import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.SingletonStepSpecification
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.sync.ImmutableSlot
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Concrete implementation of all the interfaces relevant to define and use a scenario specification.
 *
 * @author Eric Jessé
 */
internal class ScenarioSpecificationImplementation(
    internal val name: String
) : StepSpecificationRegistry, ConfigurableScenarioSpecification, ConfiguredScenarioSpecification,
    ExecutionProfileSpecification, StartScenarioSpecification {

    override var minionsCount = 1

    override val rootSteps = concurrentList<StepSpecification<*, *, *>>()

    @KTestable
    private val registeredSteps = ConcurrentHashMap<String, ImmutableSlot<StepSpecification<*, *, *>>>()

    override var executionProfile: ExecutionProfile? = null

    override var retryPolicy: RetryPolicy? = null

    override var dagsCount = 0

    override var dagsUnderLoad = concurrentSet<DirectedAcyclicGraphName>()

    override var size: Long = 0

    override var description: String? = null

    override lateinit var version: String

    override lateinit var builtAt: Instant

    override fun add(step: StepSpecification<*, *, *>) {
        size++
        step.scenario = this
        rootSteps.add(step)
        register(step)
        if (step.directedAcyclicGraphName.isBlank() || step.directedAcyclicGraphName == "_") {
            step.directedAcyclicGraphName = this.buildDagId()
        }
    }

    override fun insertRoot(newRoot: StepSpecification<*, *, *>, rootToShift: StepSpecification<*, *, *>) {
        rootSteps.removeIf { it === rootToShift }
        newRoot.directedAcyclicGraphName = rootToShift.directedAcyclicGraphName
        add(newRoot)
        newRoot.add(rootToShift)
    }

    override fun registerNext(previousStep: StepSpecification<*, *, *>, nextStep: StepSpecification<*, *, *>) {
        register(nextStep)
        if (isDagNameBlankOrUndefined(nextStep.directedAcyclicGraphName)) {
            if (isNewDag(previousStep, nextStep)) {
                nextStep.directedAcyclicGraphName = buildDagId(previousStep.directedAcyclicGraphName)
            } else {
                nextStep.directedAcyclicGraphName = previousStep.directedAcyclicGraphName
            }
        }
    }

    /**
     *  Verifies whether the DAG should receive a new name or inherit from its parent.
     *  The underscore _ is a value used to force the creation of a new DAG and prevent from inheriting from the previous one.
     */
    private fun isDagNameBlankOrUndefined(dagName: String) = dagName.isBlank() || dagName == "_"

    /**
     *  Verifies whether the DAG should receive a new name.
     *  The underscore _ is a value used to force the creation of a new DAG and prevent from inheriting from the previous one.
     */
    private fun isDagNameUndefined(dagName: String) = dagName == "_"

    /**
     * Checks whether a new DAG has to be created for [nextStep].
     */
    private fun isNewDag(previousStep: StepSpecification<*, *, *>, nextStep: StepSpecification<*, *, *>): Boolean {
        return ((previousStep as? SingletonStepSpecification)?.isReallySingleton == true)
                || ((nextStep as? SingletonStepSpecification)?.isReallySingleton == true)
                || previousStep.tags != nextStep.tags
                || isDagNameUndefined(nextStep.directedAcyclicGraphName)
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

    override fun profile(specification: ExecutionProfileSpecification.() -> Unit) {
        this.specification()
    }

    override fun strategy(executionProfile: ExecutionProfile) {
        this.executionProfile = executionProfile
    }

    override fun retryPolicy(retryPolicy: RetryPolicy) {
        this.retryPolicy = retryPolicy
    }

    override fun buildDagId(parent: DirectedAcyclicGraphName?): DirectedAcyclicGraphName {
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
