package io.qalipsis.core.factory.steps.singleton

import io.qalipsis.api.context.DirectedAcyclicGraphName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.steps.StepReportingSpecification
import io.qalipsis.api.steps.StepSpecification
import java.time.Duration
import javax.validation.constraints.Positive

/**
 * Specification for a [io.qalipsis.api.steps.Step] running behind a singleton.
 *
 *
 * @property topic topic to transport the data from the singleton step to another one
 * @param T type ot of the output of the decorated step.
 *
 * @author Eric Jessé
 */
internal open class SingletonProxyStepSpecification<T>(
    val singletonStepName: StepName,
    val next: StepSpecification<T, *, *>,
    val topic: Topic<T>,
) : StepSpecification<T, T, SingletonProxyStepSpecification<T>> {

    override var name: StepName = singletonStepName

    override var scenario: StepSpecificationRegistry = next.scenario

    override var timeout: Duration? = null

    @field:Positive
    override var iterations: Long = 0

    override var iterationPeriods: Duration = Duration.ZERO

    override var retryPolicy: RetryPolicy? = null

    override var directedAcyclicGraphName: DirectedAcyclicGraphName = next.directedAcyclicGraphName

    override val nextSteps: MutableList<StepSpecification<*, *, *>>
        get() = mutableListOf(next)

    override var tags = mutableMapOf<String, String>()

    override fun split(block: SingletonProxyStepSpecification<T>.() -> Unit): SingletonProxyStepSpecification<T> {
        // Nothing to do.
        return this
    }

    override fun add(step: StepSpecification<*, *, *>) {
        // Nothing to do.
    }

    override var reporting: StepReportingSpecification = StepReportingSpecification()

    override fun tag(tags: Map<String, String>) {
        this.tags.clear()
        this.tags += tags
    }
}
