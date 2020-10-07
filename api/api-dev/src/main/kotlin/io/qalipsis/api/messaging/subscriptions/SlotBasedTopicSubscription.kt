package io.qalipsis.api.messaging.subscriptions

import io.qalipsis.api.messaging.LinkedRecord
import io.qalipsis.api.messaging.Record
import io.qalipsis.api.sync.ImmutableSlot
import java.time.Duration

/**
 * Subscription used to read from a [io.qalipsis.api.messaging.AbstractLinkedSlotsBasedTopic].
 *
 * @property latestReadSlot latest slot from which a value was read: next call to [poll] will extract the next element
 * @param idleTimeout duration of the idle period until timeout of the subscription
 * @param cancellation statements to run when the subscription is cancelled
 *
 * @author Eric Jessé
 */
internal class SlotBasedSubscription<T>(
        private var latestReadSlot: ImmutableSlot<LinkedRecord<T>>,
        idleTimeout: Duration,
        cancellation: (() -> Unit)
) : AbstractTopicSubscription<T>(idleTimeout, cancellation) {

    override suspend fun poll(): Record<T> {
        verifyState()
        // Wait for or get the value from the slot and changes the read slot to the next one.
        val linkedRecord = latestReadSlot.get()
        latestReadSlot = linkedRecord.next
        return linkedRecord.record
    }
}

