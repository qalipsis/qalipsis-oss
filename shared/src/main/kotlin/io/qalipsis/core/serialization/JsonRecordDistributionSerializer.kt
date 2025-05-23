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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.fasterxml.jackson.module.kotlin.readValue
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
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
internal class JsonRecordDistributionSerializer : DistributionSerializer {

    private val objectMapper = jacksonMapperBuilder()
        .addModule(Jdk8Module())
        .addModule(JavaTimeModule())
        .addModule(
            KotlinModule.Builder()
                .configure(KotlinFeature.NullToEmptyCollection, true)
                .configure(KotlinFeature.NullToEmptyMap, true)
                .configure(KotlinFeature.NullIsSameAsDefault, true)
                .build()
        )

        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
        .enable(MapperFeature.AUTO_DETECT_CREATORS)
        .enable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .enable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)

        // Serialization configuration.
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        // Deserialization configuration.
        .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
        .build()

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