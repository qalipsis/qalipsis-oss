package io.evolue.api.messaging

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import java.time.Duration

/**
 * This class provides an implementation of [Topic] that forwards messages uniquely to each subscribed, using a FIFO strategy.
 *
 * @author Eric Jessé
 */
internal class UnicastTopic(
    /**
     * Size of the buffer to keep the received records.
     */
    bufferSize: Int,

    /**
     * Time to idle of a subscription. Once idle a subscription passed this duration, it is automatically cancelled.
     */
    idleTimeout: Duration,

    /**
     * Defines if the first subscriber will receive all the records from the beginning or only from now on.
     * When set to {@code false}, records before the first subscription are simply discarded.
     */
    private val fromBeginning: Boolean

) : ChannelBasedTopic(idleTimeout) {

    override val channel = Channel<Topic.Record>(bufferSize)

    override suspend fun produce(record: Topic.Record) {
        if (fromBeginning || subscriptions.isNotEmpty()) {
            super.produce(record)
        }
    }

    override fun buildSubscriptionChannel() = channel

    override fun onSubscriptionCancel(subscriptionChannel: ReceiveChannel<Topic.Record>) = {}

}