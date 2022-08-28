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

