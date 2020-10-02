package io.evolue.api.steps

import io.evolue.api.context.DirectedAcyclicGraphId
import io.evolue.api.context.StepName
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.scenario.MutableScenarioSpecification
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

    override var scenario: @NotNull MutableScenarioSpecification? = null

    override var directedAcyclicGraphId: @NotNull DirectedAcyclicGraphId? = null

    override var timeout: Duration? = null

    override var iterations: @Positive Long = 1

    override var iterationPeriods: Duration = Duration.ZERO

    override var retryPolicy: RetryPolicy? = null

    override val nextSteps = mutableListOf<StepSpecification<*, *, *>>()

    override fun configure(specification: SELF.() -> Unit): StepSpecification<INPUT, OUTPUT, *> {
        (this as SELF).specification()
        return this
    }

    override fun add(step: StepSpecification<*, *, *>) {
        scenario!!.register(step)
        // If either the current or next step is a singleton but not the other, a new DAG is built.
        if ((this is SingletonStepSpecification<*, *, *> && step !is SingletonStepSpecification<*, *, *>)
            || (this !is SingletonStepSpecification<*, *, *> && step is SingletonStepSpecification<*, *, *>)
        ) {
            step.directedAcyclicGraphId = scenario!!.getDagId()
        } else {
            step.directedAcyclicGraphId = this.directedAcyclicGraphId
        }
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
    override fun all(block: SELF.() -> Unit) {
        (this as SELF).block()
    }

    /**
     * Define the timeout of the step execution on a single context, in milliseconds.
     */
    override fun timeout(duration: Long) {
        timeout = Duration.ofMillis(duration)
    }

    /**
     * Define the individual retry strategy on the step. When none is set, the default one of the scenario is used.
     */
    override fun retry(retryPolicy: RetryPolicy) {
        this.retryPolicy = retryPolicy
    }

    override fun iterate(iterations: Long, period: Duration) {
        this.iterations = iterations
        this.iterationPeriods = period
    }

}
