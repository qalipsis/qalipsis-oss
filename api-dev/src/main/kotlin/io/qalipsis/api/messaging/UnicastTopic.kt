package io.qalipsis.api.messaging

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import java.time.Duration

/**
 * This class provides an implementation of [Topic] that forwards messages uniquely to each subscribed, using a FIFO strategy.
 *
 * @author Eric Jessé
 */
internal class UnicastTopic<T>(
    /**
     * Size of the buffer to keep the received records.
     */
    bufferSize: Int,

    /**
     * Idle time of a subscription. Once a subscription passed this duration without record, it is cancelled.
     */
    idleTimeout: Duration

) : AbstractChannelBasedTopic<T>(idleTimeout) {

    override val channel = Channel<Record<T>>(bufferSize)

    override fun buildSubscriptionChannel() = channel

    override fun onSubscriptionCancel(subscriptionChannel: ReceiveChannel<Record<T>>) = {}

}