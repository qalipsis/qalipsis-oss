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

import io.qalipsis.api.sync.ImmutableSlot

/**
 * Item of the linked chain of records.
 *
 * @property record the record value of the slot
 * @property next the next item in the chain
 *
 * @author Eric Jessé
 */
interface LinkedRecord<T> {

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

    override fun toString(): String {
        return "EmptyLinkedRecord()"
    }

}

/**
 * Item of the linked chain of records able to host a value.
 *
 * @property record the record value of the slot
 * @property nextSlot the next slot of the chain
 *
 * @author Eric Jessé
 */
internal class LinkedRecordWithValue<T>(
    override val record: Record<T>,
    override val next: ImmutableSlot<LinkedRecord<T>> = ImmutableSlot()
) : LinkedRecord<T> {

    override fun toString(): String {
        return "LinkedRecordWithValue(record=$record)"
    }
}
