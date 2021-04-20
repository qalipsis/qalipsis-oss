package io.qalipsis.api.messaging

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.messaging.subscriptions.ChannelBasedTopicSubscription
import io.qalipsis.api.messaging.subscriptions.TopicSubscription
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 *  Abstract implementation of [topic] for the ones that are based upon native capabilities of
 *  [kotlinx.coroutines.channels.Channel]s.
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

    protected val subscriptions: MutableMap<String, ChannelBasedTopicSubscription<T>> = ConcurrentHashMap()

    private val log = logger()

    override suspend fun subscribe(subscriberId: String): TopicSubscription<T> {
        verifyState()
        return if (subscriptions.containsKey(subscriberId)) {
            subscriptions[subscriberId]!!
        } else {
            val subscriptionChannel = buildSubscriptionChannel()
            val subscription = ChannelBasedTopicSubscription(subscriptionChannel, idleTimeout) {
                subscriptions.remove(subscriberId)
                onSubscriptionCancel(subscriptionChannel)
            }
            subscriptions[subscriberId] = subscription
            subscription
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
        val subscription = subscriptions[subscriberId] ?: error("The subscription $subscriberId no longer exists")
        return subscription.poll()
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
}
