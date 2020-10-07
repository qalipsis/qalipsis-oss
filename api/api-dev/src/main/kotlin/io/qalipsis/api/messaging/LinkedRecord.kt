package io.qalipsis.api.messaging

import io.qalipsis.api.sync.ImmutableSlot

/**
 * Item of the linked chain of records.
 *
 * @property record the record value of the slot
 * @property next the next item in the chain
 *
 * @author Eric Jessé
 */
internal interface LinkedRecord<T> {

    val record: Record<T>

    val next: ImmutableSlot<LinkedRecord<T>>
}

/**
 * Empty item of a chain of records.
 *
 * @author Eric Jessé
 */
internal class EmptyLinkedRecord<T>(override val next: ImmutableSlot<LinkedRecord<T>> = ImmutableSlot()) :
    LinkedRecord<T> {

    override val record: Record<T>
        get() = error("Not supported")

}

/**
 * Item of the linked chain of records able to host a value.
 *
 * @property record the record value of the slot
 * @property nextSlot the next slot of the chain
 *
 * @author Eric Jessé
 */
internal class LinkedRecordWithValue<T>(override val record: Record<T>,
                                        override val next: ImmutableSlot<LinkedRecord<T>> = ImmutableSlot()) :
    LinkedRecord<T>
