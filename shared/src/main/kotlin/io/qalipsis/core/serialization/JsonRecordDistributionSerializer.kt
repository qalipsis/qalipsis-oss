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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.serialization.SerializableClass
import jakarta.inject.Singleton
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Implementation of [DistributionSerializer] using a [SerializedRecord] to transport the data.
 *
 * @author Eric Jessé
 */
@Singleton
@Primary
@ExperimentalSerializationApi
@Requires(property = "streaming.serialization", value = "json")
internal class JsonRecordDistributionSerializer(
    private val objectMapper: ObjectMapper,
) : DistributionSerializer {

    override fun <T> serialize(entity: T, serializationContext: SerializationContext): ByteArray {
        return objectMapper.writeValueAsBytes(serializeAsRecord(entity, serializationContext))
    }

    override fun <T> deserialize(source: ByteArray, deserializationContext: DeserializationContext): T? {
        val record: JsonDebugSerializedRecord = objectMapper.readValue(source)
        return deserializeRecord(record)
    }

    override fun <T> serializeAsRecord(entity: T, serializationContext: SerializationContext): SerializedRecord {
        return entity?.let {
            JsonDebugSerializedRecord(objectMapper.writeValueAsString(it), it::class.java, serializer = QUALIFIER)
        } ?: JsonDebugSerializedRecord(serializer = QUALIFIER)
    }

    override fun <T> deserializeRecord(record: SerializedRecord): T? {
        record as JsonDebugSerializedRecord
        return record.value?.let { value ->
            objectMapper.readValue(value, record.type) as T
        }
    }

    private companion object {

        const val QUALIFIER = "json-debug"

    }

}