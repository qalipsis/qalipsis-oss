package io.qalipsis.api.messaging.subscriptions

import io.qalipsis.api.messaging.Record
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

/**
 * Subscription to consume records from a topic.
 *
 * @author Eric Jessé
 */
interface TopicSubscription<T> {

    /**
     * Polls the next record from the topic.
     */
    suspend fun poll(): Record<T>

    /**
     * Polls the next record from the topic and directly returns its value.
     */
    suspend fun pollValue(): T {
        return poll().value
    }

    /**
     * Executes an action on each received value.
     *
     * @return the [Job] running the consumption and execution of the handler.
     */
    suspend fun onReceiveValue(context: CoroutineContext = Dispatchers.Default, block: suspend (T) -> Unit): Job

    /**
     * Returns `true` if the subscription is still active, otherwise `false`.
     */
    fun isActive(): Boolean

    /**
     * Cancels the subscription.
     */
    fun cancel()

}