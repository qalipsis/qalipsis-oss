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
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import jakarta.inject.Singleton

/**
 * Implementation of [RecordSerializer] using the Jackson library for JSON.
 *
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
@Singleton
internal class JacksonJsonRecordSerializer : RecordSerializer {

    override val order: Int = 0

    private val mapper = jsonMapper {
        addModule(JavaTimeModule())
        addModule(kotlinModule {})
        addModule(Jdk8Module())

        disable(SerializationFeature.FAIL_ON_SELF_REFERENCES)
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
        disable(SerializationFeature.WRITE_CHAR_ARRAYS_AS_JSON_ARRAYS)
        disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)

        disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)

        enable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    }

    override fun acceptsToSerialize(entity: Any?) = entity != null

    override fun acceptsToDeserialize(record: SerializedRecord): Boolean {
        return record.serializer == QUALIFIER
    }

    override fun <T : Any> serialize(entity: T?, serializationContext: SerializationContext): SerializedRecord {
        val entityType = (entity!!)::class
        return BinarySerializedRecord.from(mapper.writeValueAsBytes(entity), entityType, QUALIFIER)
    }

    override fun <T : Any> deserialize(source: SerializedRecord, deserializationContext: DeserializationContext): T? {
        source as BinarySerializedRecord
        return mapper.readValue(source.value, source.serType.type) as T
    }

    companion object {

        private const val QUALIFIER = "jackson-json"

    }
}