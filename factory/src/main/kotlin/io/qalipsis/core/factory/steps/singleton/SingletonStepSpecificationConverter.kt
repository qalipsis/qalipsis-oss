package io.qalipsis.core.factory.steps.singleton

import io.qalipsis.api.Executors
import io.qalipsis.api.annotations.StepConverter
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.steps.SingletonStepSpecification
import io.qalipsis.api.steps.SingletonType
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepSpecification
import io.qalipsis.api.steps.StepSpecificationConverter
import io.qalipsis.api.steps.StepSpecificationDecoratorConverter
import io.qalipsis.core.factory.steps.topicrelatedsteps.TopicBuilder
import io.qalipsis.core.factory.steps.topicrelatedsteps.TopicConfiguration
import io.qalipsis.core.factory.steps.topicrelatedsteps.TopicDataPushStep
import io.qalipsis.core.factory.steps.topicrelatedsteps.TopicMirrorStep
import io.qalipsis.core.factory.steps.topicrelatedsteps.TopicType
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineScope

/**
 * Decorator of [SingletonStepSpecification] by adding it a [TopicMirrorStep] as next and converter from
 * [SingletonProxyStepSpecification] to [SingletonProxyStep].
 *
 * @author Eric Jessé
 */
@StepConverter
internal class SingletonStepSpecificationConverter(
    private val idGenerator: IdGenerator,
    @Named(Executors.CAMPAIGN_EXECUTOR_NAME) private val coroutineScope: CoroutineScope
) : StepSpecificationDecoratorConverter<StepSpecification<*, *, *>>(),
    StepSpecificationConverter<SingletonProxyStepSpecification<*>> {

    override val order: Int
        get() = 100

    override suspend fun decorate(creationContext: StepCreationContext<StepSpecification<*, *, *>>) {
        val spec = creationContext.stepSpecification
        if ((spec as? SingletonStepSpecification)?.isReallySingleton == true && spec.nextSteps.isNotEmpty()) {
            @Suppress("UNCHECKED_CAST")
            val decoratedStep = creationContext.createdStep as Step<Any?, Any?>

            // In order to match all the distribution use cases, a topic is used, which is fed with the data coming
            // from outputStep.
            val singletonConfig = spec.singletonConfiguration
            val topicType = when (singletonConfig.type) {
                SingletonType.UNICAST -> TopicType.UNICAST
                SingletonType.BROADCAST -> TopicType.BROADCAST
                SingletonType.LOOP -> TopicType.LOOP
                else -> throw IllegalArgumentException("Singletons of type ${singletonConfig.type} are not supported")
            }
            val topicConfig = TopicConfiguration(topicType, singletonConfig.bufferSize, singletonConfig.idleTimeout)

            // Topic to transport the data from the singleton step to the [SingletonProxyStep], via a [TopicMirrorStep].
            val topic: Topic<Any?> = TopicBuilder.build(topicConfig)

            // A step is added as output to forward the data to the topic.
            val consumer = TopicMirrorStep<Any?, Any?>(idGenerator.short(), topic)
            decoratedStep.addNext(consumer)
            // No other next step can be added to a singleton step.
            creationContext.createdStep(NoMoreNextStepDecorator(decoratedStep))

            // All the next step specifications have to be decorated by [SingletonProxyStepSpecification].
            spec.nextSteps.replaceAll {
                // All the next steps are wrapped so that they can receive the data from the topic.
                if (it.name.isBlank()) {
                    // Create a name here to identify replaced steps.
                    it.name = idGenerator.short()
                }
                @Suppress("UNCHECKED_CAST")
                SingletonProxyStepSpecification(it.name, it as StepSpecification<Any?, *, *>, topic)
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
            SingletonProxyStep("${spec.name}-singleton-proxy-${idGenerator.short()}", spec.topic)
        } else {
            // This DAG is not receiving any minion, so the next step has to be forced to be executed.
            TopicDataPushStep(
                "${spec.name}-topic-data-push-${idGenerator.short()}",
                spec.singletonStepName,
                spec.topic,
                coroutineScope = coroutineScope
            )
        }
        creationContext.createdStep(step)
    }

}
