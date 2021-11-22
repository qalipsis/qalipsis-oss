package io.qalipsis.core.factory.steps.singleton

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepId
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.messaging.Topic
import io.qalipsis.api.steps.AbstractStep

/**
 * Step to provide data from an output [io.qalipsis.api.steps.Step] running in a singleton [Minion] to a standard [Minion].
 *
 * This step is part of a non-singleton DAG and acts as a proxy to buffer and deliver the records to the contexts.
 *
 * @author Eric Jessé
 */
internal class SingletonProxyStep<I>(
        id: StepId,

        /**
         * Topic used to provide data to all the minions running the step.
         */
        private val topic: Topic<I>,

        /**
         * Specification to filter the record from the remote step.
         *
         * By default, all the records are accepted.
         */
        private val filter: (suspend (remoteRecord: I) -> Boolean) = { _ -> true }

) : AbstractStep<I, I>(id, null) {

    override suspend fun destroy() {
        topic.close()
    }

    override suspend fun execute(context: StepContext<I, I>) {
        val valueFromTopic = topic.subscribe("${context.minionId}-${context.stepId}").pollValue()
        if (filter(valueFromTopic)) {
            context.send(valueFromTopic)
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
