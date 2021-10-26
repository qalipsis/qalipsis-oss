package io.qalipsis.api.messaging

import io.qalipsis.api.messaging.subscriptions.AbstractTopicSubscription
import io.qalipsis.api.messaging.subscriptions.TopicSubscription
import kotlinx.coroutines.channels.Channel
import java.time.Duration

/**
 * A [Topic] is a messaging interface able to address different models of pub/sub behind a unified interface.
 * It is freely inspired by [Apache Kafka topics][https://kafka.apache.org], moving customer configuration
 * (like <i>auto.offset.reset<i>,  <i>connections.max.idle.ms<i>) to the topic configuration for the ease-of-use.
 *
 * Since [kotlinx.coroutines.channels.Channel]s are used behind the scenes, there is no mechanism of acknowledgment.
 *
 * @author Eric Jessé
 */
interface Topic<T> {

    /**
     * Create a subscription to the topic for the given subscriber.
     *
     * @param subscriberId the identifier of the subscriber. If it is already known by the topic, the existing channel is returned.
     * @param idleVerificationCoroutineScope coroutine scope to use for the .
     */
    @Throws(ClosedTopicException::class)
    suspend fun subscribe(subscriberId: String): TopicSubscription<T>

    /**
     * Produce a value to the topic, which will be wrapped into a [Record].
     */
    @Throws(ClosedTopicException::class)
    suspend fun produceValue(value: T)

    /**
     * Produce a [Record] to the topic.
     */
    @Throws(ClosedTopicException::class)
    suspend fun produce(record: Record<T>)

    /**
     * Poll the next record from the [AbstractTopicSubscription] attached to [subscriberId].
     *
     * @throws UnknownSubscriptionException if not subscription can be found for [subscriberId]
     */
    @Throws(UnknownSubscriptionException::class, ClosedTopicException::class)
    suspend fun poll(subscriberId: String): Record<T>

    /**
     * Poll the value of the next record from the [AbstractTopicSubscription] attached to [subscriberId].
     *
     * @throws UnknownSubscriptionException if not subscription can be found for [subscriberId]
     */
    @Throws(UnknownSubscriptionException::class, ClosedTopicException::class)
    suspend fun pollValue(subscriberId: String): T

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

    /**
     * Notify the topic that no more value will be produced.
     */
    suspend fun complete()

}

/**
 * Creates a new [Topic] that loops on itself once [Topic.complete] is called.
 *
 * @param subscriptionIdleTimeout duration of the idle subscriptions before they are cancelled, `Duration.ZERO` or less means infinite.
 *
 * @author Eric Jessé
 */
fun <T> loopTopic(subscriptionIdleTimeout: Duration = Duration.ZERO): Topic<T> = LoopTopic(subscriptionIdleTimeout)

/**
 * Creates a new [Topic] that forward each record to the first consumer that will poll it.
 *
 * @param bufferSize size of the buffer to keep the received records before they are consumed, unlimited by default.
 * @param subscriptionIdleTimeout duration of the idle subscriptions (subscriptions not receiving any record) before they are cancelled.
 *
 * @author Eric Jessé
 */
fun <T> unicastTopic(bufferSize: Int = Channel.UNLIMITED, subscriptionIdleTimeout: Duration = Duration.ZERO): Topic<T> =
    UnicastTopic(if (bufferSize > 0) bufferSize else Channel.UNLIMITED, subscriptionIdleTimeout)


/**
 * Creates a new [Topic] that forward each record to all the subscribers. New subscriptions receive the earliest
 * available record at the time of subscription, considering the maximal buffer size.
 *
 * @param bufferSize size of the buffer to keep the received records before the subscriptions, unlimited by default.
 * @param subscriptionIdleTimeout duration of the idle subscriptions (subscriptions not receiving any record) before they are cancelled.
 *
 * @author Eric Jessé
 */
fun <T> broadcastTopic(bufferSize: Int = -1, subscriptionIdleTimeout: Duration = Duration.ZERO): Topic<T> =
    BroadcastTopic(bufferSize, subscriptionIdleTimeout)
