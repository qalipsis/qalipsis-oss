package io.qalipsis.core.factories.steps.singleton

import cool.graph.cuid.Cuid
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.steps.SingletonStepSpecification
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.StepSpecificationDecoratorConverter
import io.qalipsis.core.factories.steps.topicrelatedsteps.TopicBuilder
import io.qalipsis.core.factories.steps.topicrelatedsteps.TopicConfiguration
import io.qalipsis.core.factories.steps.topicrelatedsteps.TopicDataPushStep
import io.qalipsis.core.factories.steps.topicrelatedsteps.TopicMirrorStep
import io.qalipsis.core.factories.steps.topicrelatedsteps.TopicType

/**
 * Decorator of [SingletonStepSpecification] by adding it a [TopicMirrorStep] as next and converter from
 * [SingletonProxyStepSpecification] to [SingletonProxyStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class SingletonStepSpecificationConverter :
    StepSpecificationDecoratorConverter<StepSpecification<*, *, *>>(),
    StepSpecificationConverter<SingletonProxyStepSpecification<*>> {

    override val order: Int
        get() = 100

    override suspend fun decorate(creationContext: StepCreationContext<StepSpecification<*, *, *>>) {
        val spec = creationContext.stepSpecification
        if (spec is SingletonStepSpecification && spec.nextSteps.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST")
            val decoratedStep = creationContext.createdStep as Step<Any?, Any?>

            // In order to match all the distribution use cases, a topic is used, which is fed with the data coming
            // from outputStep.
            val singletonConfig = spec.singletonConfiguration
            val topicType = when (singletonConfig.type) {
                SingletonType.UNICAST -> TopicType.UNICAST
                SingletonType.BROADCAST -> TopicType.BROADCAST
                SingletonType.LOOP -> TopicType.LOOP
            }
            val topicConfig = TopicConfiguration(topicType, singletonConfig.bufferSize, singletonConfig.idleTimeout)

            // Topic to transport the data from the singleton step to the [SingletonProxyStep], via a [TopicMirrorStep].
            val topic: Topic<Any?> = TopicBuilder.build(topicConfig)

            // A step is added as output to forward the data to the topic.
            val consumer = TopicMirrorStep<Any?, Any?>(Cuid.createCuid(), topic)
            decoratedStep.addNext(consumer)
            // No other next step can be added to a singleton step.
            creationContext.createdStep(NoMoreNextStepDecorator(decoratedStep))

            // All the next step specifications have to be decorated by [SingletonProxyStepSpecification].
            spec.nextSteps.replaceAll {
                // All the next steps are wrapped so that they can receive the data from the topic.
                if (it.name.isNullOrBlank()) {
                    // Create a name here to
                    it.name = Cuid.createCuid()
                }
                @Suppress("UNCHECKED_CAST")
                SingletonProxyStepSpecification(it.name!!, it as StepSpecification<Any?, *, *>, topic)
            }
        }
    }

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is SingletonProxyStepSpecification<*>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<SingletonProxyStepSpecification<*>>) {
        val spec = creationContext.stepSpecification

        val step = if (creationContext.directedAcyclicGraph.isUnderLoad) {
            // When a minion is executing the SingletonProxyStep, a record will be polled from the topic if any.
            SingletonProxyStep("${spec.name}-singleton-proxy-${Cuid.createCuid()}", spec.topic)
        } else {
            // This DAG is not receiving any minion, so the next step has to be forced to be executed.
            TopicDataPushStep("${spec.name}-topic-data-push-${Cuid.createCuid()}", spec.singletonStepId, spec.topic)
        }
        creationContext.createdStep(step)
    }

}
