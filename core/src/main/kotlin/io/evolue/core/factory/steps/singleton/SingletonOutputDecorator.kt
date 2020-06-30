package io.evolue.core.factory.steps.singleton

import com.google.common.annotations.VisibleForTesting
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
 * Decorator for [Step]s considered to be outputs of singleton.
 *
 * A [SingletonOutputDecorator] acts as a data provider for steps of real minions.
 *
 * @author Eric Jessé
 */
@Singleton
internal class SingletonOutputDecorator<I, O>(private val decorated: Step<I, O>) : Step<I, O>, StepExecutor {

    @VisibleForTesting
    internal val broadcastChannel = BroadcastChannel<Any?>(Channel.BUFFERED)

    override val id: StepId
        get() = decorated.id

    override var retryPolicy: RetryPolicy? = null

    override var next = mutableListOf<Step<O, *>>()

    override suspend fun init() {
        decorated.init()
    }

    override suspend fun destroy() {
        decorated.destroy()
        broadcastChannel.close()
    }

    override suspend fun execute(context: StepContext<I, O>) {
        // Consume the data emitted by the decorated step to populate the broadcast channel to the proxy steps.
        GlobalScope.launch {
            for (record in context.output as Channel<O>) {
                broadcastChannel.send(record)
            }
        }
        executeStep(decorated, context)
    }

    /**
     * Open a subscription to the channel of data emitted by the decorated channel.
     * All the subscribers should be registered before the step is executed for the first time.
     */
    fun subscribe(): ReceiveChannel<Any?> = broadcastChannel.openSubscription()
}
