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