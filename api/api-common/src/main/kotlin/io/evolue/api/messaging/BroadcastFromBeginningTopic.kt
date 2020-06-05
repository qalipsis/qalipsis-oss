package io.evolue.api.messaging

import com.google.common.collect.EvictingQueue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration

/**
 * This class provides an implementation of [Topic] that forwards all the messages to all the subscriber.
 * Contrary to [BroadcastTopic], the new subscribers first receive all the records buffered so far.
 *
 * The implementation has a cost to consider, since it requires more processing to maintain thread-safety and buffering.
 *
 * @author Eric Jessé
 */
internal class BroadcastFromBeginningTopic(
    /**
     * Size of the buffer to keep the past received records.
     */
    bufferSize: Int,
    /**
     * Idle time of a subscription. Once a subscription passed this duration without record, it is cancelled.
     */
    private val idleTimeout: Duration
) : Topic {

    private var open = true

    private val buffer = EvictingQueue.create<Topic.Record>(bufferSize)

    private val subscriptionMutex = Mutex(false)

    private val subscriptions: MutableMap<String, Topic.TopicSubscription> = mutableMapOf()

    override suspend fun subscribe(subscriberId: String): Topic.TopicSubscription {
        verifyState()
        if (subscriptions.containsKey(subscriberId)) {
            return subscriptions[subscriberId]!!
        }
        return subscriptionMutex.withLock {
            val subscriptionChannel = Channel<Topic.Record>(Channel.UNLIMITED)

            if (buffer.isNotEmpty()) {
                buffer.forEach { subscriptionChannel.send(it) }
            }
            val subscription = Topic.TopicSubscription(subscriptionChannel, idleTimeout) {
                subscriptions.remove(subscriberId)

                subscriptionChannel.cancel()
            }
            subscriptions[subscriberId] = subscription
            subscription
        }
    }

    override suspend fun produce(record: Topic.Record) {
        verifyState()
        subscriptionMutex.withLock {
            buffer.add(record)
        }
        subscriptions.values.map { it.channel as Channel<Topic.Record> }.forEach { c -> c.send(record) }
    }

    override suspend fun produce(value: Any?) = produce(Topic.Record(value = value))

    override suspend fun poll(subscriberId: String): Topic.Record {
        verifyState()
        val subscription = subscriptions[subscriberId] ?: UnknownSubscriptionException()
        return (subscription as Topic.TopicSubscription).poll()
    }

    override suspend fun pollValue(subscriberId: String) = poll(subscriberId).value

    override fun cancel(subscriberId: String) {
        subscriptions.remove(subscriberId)?.cancel()
    }

    override fun close() {
        open = false
        buffer.clear()
        subscriptions.values.forEach { it.cancel() }
        subscriptions.clear()
    }

    private fun verifyState() {
        if (!open) {
            throw ClosedTopicException()
        }
    }
}