package io.evolue.core.factory.steps.singleton

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.messaging.Topic
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.AbstractStep
import io.evolue.core.exceptions.NotInitializedStepException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

/**
 * Step to provide data from an output [io.evolue.api.steps.Step] running in a singleton
 * [io.evolue.core.factory.orchestration.MinionImpl] to the one of a standard [Minion].
 *
 * This step is part of a non-singleton DAG and acts as a proxy to buffer and deliver the records to the buffers.
 *
 * @author Eric Jessé
 */
internal class SingletonProxyStep<I>(
    id: StepId,

    retryPolicy: RetryPolicy?,

    /**
     * [ReceiveChannel] obtained from a [io.evolue.core.factory.steps.singleton.SingletonOutputDecorator] to forward the records.
     */
    private val subscriptionChannel: ReceiveChannel<I>,

    /**
     * Topic used to provide data to all the minions running the step.
     */
    private val topic: Topic,

    /**
     * Specification to filter the record from the remote step.
     *
     * By default, all the records are accepted.
     */
    private val filter: (suspend (remoteRecord: I) -> Boolean) = { _ -> true }

) : AbstractStep<I, I>(id, retryPolicy) {

    private lateinit var consumptionJob: Job

    private var initialized = false

    override suspend fun init() {
        // Coroutine to buffer the data coming from the remote job into the local topic.
        consumptionJob = GlobalScope.launch {
            log.debug("Starting the coroutine to buffer remote records")
            for (value in subscriptionChannel) {
                if (filter(value)) {
                    topic.produce(Topic.Record(value = value))
                }
            }
            log.debug("Leaving the coroutine to buffer remote records")
        }
        initialized = true
    }

    override suspend fun destroy() {
        subscriptionChannel.cancel()
        topic.close()
    }

    override suspend fun execute(context: StepContext<I, I>) {
        if (!initialized) throw NotInitializedStepException()
        context.output.send(topic.subscribe(context.minionId).pollValue() as I)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}