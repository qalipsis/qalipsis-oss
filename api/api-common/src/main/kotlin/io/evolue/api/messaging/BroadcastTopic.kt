package io.evolue.api.messaging

import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import java.time.Duration

/**
 * This class provides an implementation of [Topic] that forwards all the messages to all the subscriber.
 *
 * @author Eric Jessé
 */
internal class BroadcastTopic(
    /**
     * Size of the buffer to keep the received records.
     */
    bufferSize: Int,

    /**
     * Idle time of a subscription. Once a subscription passed this duration without record, it is cancelled.
     */
    idleTimeout: Duration

) : ChannelBasedTopic(idleTimeout) {

    override val channel = BroadcastChannel<Topic.Record>(bufferSize)

    override fun buildSubscriptionChannel() = channel.openSubscription()

    override fun onSubscriptionCancel(subscriptionChannel: ReceiveChannel<Topic.Record>) = {
        subscriptionChannel.cancel()
    }
}