package io.evolue.core.factory.steps.singleton

import cool.graph.cuid.Cuid
import io.evolue.api.messaging.TopicMode
import io.evolue.api.messaging.topic
import io.evolue.api.steps.SingletonStepSpecification
import io.evolue.api.steps.SingletonType
import io.evolue.api.steps.Step
import io.evolue.api.steps.StepCreationContext
import io.evolue.api.steps.StepSpecification
import io.evolue.api.steps.StepSpecificationConverter
import io.evolue.api.steps.StepSpecificationDecoratorConverter

/**
 * Decorator of [SingletonStepSpecification] by [SingletonOutputDecorator] and converter from
 * [SingletonProxyStepSpecification] to [SingletonProxyStep].
 *
 * @author Eric Jessé
 */
internal class SingletonStepSpecificationConverter :
    StepSpecificationDecoratorConverter<StepSpecification<*, *, *>>(),
    StepSpecificationConverter<SingletonProxyStepSpecification<*>> {

    override val order: Int
        get() = 100

    override suspend fun <I, O> decorate(creationContext: StepCreationContext<StepSpecification<*, *, *>>,
                                         decoratedStep: Step<I, O>): Step<*, *> {
        val spec = creationContext.stepSpecification
        return if (spec is SingletonStepSpecification<*, *, *>) {
            // The actual step is decorated with a SingletonOutputDecorator.
            val outputStep = SingletonOutputDecorator(decoratedStep)
            // All the next step specifications have to be decorated by [SingletonProxyStepSpecification].
            spec.nextSteps.replaceAll {
                SingletonProxyStepSpecification(
                    it as StepSpecification<O, *, *>,
                    outputStep,
                    spec.singletonType,
                    spec.bufferSize,
                    spec.idleTimeout,
                    spec.fromBeginning
                )
            }
            outputStep
        } else {
            decoratedStep
        }
    }

    override fun support(stepSpecification: StepSpecification<*, *, *>): Boolean {
        return stepSpecification is SingletonProxyStepSpecification<*>
    }

    override suspend fun <I, O> convert(creationContext: StepCreationContext<SingletonProxyStepSpecification<*>>) {
        val spec = creationContext.stepSpecification as SingletonProxyStepSpecification<I>
        val outputStep = spec.singletonOutputDecorator

        val topicMode = if (spec.singletonType == SingletonType.UNICAST) TopicMode.UNICAST else TopicMode.BROADCAST
        val topic = topic(topicMode, spec.bufferSize, spec.fromBeginning, spec.idleTimeout)

        val step = SingletonProxyStep(spec.name ?: Cuid.createCuid(), outputStep.subscribe(), topic)
        creationContext.createdStep(step)
    }

}