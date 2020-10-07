package io.qalipsis.core.factories.steps.leftjoin

import cool.graph.cuid.Cuid
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.CorrelationRecord
import io.qalipsis.api.context.StepId
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.messaging.broadcastTopic
import io.qalipsis.api.steps.LeftJoinStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factories.steps.singleton.NoMoreNextStepDecorator
import io.qalipsis.core.factories.steps.topicmirror.TopicMirrorStep

/**
 * [StepSpecificationConverter] from [LeftJoinStepSpecification] to [LeftJoinStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class LeftJoinStepSpecificationConverter : StepSpecificationConverter<LeftJoinStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is LeftJoinStepSpecification<*, *>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<LeftJoinStepSpecification<*, *>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as LeftJoinStepSpecification<I, O>
        if (!creationContext.scenarioSpecification.exists(spec.secondaryStepName)) {
            throw InvalidSpecificationException(
                    "The dependency step ${spec.secondaryStepName} of ${spec.name ?: "Unknown"} of type CorrelationStep does not exist in the scenario")
        }

        // The secondary step to correlate is decorated in order to transfer its output to the newly created step.
        val (secondaryStep, _) = requireNotNull(creationContext.directedAcyclicGraph.scenario.findStep(
                spec.secondaryStepName)) { "Step ${spec.secondaryStepName} could not be found" }

        val topic = broadcastTopic<CorrelationRecord<*>>()

        // A step is added as output to forward the data to the topic.
        val consumer = TopicMirrorStep<O, CorrelationRecord<*>>(Cuid.createCuid(), topic, { _, value -> value != null },
                { context, value -> CorrelationRecord(context.minionId, context.stepId, value) })

        if (secondaryStep is NoMoreNextStepDecorator<*, *>) {
            secondaryStep.decorated.addNext(consumer)
        } else {
            secondaryStep.addNext(consumer)
        }

        // For now, only two-source left join is supported.
        @Suppress("UNCHECKED_CAST")
        val outputSupplier: (I, Map<StepId, Any?>) -> O = { left, right -> (left to right.values.first()) as O }

        @Suppress("UNCHECKED_CAST")
        val step = LeftJoinStep(spec.name ?: Cuid.createCuid(), spec.primaryKeyExtractor,
                listOf(RightCorrelation(secondaryStep.id, topic as Topic<CorrelationRecord<Any>>,
                        spec.secondaryKeyExtractor)), spec.cacheTimeout, outputSupplier)
        creationContext.createdStep(step)
    }

}
