package io.evolue.api.messaging

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.time.Duration

/**
 * A [topic] is a messaging interface able to address different models of pub/sub behind a unified interface.
 * It is freely inspired by [Apache Kafka topics][https://kafka.apache.org], moving customer configuration
 * (like <i>auto.offset.reset<i>,  <i>connections.max.idle.ms<i>) to the topic configuration for the ease-of-use.
 *
 * Since [kotlinx.coroutines.channels.Channel]s are used behind the scenes, there is no mechanism of acknowledgment.
 *
 * @author Eric Jessé
 */
interface Topic {

    /**
     * Create a subscription to the topic for the given subscriber.
     *
     * @param subscriberId the identifier of the subscriber. If it is already known by the topic, the existing channel
     * is returned.
     */
    @Throws(ClosedTopicException::class)
    suspend fun subscribe(subscriberId: String): TopicSubscription

    /**
     * Produce a value to the topic, which will be wrapped into a [Record].
     */
    @Throws(ClosedTopicException::class)
    suspend fun produce(value: Any?)

    /**
     * Produce a [Record] to the topic.
     */
    @Throws(ClosedTopicException::class)
    suspend fun produce(record: Record)

    /**
     * Poll the next record from the [TopicSubscription] attached to [subscriberId].
     *
     * @throws UnknownSubscriptionException if not subscription can be found for [subscriberId]
     */
    @Throws(UnknownSubscriptionException::class, ClosedTopicException::class)
    suspend fun poll(subscriberId: String): Record

    /**
     * Poll the value of the next record from the [TopicSubscription] attached to [subscriberId].
     *
     * @throws UnknownSubscriptionException if not subscription can be found for [subscriberId]
     */
    @Throws(UnknownSubscriptionException::class, ClosedTopicException::class)
    suspend fun pollValue(subscriberId: String): Any?

    /**
     * Cancel the subscription for the given [subscriberId]. Any subsequent call to [poll] or [pollValue] will fail.
     *
     * This function is idempotent and has no effect if the subscription was already cancelled.
     */
    @Throws(ClosedTopicException::class)
    fun cancel(subscriberId: String)

    /**
     * Cancel all subscriptions to the topic.
     */
    fun close()

    data class Record(
        val headers: MutableMap<String, Any> = mutableMapOf(),
        val value: Any?
    )

    class TopicSubscription(
        internal val channel: ReceiveChannel<Record>,
        private val idleTimeout: Duration,
        private val cancellation: (() -> Unit)
    ) {
        private var active = true

        private val activityChannel = Channel<Unit>(1)

        private var timeout: ReceiveChannel<Unit> = buildTimeout()

        init {
            GlobalScope.launch {
                while (active) {
                    select<Unit> {
                        activityChannel.onReceive { _ ->
                            timeout.cancel()
                            timeout = buildTimeout()
                        }
                        timeout.onReceive { _ ->
                            cancel()
                        }
                    }
                }
            }
        }

        private fun buildTimeout() = if (idleTimeout.toMillis() > 0) ticker(idleTimeout.toMillis()) else Channel(
            Channel.RENDEZVOUS)

        suspend fun poll(): Record {
            if (!active) {
                throw CancelledSubscriptionException()
            }
            activityChannel.send(Unit)
            return channel.receive()
        }

        suspend fun pollValue(): Any? {
            return poll().value
        }

        fun isActive() = active

        fun cancel() {
            active = false
            timeout.cancel()
            activityChannel.close()
            cancellation()
        }
    }
}

enum class TopicMode {
    /**
     * All the subscribers receive all the records.
     */
    BROADCAST,

    /**
     * The subscribers receive unique records using a FIFO strategy.
     */
    UNICAST
}

/**
 * Creates a new topic.
 *
 * @param mode mode of the [topic] to create. See [TopicMode] for more details.
 * @param bufferSize size of the buffer to keep the received records. ({@code 1024} by default [mode] is [BROADCAST] and [fromBeginning] is {@code true}, {@code 1024} otherwise)
 * @param fromBeginning defines if new subscriptions start from the beginning of the buffer or only at present time.
 * @param subscriptionIdleTimeout duration of the idle subscriptions (subscriptions not receiving any record) before they are cancelled.
 */
fun topic(mode: TopicMode = TopicMode.BROADCAST, bufferSize: Int = -1, fromBeginning: Boolean = false,
          subscriptionIdleTimeout: Duration = Duration.ZERO): Topic {
    return when {
        TopicMode.UNICAST == mode -> {
            val actualBufferSize = if (bufferSize > 0) bufferSize else Channel.UNLIMITED
            UnicastTopic(actualBufferSize, subscriptionIdleTimeout, fromBeginning)
        }
        fromBeginning -> {
            val actualBufferSize = if (bufferSize > 0) bufferSize else 1024
            BroadcastFromBeginningTopic(actualBufferSize, subscriptionIdleTimeout)
        }
        else -> {
            val actualBufferSize = if (bufferSize > 0) bufferSize else Channel.UNLIMITED
            BroadcastTopic(actualBufferSize, subscriptionIdleTimeout)
        }
    }
}