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

import io.qalipsis.api.logging.LoggerHelper.logger
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
        return sortedSerializers.asSequence().filter { it.acceptsToSerialize(entity) }
            .onEach { log.trace { "$it can serialize the entity of type ${entity?.let { it::class }}" } }
            .firstNotNullOfOrNull {
                try {
                    it.serialize(entity)
                } catch (e: Exception) {
                    log.trace(e) { "An exception occurred while serializing $entity with $it" }
                    null
                }
            }?.also {
                log.trace { "The entity of type ${entity?.let { it::class }} was serialized with the qualifier ${it.serializer}" }
            } ?: throw SerializationException("The value of type ${entity?.let { it::class }} could not be serialized")
    }

    override fun <T> deserializeRecord(record: SerializedRecord): T? {
        return sortedSerializers.asSequence().filter { it.acceptsToDeserialize(record) }
            .onEach { log.trace { "$it can deserialize the record of type ${record.type} and qualifier ${record.serializer}" } }
            .firstNotNullOfOrNull { kotlin.runCatching { it.deserialize(record) as T? }.getOrNull() }
    }

    private companion object {
        val log = logger()
    }
}