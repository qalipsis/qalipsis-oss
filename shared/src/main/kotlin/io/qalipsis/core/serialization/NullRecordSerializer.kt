/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.serialization

import jakarta.inject.Singleton

/**
 * Implementation of [RecordSerializer] using the Jackson library for JSON.
 *
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
@Singleton
internal class NullRecordSerializer : RecordSerializer {

    override val order: Int = -1

    override fun acceptsToSerialize(entity: Any?) = entity == null

    override fun acceptsToDeserialize(record: SerializedRecord): Boolean {
        return record.serializer == QUALIFIER
    }

    override fun <T : Any> serialize(entity: T?, serializationContext: SerializationContext): SerializedRecord {
        return BinarySerializedRecord.from(ByteArray(0), Unit::class, QUALIFIER)
    }

    override fun <T : Any> deserialize(source: SerializedRecord, deserializationContext: DeserializationContext): T? {
        return null
    }

    companion object {

        private const val QUALIFIER = "null"

    }
}