package io.evolue.core.factory.steps.correlation

import com.google.common.annotations.VisibleForTesting
import io.evolue.api.context.CorrelationRecord
import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.Step
import io.evolue.api.steps.StepExecutor
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import javax.inject.Singleton

/**
 * Decorator for [Step]s considered to be "secondary" steps for [CorrelationStep]s.
 *
 * @author Eric Jessé
 */
@Singleton
internal class CorrelationOutputDecorator<I : Any?, O : Any?>(private val decorated: Step<I, O>) : Step<I, O>,
    StepExecutor {

    @VisibleForTesting
    internal val broadcastChannel = BroadcastChannel<CorrelationRecord<O>>(Channel.BUFFERED)

    override val id: StepId
        get() = decorated.id

    override var retryPolicy: RetryPolicy? = null

    override var next = decorated.next

    override suspend fun init() {
        decorated.init()
    }

    override suspend fun destroy() {
        decorated.destroy()
        broadcastChannel.close()
    }

    override suspend fun execute(context: StepContext<I, O>) {
        // Consume the data emitted by the decorated step to populate the broadcast channel for the correlation steps.
        GlobalScope.launch {
            for (record in context.output as Channel<O>) {
                broadcastChannel.send(
                    CorrelationRecord(context.minionId, id, record))
            }
        }
        executeStep(decorated, context)
    }

    /**
     * Open a subscription to the channel of data emitted by the decorated channel.
     * All the subscribers should be registered before the step is executed for the first time.
     */
    fun subscribe(): ReceiveChannel<CorrelationRecord<O>> = broadcastChannel.openSubscription()
}
