package io.evolue.core.factories.steps.singleton

import cool.graph.cuid.Cuid
import io.evolue.api.annotations.StepConverter
import io.evolue.api.messaging.Topic
import io.evolue.api.steps.SingletonStepSpecification
import io.evolue.api.steps.SingletonType
import io.evolue.api.steps.Step
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.api.steps.StepSpecificationDecoratorConverter
import io.evolue.core.factories.steps.topicmirror.TopicBuilder
import io.evolue.core.factories.steps.topicmirror.TopicConfiguration
import io.evolue.core.factories.steps.topicmirror.TopicMirrorStep
import io.evolue.core.factories.steps.topicmirror.TopicType

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
        if (spec is SingletonStepSpecification<*, *, *> && spec.nextSteps.isNotEmpty()) {
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
                // If the wrapped step is itself a singleton, the SingletonProxyStep should loop endless on the first execution
                // to make sure that all the available data from the topic are consumed.
                @Suppress("UNCHECKED_CAST")
                SingletonProxyStepSpecification(it as StepSpecification<Any?, *, *>, topic)
            }
        }
    }

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is SingletonProxyStepSpecification<*>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<SingletonProxyStepSpecification<*>>) {
        val spec = creationContext.stepSpecification

        // The singleton step will makes the link between the data source step `outputStep` and the next steps
        // using the topic and subscriptions for each minion.
        val step = SingletonProxyStep(spec.name ?: Cuid.createCuid(), spec.topic)
        creationContext.createdStep(step)
    }

}
