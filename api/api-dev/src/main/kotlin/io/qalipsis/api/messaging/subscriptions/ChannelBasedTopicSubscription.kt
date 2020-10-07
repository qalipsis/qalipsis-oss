package io.qalipsis.api.messaging.subscriptions

import io.qalipsis.api.messaging.Record
import kotlinx.coroutines.channels.ReceiveChannel
import java.time.Duration

/**
 *
 * @author Eric Jessé
 */
internal open class ChannelBasedTopicSubscription<T>(
        internal val channel: ReceiveChannel<Record<T>>,
        idleTimeout: Duration,
        cancellation: (() -> Unit)
) : AbstractTopicSubscription<T>(idleTimeout, cancellation) {

    override suspend fun poll(): Record<T> {
        verifyState()
        pollNotificationChannel?.send(Unit)
        return channel.receive()
    }
}