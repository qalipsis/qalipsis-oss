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

import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.messaging.LinkedRecord
import io.qalipsis.api.messaging.Record
import io.qalipsis.api.sync.ImmutableSlot
import java.time.Duration

/**
 * Subscription used to read from a [io.qalipsis.api.messaging.AbstractLinkedSlotsBasedTopic].
 *
 * @param subscriberId name of the subscriber
 * @property latestReadSlot latest slot from which a value was read: next call to [poll] will extract the next element
 * @param idleTimeout duration of the idle period until timeout of the subscription
 * @param cancellation statements to run when the subscription is cancelled
 *
 * @author Eric Jessé
 */
internal class SlotBasedSubscription<T> private constructor(
    private val subscriberId: String,
    private var latestReadSlot: ImmutableSlot<LinkedRecord<T>>,
    idleTimeout: Duration,
    cancellation: (() -> Unit)
) : AbstractTopicSubscription<T>(subscriberId, idleTimeout, cancellation) {

    override suspend fun poll(): Record<T> {
        verifyState()
        // Wait for or get the value from the slot and changes the read slot to the next one.
        log.trace { "Subscription ${subscriberId}: Polling the next record of slot $latestReadSlot" }
        val linkedRecord = latestReadSlot.get()
        log.trace { "Subscription ${subscriberId}: The record $linkedRecord was read, moving the cursor to the next slot" }
        latestReadSlot = linkedRecord.next
        log.trace { "Subscription ${subscriberId}: Returning the record and moving the cursor to the next slot" }
        return linkedRecord.record
    }

    companion object {

        private val log = logger()

        /**
         * Creates a new instance of [SlotBasedSubscription].
         *
         * @param subscriberId name of the subscriber
         * @param latestReadSlot latest slot from which a value was read: next call to [poll] will extract the next element
         * @param idleTimeout duration of the idle period until timeout of the subscription
         * @param cancellation statements to run when the subscription is cancelled
         */
        suspend fun <T> create(
            subscriberId: String,
            latestReadSlot: ImmutableSlot<LinkedRecord<T>>,
            idleTimeout: Duration,
            cancellation: (() -> Unit)
        ) = SlotBasedSubscription(
            subscriberId,
            latestReadSlot,
            idleTimeout,
            cancellation
        ).apply { init() }

    }
}

