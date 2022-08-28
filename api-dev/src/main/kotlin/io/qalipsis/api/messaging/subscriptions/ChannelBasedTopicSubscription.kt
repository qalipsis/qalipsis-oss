/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
internal open class ChannelBasedTopicSubscription<T> private constructor(
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