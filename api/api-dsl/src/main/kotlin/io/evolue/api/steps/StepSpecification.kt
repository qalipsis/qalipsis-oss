package io.evolue.api.steps

import io.evolue.api.ScenarioSpecification
import io.evolue.api.context.StepName
import io.evolue.api.retry.RetryPolicy
import java.time.Duration

/**
 * Generic specification for the step specifications. The actual operations are added by extensions.
 *
 * @author Eric Jessé
 */
interface StepSpecification<INPUT : Any?, OUTPUT : Any?, SELF : StepSpecification<INPUT, OUTPUT, SELF>> {

    /**
     * Name of the step (default is assigned at runtime).
     */
    var name: StepName?

    /**
     * The parent [ScenarioSpecification] to which the [StepSpecification] belongs.
     */
    var scenario: ScenarioSpecification?

    /**
     * Defines the delay after which a non completed execution triggers a failure.
     */
    val timeout: Duration?

    /**
     * Defines the number of iterations, limited to the capacity of the source to provide data, if any (default is 1).
     */
    val iterations: Long

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
     * Add a new [StepSpecification] as root of the scenario.
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