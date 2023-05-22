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

import io.qalipsis.api.serialization.SerialFormatWrapper
import io.qalipsis.api.serialization.SerializersProvider
import jakarta.inject.Singleton
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.reflect.KClass

/**
 * Implementation of [RecordSerializer] supporting the serialization of objects using the
 * kotlinx.serialization module.
 *
 * @author Eric Jessé
 */
@ExperimentalSerializationApi
@Suppress("UNCHECKED_CAST")
@Singleton
internal class SerialFormatRecordSerializer(
    serializersProvider: SerializersProvider
) : RecordSerializer {

    override val order: Int = Int.MIN_VALUE

    /**
     * Map of all the serializers, accessible by the types they support.
     */
    private val serializersByType = serializersProvider.serialFormatWrappersByType

    /**
     * All the qualifiers of the supported serializers.
     */
    private val serializersQualifiers = serializersByType.values.flatMap { it.map { it.qualifier } }.toSet()

    override fun acceptsToSerialize(entity: Any?) = entity?.let { it::class in serializersByType.keys } ?: false

    override fun acceptsToDeserialize(record: SerializedRecord): Boolean {
        return record.serializer in serializersQualifiers
    }

    override fun <T : Any> serialize(entity: T?, serializationContext: SerializationContext): SerializedRecord {
        val entityType = (entity!!)::class
        val serializer = getSerializer(entityType) as SerialFormatWrapper<T>
        return SerializedRecord.from(serializer.serialize(entity), entityType, serializer.qualifier)
    }

    override fun <T : Any> deserialize(source: SerializedRecord, deserializationContext: DeserializationContext): T? {
        val serializer = getSerializer(source.type.type.kotlin, source.serializer) as SerialFormatWrapper<T>
        return serializer.deserialize(source.value)
    }

    private fun <T : Any> getSerializer(type: KClass<T>, qualifier: String? = null): SerialFormatWrapper<T> {
        val serializers = serializersByType[type] as Collection<SerialFormatWrapper<T>>
        return if (serializers.size == 1 || qualifier.isNullOrBlank()) {
            serializers.first()
        } else {
            serializers.first { it.qualifier == qualifier }
        }
    }
}