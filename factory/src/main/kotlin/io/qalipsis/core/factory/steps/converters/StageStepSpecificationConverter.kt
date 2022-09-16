/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.factory.steps.converters

import cool.graph.cuid.Cuid
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep
import io.qalipsis.api.steps.StageStepEndSpecification
import io.qalipsis.api.steps.StageStepSpecification
import io.qalipsis.api.steps.StageStepStartSpecification
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factory.steps.StageStep
import java.util.concurrent.ConcurrentHashMap

/**
 * [StepSpecificationConverter] to create a [StageStep] from a set of [StageStepStartSpecification].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class StageStepSpecificationConverter : StepSpecificationConverter<StageStepSpecification<*, *>> {

    private val startStepsById = ConcurrentHashMap<String, StageStep<*, *>>()

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is StageStepSpecification
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<StageStepSpecification<*, *>>) {
        @Suppress("UNCHECKED_CAST")
        val step = when (val spec = creationContext.stepSpecification) {
            is StageStepStartSpecification<*> -> convertGroupStartBoundary<I, O>(spec as StageStepStartSpecification<I>,
                spec.retryPolicy ?: creationContext.directedAcyclicGraph.scenario.defaultRetryPolicy)
            is StageStepEndSpecification<*, *> -> convertGroupEndBoundary(spec)
            else -> throw IllegalArgumentException("A step specification of type ${spec::class} is not supported")
        }
        creationContext.createdStep(step)
    }

    /**
     * Creates the step for the group of steps.
     */
    private fun <I, O> convertGroupStartBoundary(spec: StageStepStartSpecification<I>,
                                                 retryPolicy: RetryPolicy?): StageStep<I, O> {
        return StageStep<I, O>(spec.name, retryPolicy).also {
            startStepsById[it.name] = it
        }
    }

    /**
     * Creates the step for the end boundary of the step groups.
     */
    private fun <I, O> convertGroupEndBoundary(spec: StageStepEndSpecification<I, O>): GroupEndProxy<O> {
        val groupEndBoundaryStep = startStepsById.remove(spec.start.name)!!
        // Creates the step to proxy the addition of next step to the group.
        @Suppress("UNCHECKED_CAST")
        return GroupEndProxy(groupEndBoundaryStep as StageStep<*, O>)
    }

    /**
     * Transparent step, only there to connect the step after the group to the [StageStep].
     *
     * This step is added as next of tail of the group, and simply forwards the input to the output.
     * When a next step is added, it is actually added as step of the [StageStep].
     *
     * @author Eric Jessé
     */
    class GroupEndProxy<O>(private val start: StageStep<*, O>) : AbstractStep<O, O>(Cuid.createCuid(), null) {

        override fun addNext(nextStep: Step<*, *>) {
            start.addNext(nextStep)
        }

        override suspend fun execute(context: StepContext<O, O>) {
            context.send(context.receive())
        }

    }

}
