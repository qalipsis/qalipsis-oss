package io.qalipsis.api.messaging.subscriptions

import io.qalipsis.api.messaging.Record
import kotlinx.coroutines.channels.ReceiveChannel
import mu.KotlinLogging
import java.time.Duration

/**
 * Subscription used to read from a [io.qalipsis.api.messaging.AbstractChannelBasedTopic].
 *
 * @param subscriberId name of the subscriber
 * @property channel channel to push the data to the caller
 * @param idleTimeout duration of the idle period until timeout of the subscription
 * @param cancellation statements to run when the subscription is cancelled
 *
 * @author Eric Jessé
 */
internal open class ChannelBasedTopicSubscription<T> private constructor(
    private val subscriberId: String,
    internal val channel: ReceiveChannel<Record<T>>,
    idleTimeout: Duration,
    cancellation: () -> Unit
) : AbstractTopicSubscription<T>(subscriberId, idleTimeout, cancellation) {

    override suspend fun poll(): Record<T> {
        log.trace { "Polling for subscription $subscriberId" }
        verifyState()
        log.trace { "Tracking activity for subscription $subscriberId" }
        pollNotificationChannel?.send(Unit)
        log.trace { "Waiting for the next record for subscription $subscriberId" }
        return channel.receive()
    }

    companion object {

        @JvmStatic
        private val log = KotlinLogging.logger { }

        /**
         * Creates a new instance of [ChannelBasedTopicSubscription].
         *
         * @param subscriberId name of the subscription
         * @param channel channel to push the data to the caller
         * @param idleTimeout duration of the idle period until timeout of the subscription
         * @param cancellation statements to run when the subscription is cancelled
         */
        suspend fun <T> create(
            subscriberId: String,
            channel: ReceiveChannel<Record<T>>,
            idleTimeout: Duration,
            cancellation: () -> Unit
        ) = ChannelBasedTopicSubscription(
            subscriberId,
            channel,
            idleTimeout,
            cancellation
        ).apply { init() }

    }
}