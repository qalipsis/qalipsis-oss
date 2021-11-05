package io.qalipsis.core.factories.steps.join

import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.context.CorrelationRecord
import io.qalipsis.api.context.StepId
import io.qalipsis.api.exceptions.InvalidSpecificationException
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.messaging.broadcastTopic
import io.qalipsis.api.steps.InnerJoinStepSpecification
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.core.factories.steps.singleton.NoMoreNextStepDecorator
import io.qalipsis.core.factories.steps.topicrelatedsteps.TopicMirrorStep
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineScope

/**
 * [StepSpecificationConverter] from [InnerJoinStepSpecification] to [InnerJoinStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class InnerJoinStepSpecificationConverter(
    private val idGenerator: IdGenerator,
    @Named(Executors.CAMPAIGN_EXECUTOR_NAME) private val coroutineScope: CoroutineScope
) : StepSpecificationConverter<InnerJoinStepSpecification<*, *>> {

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is InnerJoinStepSpecification<*, *>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<InnerJoinStepSpecification<*, *>>) {
        @Suppress("UNCHECKED_CAST")
        val spec = creationContext.stepSpecification as InnerJoinStepSpecification<I, O>
        if (!creationContext.scenarioSpecification.exists(spec.secondaryStepName)) {
            throw InvalidSpecificationException(
                "The dependency step ${spec.secondaryStepName} of ${spec.name} of type CorrelationStep does not exist in the scenario"
            )
        }

        // The secondary step to correlate is decorated in order to transfer its output to the newly created step.
        val (secondaryStep, _) = requireNotNull(
            creationContext.directedAcyclicGraph.scenario.findStep(
                spec.secondaryStepName
            )
        ) { "Step ${spec.secondaryStepName} could not be found" }

        val topic = broadcastTopic<CorrelationRecord<*>>()

        // A step is added as output to forward the data to the topic.
        val consumer = TopicMirrorStep<O, CorrelationRecord<*>>(
            "${secondaryStep.id}-topic-mirror-step-${idGenerator.short()}",
            topic, { _, value -> value != null },
            { context, value -> CorrelationRecord(context.minionId, context.stepId, value) }
        )

        if (secondaryStep is NoMoreNextStepDecorator<*, *>) {
            secondaryStep.decorated.addNext(consumer)
        } else {
            secondaryStep.addNext(consumer)
        }

        // For now, only two-source left join is supported.
        @Suppress("UNCHECKED_CAST")
        val outputSupplier: (I, Map<StepId, Any?>) -> O = { left, right -> (left to right.values.first()) as O }

        @Suppress("UNCHECKED_CAST")
        val step = InnerJoinStep(
            spec.name, coroutineScope, spec.primaryKeyExtractor,
            listOf(
                RightCorrelation(secondaryStep.id, topic as Topic<CorrelationRecord<Any>>, spec.secondaryKeyExtractor)
            ), spec.cacheTimeout, outputSupplier
        )
        creationContext.createdStep(step)
    }

}
