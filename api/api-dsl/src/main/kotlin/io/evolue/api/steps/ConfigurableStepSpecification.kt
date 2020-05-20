package io.evolue.api.steps

import io.evolue.api.retry.RetryPolicy
import java.time.Duration

/**
 * Specification to configure a step specification.
 *
 * @author Eric Jessé
 */
interface ConfigurableStepSpecification<INPUT : Any?, OUTPUT : Any?, SELF : StepSpecification<INPUT, OUTPUT, SELF>> :
    StepSpecification<INPUT, OUTPUT, SELF> {

    fun configure(specification: SELF.() -> Unit): StepSpecification<INPUT, OUTPUT, *>

    /**
     * Define the timeout of the step execution on a single context, in milliseconds.
     */
    fun timeout(duration: Long)

    /**
     * Define the individual retry strategy on the step. When none is set, the default one of the scenario is used.
     */
    fun retry(retryPolicy: RetryPolicy)

    /**
     * Define how many times and how often the step execution has to be repeated.
     */
    fun iterate(iterations: Long, period: Duration)
}