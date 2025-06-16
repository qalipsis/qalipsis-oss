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
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.scenario.ScenarioSpecification
import io.qalipsis.api.scenario.StepSpecificationRegistry
import java.time.Duration
import javax.validation.constraints.Positive

/**
 * Generic specification for the step specifications. The actual operations are added by extensions.
 *
 * @param INPUT type of the data to process as input
 * @param OUTPUT type of the result forwarder to the output
 * @param SELF type of the step as visible to the scenario developer, it can be a concrete implementation or an interface
 *
 * @author Eric Jessé
 */
interface StepSpecification<INPUT, OUTPUT, SELF : StepSpecification<INPUT, OUTPUT, SELF>> {

    /**
     * Name of the step (default is assigned at runtime).
     */
    var name: StepName

    /**
     * The parent [ScenarioSpecification] to which the [StepSpecification] belongs.
     * The scenario has to be assigned before the name is set.
     */
    var scenario: StepSpecificationRegistry

    /**
     * ID of the directed acyclic graph attached to the step.
     */
    var directedAcyclicGraphName: DirectedAcyclicGraphName

    /**
     * Defines the delay after which a non completed execution triggers a failure.
     */
    val timeout: Duration?

    /**
     * Defines the number of iterations, limited to the capacity of the source to provide data, if any (default is 1).
     */
    val iterations: @Positive Long

    /**
     * Defines the delay between each iteration (default is [Duration.ZERO]).
     */
    val iterationPeriods: Duration

    /**
     * When the step has to be repeated via several iterations, specify whether an execution should stop the iterations.
     */
    val stopIterationsOnError: Boolean

    /**
     * The [RetryPolicy] to apply when using the step, if it requires one.
     */
    val retryPolicy: RetryPolicy?

    /**
     * Configuration of the reporting for the step.
     */
    var reporting: StepReportingSpecification

    /**
     * Tags of the factories where the step can be executed.
     */
    val tags: Map<String, String>

    /**
     * Collection of [StepSpecification]s to create the sibling [io.qalipsis.api.steps.Step]s.
     */
    val nextSteps: MutableList<StepSpecification<*, *, *>>

    /**
     * Adds the [StepSpecification] as next step of the current one, declare it in the scenario and assign a relevant [StepSpecification.directedAcyclicGraphName].
     */
    fun add(step: StepSpecification<*, *, *>)

    /**
     * Specifies the tags to additionally describe the steps.
     */
    fun tag(vararg tags: Pair<String, String>) = tag(tags.toMap())

    /**
     * Specifies the tags to additionally describe the steps.
     */
    fun tag(tags: Map<String, String>)

    /**
     * Add several next steps to run concurrently.
     *
     * Example:
     * ```
     * myStep.split{
     *   assert{...}.filter{}
     *   map{...}.validate{}.split {
     *      ...
     *   }
     * }.map{...}
     * ```
     */
    fun split(block: StepSpecification<INPUT, OUTPUT, *>.() -> Unit): SELF {
        @Suppress("UNCHECKED_CAST")
        SplitStepSpecificationDecorator(this).block()
        return this as SELF
    }

}
