package io.qalipsis.api.messaging

import io.qalipsis.api.coroutines.currentCoroutineScope
import io.qalipsis.api.messaging.subscriptions.ChannelBasedTopicSubscription
import io.qalipsis.api.messaging.subscriptions.TopicSubscription
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import java.time.Duration
import kotlin.coroutines.CoroutineContext

/**
 *  Abstract implementation of [Topic] using native capabilities of [kotlinx.coroutines.channels.Channel]s.
 *
 * @author Eric Jessé
 */
internal abstract class AbstractChannelBasedTopic<T>(
    /**
     * Idle time of a subscription. Once a subscription passed this duration without record, it is cancelled.
     */
    private val idleTimeout: Duration

) : Topic<T> {

    private var open = true

    protected abstract val channel: SendChannel<Record<T>>

    protected val subscriptionMutex = Mutex(false)

    protected val subscriptions = mutableMapOf<String, ChannelBasedTopicSubscription<T>>()

    override suspend fun subscribe(
        subscriberId: String,
        idleCoroutineContext: CoroutineContext?
    ): TopicSubscription<T> {
        verifyState()
        return if (subscriptions.containsKey(subscriberId)) {
            log.trace { "Returning existing subscription for $subscriberId" }
            subscriptions[subscriberId]!!
        } else {
            subscriptionMutex.withLock {
                if (subscriptions.containsKey(subscriberId)) {
                    log.trace { "Returning existing subscription for $subscriberId" }
                    subscriptions[subscriberId]!!
                } else {
                    log.trace { "Creating new subscription for $subscriberId" }
                    val subscriptionChannel = buildSubscriptionChannel()
                    val context = idleCoroutineContext ?: currentCoroutineScope().coroutineContext
                    ChannelBasedTopicSubscription.create(context, subscriberId, subscriptionChannel, idleTimeout) {
                        subscriptions.remove(subscriberId)
                        onSubscriptionCancel(subscriptionChannel)
                    }.also {
                        subscriptions[subscriberId] = it
                        log.trace { "New subscription created for $subscriberId" }
                    }
                }
            }
        }
    }

    protected abstract fun buildSubscriptionChannel(): ReceiveChannel<Record<T>>

    protected abstract fun onSubscriptionCancel(subscriptionChannel: ReceiveChannel<Record<T>>): (() -> Unit)

    override suspend fun produce(record: Record<T>) {
        verifyState()
        log.trace { "Producing record $record" }
        channel.send(record)
    }

    override suspend fun produceValue(value: T) {
        verifyState()
        log.trace { "Producing value $value" }
        produce(Record<T>(value = value))
    }

    override suspend fun poll(subscriberId: String): Record<T> {
        verifyState()
        return subscriptions[subscriberId]?.poll() ?: error("The subscription $subscriberId no longer exists")
    }

    override suspend fun pollValue(subscriberId: String): T {
        verifyState()
        return poll(subscriberId).value
    }

    override fun cancel(subscriberId: String) {
        subscriptions.remove(subscriberId)?.cancel()
    }

    override fun close() {
        open = false
        channel.close()
        subscriptions.values.forEach { it.cancel() }
        subscriptions.clear()
    }

    override suspend fun complete() {
        // Nothing to do.
    }

    private fun verifyState() {
        log.trace { "Verifying topic state" }
        if (!open) {
            log.trace { "Topic is closed" }
            throw ClosedTopicException()
        }
        log.trace { "Topic is open" }
    }

    companion object {

        @JvmStatic
        private val log = KotlinLogging.logger { }
    }
}
