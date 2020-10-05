package io.evolue.core.factories.steps.topicmirror

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.messaging.Topic
import io.evolue.api.steps.AbstractStep
import io.evolue.api.steps.Step

/**
 * Step forwarding the input to a topic and the output.
 *
 * @property topic topic to forward the data to
 * @property predicate predicate to match to forward the data to the topic, default to always true
 * @property wrap converter from context and received value into the entity to send to the topic
 * @param I type of the input and output
 * @param T type of the entity sent to the topic
 *
 * @author Eric Jessé
 */
internal open class TopicMirrorStep<I, T>(
        id: StepId,
        private val topic: Topic<T>,
        private val predicate: (context: StepContext<I, I>, value: Any?) -> Boolean = { _, _ -> true },
        @Suppress(
                "UNCHECKED_CAST") private val wrap: (context: StepContext<I, I>, value: Any?) -> T = { _, value -> value as T }
) : AbstractStep<I, I>(id, null) {

    override fun addNext(nextStep: Step<*, *>) {
        // Do nothing, this step is connected to its next ones using the property topic.
        // It should not have any next step, otherwise there is a race to consume the output
        // between the topic feeder and the next steps.
    }

    override suspend fun destroy() {
        topic.close()
    }

    override suspend fun execute(context: StepContext<I, I>) {
        if (context.isTail) {
            topic.complete()
        } else {
            // Consume the data emitted by the decorated step to populate the broadcast channel to the proxy steps.
            val value = context.input.receive()
            if (predicate(context, value)) {
                log.trace("Forwarding one record")
                topic.produceValue(wrap(context, value))
            }
            context.output.send(value)
        }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
