package io.qalipsis.api.steps

import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.StepName
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.scenario.StepSpecificationRegistry
import java.time.Duration
import javax.validation.constraints.NotBlank
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

    override var name: StepName = ""

    @field:NotNull
    override lateinit var scenario: StepSpecificationRegistry

    @field:NotBlank
    override var directedAcyclicGraphId: DirectedAcyclicGraphId = ""

    override var timeout: Duration? = null

    @field:Positive
    override var iterations: Long = 1

    override var iterationPeriods: Duration = Duration.ZERO

    override var retryPolicy: RetryPolicy? = null

    override val nextSteps = mutableListOf<StepSpecification<*, *, *>>()

    override var reporting = StepReportingSpecification()

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
        scenario.registerNext(this, step)
    }

    override fun report(specification: StepReportingSpecification.() -> Unit) {
        reporting.specification()
    }
}
