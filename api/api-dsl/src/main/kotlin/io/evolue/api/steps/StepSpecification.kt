package io.evolue.api.steps

import io.evolue.api.context.DirectedAcyclicGraphId
import io.evolue.api.context.StepName
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.scenario.MutableScenarioSpecification
import io.evolue.api.scenario.ScenarioSpecification
import java.time.Duration
import javax.validation.constraints.NotNull
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
    var name: StepName?

    /**
     * The parent [ScenarioSpecification] to which the [StepSpecification] belongs.
     */
    var scenario: @NotNull MutableScenarioSpecification?

    /**
     * ID of the directed acyclic graph attached to the step.
     */
    var directedAcyclicGraphId: @NotNull DirectedAcyclicGraphId?

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
     * The [RetryPolicy] to apply when using the step, if it requires one.
     */
    val retryPolicy: RetryPolicy?

    /**
     * Collection of [StepSpecification]s to create the sibling [io.evolue.api.steps.Step]s.
     */
    val nextSteps: MutableList<StepSpecification<*, *, *>>

    /**
     * Add the [StepSpecification] as next step of the current one, declare it in the scenario and assign a relevant [StepSpecification.directedAcyclicGraphId].
     */
    fun add(step: StepSpecification<*, *, *>)

    /**
     * Add several next steps to run concurrently.
     *
     * Example:
     *
     * myStep.all{
     *   assert{...}.filter{}
     *   map{...}.validate{}.all {
     *      ...
     *   }
     * }
     */
    fun all(block: SELF.() -> Unit)

}
