package io.qalipsis.api.steps

import io.qalipsis.api.retry.RetryPolicy
import java.time.Duration

/**
 * Specification to configure a step specification.
 *
 * @param INPUT type of the data to process as input
 * @param OUTPUT type of the result forwarder to the output
 * @param SELF type of the step as visible to the scenario developer, it can be a concrete implementation or an interface, which will inherits from [ConfigurableStepSpecification].
 *
 * @author Eric Jessé
 */
interface ConfigurableStepSpecification<INPUT, OUTPUT, SELF : StepSpecification<INPUT, OUTPUT, SELF>> :
    StepSpecification<INPUT, OUTPUT, SELF> {

    /**
     * Configure the step with type-specific settings.
     */
    fun configure(specification: SELF.() -> Unit): StepSpecification<INPUT, OUTPUT, *> {
        val nameBeforeConfiguration = name
        @Suppress("UNCHECKED_CAST")
        (this as SELF).specification()

        // If the name was changed, the step has to be declared again in the scenario with its new name.
        if (name != nameBeforeConfiguration && scenario != null) {
            scenario!!.register(this)
        }
        return this
    }

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