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
abstract class StepSpecification<INPUT : Any?, OUTPUT : Any?, SELF : StepSpecification<INPUT, OUTPUT, SELF>> {

    /**
     * Name of the step (default is assigned at runtime).
     */
    var name: StepName? = null

    internal var scenario: ScenarioSpecification? = null

    /**
     * Defines the delay after which a non completed execution triggers a failure.
     */
    internal var timeout: Duration? = null

    /**
     * Defines the number of iterations, limited to the capacity of the source to provide data, if any (default is 1).
     */
    internal var iterations: Long = 1

    /**
     * Defines the delay between each iteration (default is [Duration.ZERO]).
     */
    internal var iterationPeriods: Duration = Duration.ZERO

    /**
     * Maximal number of successive failures when iterating before the overall failure and setting the context exhausted (default is 1).
     */
    internal var allowedFailuresOnIterations: Long = 1

    internal var retryPolicy: RetryPolicy? = null

    internal val nextSteps = mutableListOf<StepSpecification<*, *, *>>()

    open fun configure(specification: SELF.() -> Unit): StepSpecification<INPUT, OUTPUT, *> {
        (this as SELF).specification()
        return this
    }

    open fun add(step: StepSpecification<*, *, *>) {
        scenario?.register(step)
        nextSteps.add(step)
    }

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
    open fun all(block: SELF.() -> Unit) {
        (this as SELF).block()
    }

    /**
     * Define the timeout of the step execution on a single context, in milliseconds.
     */
    fun timeout(duration: Long) {
        timeout = Duration.ofMillis(duration)
    }

    /**
     * Define the individual retry strategy on the step. When none is set, the default one of the scenario is used.
     */
    fun retry(retryPolicy: RetryPolicy) {
        this.retryPolicy = retryPolicy
    }

    fun iterate(iterations: Long, period: Duration, allowedFailures: Long) {
        this.iterations = iterations
        this.iterationPeriods = period
        this.allowedFailuresOnIterations = allowedFailures
    }

}