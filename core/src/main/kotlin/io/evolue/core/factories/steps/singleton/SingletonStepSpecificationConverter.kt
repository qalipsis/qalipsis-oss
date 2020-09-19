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
import io.evolue.core.factories.steps.decorators.OutputTopicStepDecorator
import io.evolue.core.factories.steps.decorators.TopicBuilder
import io.evolue.core.factories.steps.decorators.TopicConfiguration
import io.evolue.core.factories.steps.decorators.TopicType

/**
 * Decorator of [SingletonStepSpecification] by [OutputTopicStepDecorator] and converter from
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
        if (spec is SingletonStepSpecification<*, *, *>) {
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
            val topic: Topic<Any?> = TopicBuilder.build(topicConfig)

            // The actual step is decorated with a SingletonOutputDecorator.
            val outputStep = OutputTopicStepDecorator(decoratedStep, topic)
            // All the next step specifications have to be decorated by [SingletonProxyStepSpecification].
            spec.nextSteps.replaceAll {
                SingletonProxyStepSpecification(it as StepSpecification<Any?, *, *>, topic)
            }
            creationContext.createdStep(outputStep)
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
