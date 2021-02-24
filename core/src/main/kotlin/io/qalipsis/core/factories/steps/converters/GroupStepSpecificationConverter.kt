package io.qalipsis.core.factories.steps.converters

import cool.graph.cuid.Cuid
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.*
import io.qalipsis.core.factories.steps.GroupStep
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.ConcurrentHashMap

/**
 * [StepSpecificationConverter] to create a [GroupStep] from a set of [GroupStepStartSpecification].
 *
 * @author Eric Jessé
 */
@ExperimentalCoroutinesApi
@StepConverter
internal class GroupStepSpecificationConverter : StepSpecificationConverter<GroupStepSpecification<*, *>> {

    private val startStepsById = ConcurrentHashMap<String, GroupStep<*, *>>()

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is GroupStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<GroupStepSpecification<*, *>>) {
        @Suppress("UNCHECKED_CAST")
        val step = when (val spec = creationContext.stepSpecification) {
            is GroupStepStartSpecification<*> -> convertGroupStartBoundary<I, O>(spec as GroupStepStartSpecification<I>,
                spec.retryPolicy ?: creationContext.directedAcyclicGraph.scenario.defaultRetryPolicy)
            is GroupStepEndSpecification<*, *> -> convertGroupEndBoundary(spec)
            else -> throw IllegalArgumentException("A step specification of type ${spec::class} is not supported")
        }
        creationContext.createdStep(step)
    }

    /**
     * Creates the step for the group of steps.
     */
    private fun <I, O> convertGroupStartBoundary(spec: GroupStepStartSpecification<I>,
                                                 retryPolicy: RetryPolicy?): GroupStep<I, O> {
        if (spec.name.isNullOrBlank()) {
            spec.name = Cuid.createCuid()
        }
        return GroupStep<I, O>(spec.name!!, retryPolicy).also {
            startStepsById[it.id] = it
        }
    }

    /**
     * Creates the step for the end boundary of the step groups.
     */
    private fun <I, O> convertGroupEndBoundary(spec: GroupStepEndSpecification<I, O>): GroupEndProxy<O> {
        val groupEndBoundaryStep = startStepsById.remove(spec.start.name!!)!!
        // Creates the step to proxy the addition of next step to the group.
        @Suppress("UNCHECKED_CAST")
        return GroupEndProxy(groupEndBoundaryStep as GroupStep<*, O>)
    }

    /**
     * Transparent step, only there to connect the step after the group to the [GroupStep].
     *
     * This step is added as next of tail of the group, and simply forwards the input to the output.
     * When a next step is added, it is actually added as step of the [GroupStep].
     *
     * @author Eric Jessé
     */
    class GroupEndProxy<O>(private val start: GroupStep<*, O>) : AbstractStep<O, O>(Cuid.createCuid(), null) {

        override fun addNext(nextStep: Step<*, *>) {
            start.addNext(nextStep)
        }

        override suspend fun execute(context: StepContext<O, O>) {
            context.output.send(context.input.receive())
        }

    }

}
