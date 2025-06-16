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