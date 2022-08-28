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
