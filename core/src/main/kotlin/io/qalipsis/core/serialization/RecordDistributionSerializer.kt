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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

/**
 * Implementation of [DistributionSerializer] using a [SerializedRecord] to transport the data.
 *
 * @author Eric Jessé
 */
@Singleton
@ExperimentalSerializationApi
internal class RecordDistributionSerializer(
    serializers: List<RecordSerializer>,
    private val protoBuf: ProtoBuf
) : DistributionSerializer {

    private val sortedSerializers = serializers.sortedBy(RecordSerializer::order)

    override fun <T> serialize(entity: T, serializationContext: SerializationContext): ByteArray {
        return protoBuf.encodeToByteArray(serializeAsRecord(entity, serializationContext))
    }

    override fun <T> deserialize(source: ByteArray, deserializationContext: DeserializationContext): T? {
        val record: SerializedRecord = protoBuf.decodeFromByteArray(source)
        return deserializeRecord(record)
    }

    override fun <T> serializeAsRecord(entity: T, serializationContext: SerializationContext): SerializedRecord {
        val result = sortedSerializers.asSequence().filter { it.acceptsToSerialize(entity) }
            .firstNotNullOfOrNull {
                kotlin.runCatching {
                    it.serialize(entity)
                }.getOrNull()
            }
            ?: throw SerializationException("The value of type ${entity?.let { it::class }} could not be serialized")
        return result
    }

    override fun <T> deserializeRecord(record: SerializedRecord): T? {
        return sortedSerializers.asSequence().filter { it.acceptsToDeserialize(record) }
            .firstNotNullOfOrNull { kotlin.runCatching { it.deserialize(record) as T? }.getOrNull() }
    }
}