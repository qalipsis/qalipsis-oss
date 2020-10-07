package io.qalipsis.core.factories.steps.singleton

import io.qalipsis.api.context.DirectedAcyclicGraphId
import io.qalipsis.api.context.StepName
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.scenario.MutableScenarioSpecification
import io.qalipsis.api.steps.StepSpecification
import java.time.Duration

/**
 * Specification for a [io.qalipsis.api.steps.Step] running behind a singleton.
 *
 * @param T type ot of the output of the decorated step.
 *
 * @author Eric Jessé
 */
internal open class SingletonProxyStepSpecification<T>(

        val next: StepSpecification<T, *, *>,

        /**
         * Configuration of the singleton.
         */
        val topic: Topic<T>,

        ) : StepSpecification<T, T, SingletonProxyStepSpecification<T>> {

    override var name: StepName? = null

    override var scenario: MutableScenarioSpecification? = next.scenario

    override var timeout: Duration? = null

    override var iterations: Long = 0

    override var iterationPeriods: Duration = Duration.ZERO

    override var retryPolicy: RetryPolicy? = null

    override var directedAcyclicGraphId: DirectedAcyclicGraphId? = next.directedAcyclicGraphId

    override val nextSteps: MutableList<StepSpecification<*, *, *>>
        get() = mutableListOf(next)

    override fun split(block: SingletonProxyStepSpecification<T>.() -> Unit) {
        // Nothing to do.
    }

    override fun add(step: StepSpecification<*, *, *>) {
        // Nothing to do.
    }
}