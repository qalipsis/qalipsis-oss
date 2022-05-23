package io.qalipsis.api.steps

import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.scenario.StepSpecificationRegistry
import java.time.Duration
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Positive
import javax.validation.constraints.PositiveOrZero

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
    override var directedAcyclicGraphName: DirectedAcyclicGraphName = ""

    override var timeout: Duration? = null

    @field:Positive
    override var iterations: Long = 1

    @field:PositiveOrZero
    override var iterationPeriods: Duration = Duration.ZERO

    override var retryPolicy: RetryPolicy? = null

    override val nextSteps = mutableListOf<StepSpecification<*, *, *>>()

    override var reporting = StepReportingSpecification()

    private var tagsSet = false

    override var tags: Map<String, String> = emptyMap()

    override fun timeout(duration: Duration) {
        timeout = duration
    }

    override fun retry(retryPolicy: RetryPolicy) {
        this.retryPolicy = retryPolicy
    }

    override fun iterate(iterations: Long, period: Duration) {
        this.iterations = iterations
        this.iterationPeriods = period
    }

    override fun add(step: StepSpecification<*, *, *>) {
        // If no selector is specified on the next step, they are inherited.
        if (step.tags.isEmpty() || (step as? AbstractStepSpecification<*, *, *>)?.tagsSet != true) {
            step.tag(tags)
        }
        nextSteps.add(step)
        scenario.registerNext(this, step)
    }

    override fun report(specification: StepReportingSpecification.() -> Unit) {
        reporting.specification()
    }

    override fun tag(tags: Map<String, String>) {
        tagsSet = true
        this.tags = tags
    }
}
