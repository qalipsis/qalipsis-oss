package io.evolue.api.messaging

import io.evolue.api.logging.LoggerHelper.logger
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import java.time.Duration

/**
 *  Abstract implementation of [topic] for the ones that are based upon native capabilities of
 *  [kotlinx.coroutines.channels.Channel]s.
 *
 * @author Eric Jessé
 */
internal abstract class ChannelBasedTopic(
    /**
     * Idle time of a subscription. Once a subscription passed this duration without record, it is cancelled.
     */
    private val idleTimeout: Duration

) : Topic {

    private var open = true

    protected abstract val channel: SendChannel<Topic.Record>

    protected val subscriptions: MutableMap<String, Topic.TopicSubscription> = mutableMapOf()

    private val log = logger()

    override suspend fun subscribe(subscriberId: String): Topic.TopicSubscription {
        verifyState()
        if (subscriptions.containsKey(subscriberId)) {
            return subscriptions[subscriberId]!!
        }
        val subscriptionChannel = buildSubscriptionChannel()
        val subscription = Topic.TopicSubscription(subscriptionChannel, idleTimeout) {
            subscriptions.remove(subscriberId)
            onSubscriptionCancel(subscriptionChannel)
        }
        subscriptions[subscriberId] = subscription
        return subscription
    }

    protected abstract fun buildSubscriptionChannel(): ReceiveChannel<Topic.Record>

    protected abstract fun onSubscriptionCancel(subscriptionChannel: ReceiveChannel<Topic.Record>): (() -> Unit)

    override suspend fun produce(record: Topic.Record) {
        verifyState()
        log.trace("Producing record $record")
        channel.send(record)
    }

    override suspend fun produce(value: Any?) {
        verifyState()
        log.trace("Producing value $value")
        produce(Topic.Record(value = value))
    }

    override suspend fun poll(subscriberId: String): Topic.Record {
        verifyState()
        val subscription = subscriptions[subscriberId] ?: UnknownSubscriptionException()
        return (subscription as Topic.TopicSubscription).poll()
    }

    override suspend fun pollValue(subscriberId: String): Any? {
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

    private fun verifyState() {
        log.trace("Verifying topic state")
        if (!open) {
            log.trace("Topic is closed")
            throw ClosedTopicException()
        }
        log.trace("Topic is open")
    }
}