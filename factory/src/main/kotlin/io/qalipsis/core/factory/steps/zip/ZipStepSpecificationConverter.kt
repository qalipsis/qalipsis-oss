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

package io.qalipsis.core.factory.steps.zip

import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.CorrelationRecord
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.messaging.broadcastTopic
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.ZipStepSpecification
import io.qalipsis.core.factory.steps.singleton.NoMoreNextStepDecorator
import io.qalipsis.core.factory.steps.topicrelatedsteps.TopicMirrorStep
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineScope

/**
 * [StepSpecificationConverter] from [ZipStepSpecification] to [ZipStep].
 *
 * @author Polina Bril
 */
@StepConverter
class ZipStepSpecificationConverter(
    private val idGenerator: IdGenerator,
    @Named(Executors.CAMPAIGN_EXECUTOR_NAME) private val coroutineScope: CoroutineScope
) : StepSpecificationConverter<ZipStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is ZipStepSpecification<*, *>
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <I, O> convert(creationContext: StepCreationContext<ZipStepSpecification<*, *>>) {
        val spec = creationContext.stepSpecification as ZipStepSpecification<I, O>
        if (!creationContext.scenarioSpecification.exists(spec.secondaryStepName)) {
            throw InvalidSpecificationException(
                "The dependency step ${spec.secondaryStepName} of ${spec.name} of type CorrelationStep does not exist in the scenario"
            )
        }

        // The secondary step to unite is decorated in order to transfer its output to the newly created step.
        val (secondaryStep, _) = requireNotNull(
            creationContext.directedAcyclicGraph.scenario.findStep(
                spec.secondaryStepName
            )
        ) { "Step ${spec.secondaryStepName} could not be found" }

        val topic = createRightDataSupplier<O>(secondaryStep)

        val step = ZipStep<I, Pair<I, Any?>>(
            spec.name, coroutineScope,
            listOf(
                RightSource(secondaryStep.name, topic as Topic<CorrelationRecord<Any>>)
            )
        )
        creationContext.createdStep(step)
    }

    private fun <O> createRightDataSupplier(secondaryStep: Step<*, *>): Topic<CorrelationRecord<*>> {
        val topic = broadcastTopic<CorrelationRecord<*>>()
        // A step is added as output to forward the data to the topic.
        // Since the step is technical, its name is prefixed by 2 underscores.
        val dataSupplier = TopicMirrorStep<O, CorrelationRecord<*>>(
            "__${secondaryStep.name}-topic-mirror-step-${idGenerator.short()}",
            topic, { _, value -> value != null },
            { context, value -> CorrelationRecord(context.minionId, context.stepName, value) }
        )
        if (secondaryStep is NoMoreNextStepDecorator<*, *>) {
            secondaryStep.decorated.addNext(dataSupplier)
        } else {
            secondaryStep.addNext(dataSupplier)
        }
        return topic
    }
}
