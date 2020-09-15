package io.evolue.core.factory.steps.decorators

import io.evolue.api.annotations.StepDecorator
import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.messaging.Topic
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.Step
import io.evolue.api.steps.StepExecutor
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Decorator for [Step]s considered to be outputs of singleton.
 *
 * A [OutputTopicStepDecorator] acts as a data provider for steps of real minions.
 *
 * @author Eric Jessé
 */
@StepDecorator
internal open class OutputTopicStepDecorator<I, O>(
        private val decorated: Step<I, O>,
        private val topic: Topic<*>
) : Step<I, O>, StepExecutor {

    private var consumptionJob: Job? = null

    override val id: StepId
        get() = decorated.id

    override var retryPolicy: RetryPolicy? = null

    override var next = mutableListOf<Step<O, *>>()

    override suspend fun init() {
        decorated.init()
    }

    override suspend fun destroy() {
        decorated.destroy()
        consumptionJob?.cancel()
        topic.close()
    }

    override suspend fun execute(context: StepContext<I, O>) {
        if (consumptionJob == null) {
            // Consume the data emitted by the decorated step to populate the broadcast channel to the proxy steps.
            consumptionJob = GlobalScope.launch {
                val actualTopic = topic as Topic<Any?>
                for (value in context.output as Channel<O>) {
                    if (shouldProduce(context, value)) {
                        actualTopic.produceValue(wrap(context, value))
                    }
                }
                topic.complete()
            }
            executeStep(decorated, context)
        } else {
            error("The decorated step is already running")
        }
    }

    protected open fun shouldProduce(context: StepContext<I, O>, value: Any?) = true

    protected open fun wrap(context: StepContext<I, O>, value: Any?) = value
}
