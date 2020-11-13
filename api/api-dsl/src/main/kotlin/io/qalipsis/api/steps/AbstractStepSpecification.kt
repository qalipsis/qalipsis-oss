package io.qalipsis.api.steps

import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.StepName
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.scenario.StepSpecificationRegistry
import java.time.Duration
import javax.validation.constraints.NotNull
import javax.validation.constraints.Positive

/**
 * Generic specification for the step specifications. The actual operations are added by extensions.
 *
 * @param INPUT type of the data to process as input
 * @param OUTPUT type of the result forwarder to the output
 * @param SELF type of the step as visible to the scenario developer, it can be a concrete implementation or an interface, which will inherits from [ConfigurableStepSpecification].
 *
 * @author Eric Jessé
 */
abstract class AbstractStepSpecification<INPUT, OUTPUT, SELF : StepSpecification<INPUT, OUTPUT, SELF>> :
    ConfigurableStepSpecification<INPUT, OUTPUT, SELF> {

    override var name: StepName? = null
        set(value) {
            field = value
            // When the name is muted after the step was added to the scenario, the step has to be registered
            // on the scenario once again.
            scenario?.register(this)
        }

    override var scenario: @NotNull StepSpecificationRegistry? = null

    override var directedAcyclicGraphId: @NotNull DirectedAcyclicGraphId? = null

    override var timeout: Duration? = null

    override var iterations: @Positive Long = 1

    override var iterationPeriods: Duration = Duration.ZERO

    override var retryPolicy: RetryPolicy? = null

    override val nextSteps = mutableListOf<StepSpecification<*, *, *>>()

    /**
     * Defines the timeout of the step execution on a single context, in milliseconds.
     */
    override fun timeout(duration: Long) {
        timeout = Duration.ofMillis(duration)
    }

    /**
     * Defines the individual retry strategy on the step. When none is set, the default one of the scenario is used.
     */
    override fun retry(retryPolicy: RetryPolicy) {
        this.retryPolicy = retryPolicy
    }

    override fun iterate(iterations: Long, period: Duration) {
        this.iterations = iterations
        this.iterationPeriods = period
    }

    override fun add(step: StepSpecification<*, *, *>) {
        nextSteps.add(step)
        scenario!!.registerNext(this, step)
    }
}
