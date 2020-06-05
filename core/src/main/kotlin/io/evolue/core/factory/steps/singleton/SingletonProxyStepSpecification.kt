package io.evolue.core.factory.steps.singleton

import io.evolue.api.context.DirectedAcyclicGraphId
import io.evolue.api.context.StepName
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.scenario.ScenarioSpecification
import io.evolue.api.steps.SingletonType
import io.evolue.api.steps.StepSpecification
import java.time.Duration

/**
 * Specification for a [io.evolue.api.steps.Step] running behind a singleton.
 *
 * @author Eric Jessé
 */
internal data class SingletonProxyStepSpecification<INPUT>(

    val next: StepSpecification<INPUT, *, *>,
    /**
     * Singleton step proving the data to this one.
     */
    internal val singletonOutputDecorator: SingletonOutputDecorator<*, INPUT>,
    /**
     * Kind of data providing: unicast or broadcast.
     */
    val singletonType: SingletonType,
    /**
     * Size of the buffer to keep the received records.
     */
    val bufferSize: Int,
    /**
     * Time to idle of a subscription. Once a idle subscription passed this duration, it is automatically cancelled.
     */
    val idleTimeout: Duration,
    /**
     * Defines if the first subscriber will receive all the records from the beginning or only from now on.
     * When set to {@code false}, records before the first subscription are simply discarded.
     */
    val fromBeginning: Boolean
) : StepSpecification<INPUT, INPUT, SingletonProxyStepSpecification<INPUT>> {

    override var name: StepName? = null

    override var scenario: ScenarioSpecification? = next.scenario

    override var timeout: Duration? = null

    override var iterations: Long = 0

    override var iterationPeriods: Duration = Duration.ZERO

    override var retryPolicy: RetryPolicy? = null

    override var directedAcyclicGraphId: DirectedAcyclicGraphId? = next.directedAcyclicGraphId

    override val nextSteps: MutableList<StepSpecification<*, *, *>>
        get() = mutableListOf(next)

    override fun all(block: SingletonProxyStepSpecification<INPUT>.() -> Unit) {
        // Nothing to do.
    }


    override fun add(step: StepSpecification<*, *, *>) {
        // Nothing to do.
    }
}
