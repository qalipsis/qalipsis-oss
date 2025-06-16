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

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.ImmutableSlot
import kotlinx.coroutines.sync.withLock
import java.time.Duration

/**
 * [LoopTopic] is a special implementation of [Topic] providing the data as an infinite loop: once the consumer polled
 * all the values from the topic, values are provided from the beginning.
 *
 * @param idleTimeout idle time of a subscription: once a subscription passed this duration without record, it is cancelled.
 *
 * @author Eric Jessé
 */
internal open class LoopTopic<T>(
    idleTimeout: Duration
) : AbstractLinkedSlotsBasedTopic<T>(idleTimeout) {

    private var shouldComplete = false

    private var complete = false

    override suspend fun updateSubscriptionSlot(lastSetSlot: ImmutableSlot<LinkedRecord<T>>) {
        // If the topic was tried to be completed but failed because it was empty, it is done now.
        if (shouldComplete != complete) {
            complete()
        }
    }

    override suspend fun complete() {
        if (!complete) {
            shouldComplete = true
            writeMutex.withLock {
                // If there is at least on item, the chain is closed to form the loop.
                if (writeSlot !== subscriptionSlot) {
                    writeSlot.set(subscriptionSlot.get())
                    complete = true
                }
            }
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }

}