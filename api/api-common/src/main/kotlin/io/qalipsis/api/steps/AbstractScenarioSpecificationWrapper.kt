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
import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry

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
abstract class AbstractScenarioSpecificationWrapper(scenario: ScenarioSpecification) : StepSpecificationRegistry,
    ScenarioSpecification by scenario {

    private val wrappedScenario = scenario as StepSpecificationRegistry

    override val rootSteps: List<StepSpecification<*, *, *>>
        get() = wrappedScenario.rootSteps

    override val dagsUnderLoad: Collection<DirectedAcyclicGraphName>
        get() = wrappedScenario.dagsUnderLoad

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

    override fun buildDagId(parent: DirectedAcyclicGraphName?): DirectedAcyclicGraphName {
        return wrappedScenario.buildDagId()
    }

    override fun insertRoot(newRoot: StepSpecification<*, *, *>, rootToShift: StepSpecification<*, *, *>) {
        return wrappedScenario.insertRoot(newRoot, rootToShift)
    }
}
