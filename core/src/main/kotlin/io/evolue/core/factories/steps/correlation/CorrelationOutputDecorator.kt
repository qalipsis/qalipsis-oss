package io.evolue.core.factories.steps.correlation

import io.evolue.api.annotations.StepDecorator
import io.evolue.api.context.CorrelationRecord
import io.evolue.api.context.StepContext
import io.evolue.api.messaging.Topic
import io.evolue.api.steps.Step
import io.evolue.core.factories.steps.decorators.OutputTopicStepDecorator

/**
 * Decorator for [Step]s considered to be "secondary" steps for [CorrelationStep]s. Those steps provide records
 * that have to be sent to teh correlation step, that put them into a cache for later use.
 *
 * @author Eric Jessé
 */
@StepDecorator
internal class CorrelationOutputDecorator<I : Any?, O : Any?>(
        decorated: Step<I, O>,
        topic: Topic<CorrelationRecord<O>>
) : OutputTopicStepDecorator<I, O>(decorated, topic) {

    override fun shouldProduce(context: StepContext<I, O>, value: Any?) = value != null

    override fun wrap(context: StepContext<I, O>, value: Any?) =
        CorrelationRecord(context.minionId, context.stepId, value as O)
}
