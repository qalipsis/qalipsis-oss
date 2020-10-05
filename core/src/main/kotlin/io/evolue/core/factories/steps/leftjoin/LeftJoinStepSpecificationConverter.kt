package io.evolue.core.factories.steps.leftjoin

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.context.CorrelationRecord
import io.evolue.api.context.StepId
import io.evolue.api.exceptions.InvalidSpecificationException
import io.evolue.api.messaging.Topic
import io.evolue.api.messaging.broadcastTopic
import io.evolue.api.steps.LeftJoinStepSpecification
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.core.factories.steps.singleton.NoMoreNextStepDecorator
import io.evolue.core.factories.steps.topicmirror.TopicMirrorStep

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
