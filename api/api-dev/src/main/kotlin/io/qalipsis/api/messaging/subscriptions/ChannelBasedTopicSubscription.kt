/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

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
open class ChannelBasedTopicSubscription<T> private constructor(
    private val subscriberId: String,
    internal val channel: ReceiveChannel<Record<T>>,
    idleTimeout: Duration,
    cancellation: () -> Unit
) : AbstractTopicSubscription<T>(subscriberId, idleTimeout, cancellation) {

    override suspend fun poll(): Record<T> {
        log.trace { "Subscription ${subscriberId}: Polling for subscription $subscriberId" }
        verifyState()
        log.trace { "Subscription ${subscriberId}: Tracking activity for subscription $subscriberId" }
        pollNotificationChannel?.send(Unit)
        log.trace { "Subscription ${subscriberId}: Waiting for the next record for subscription $subscriberId" }
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