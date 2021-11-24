package io.qalipsis.core.factory.serialization

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
        return SerializedRecord.from(ByteArray(0), Unit::class, QUALIFIER)
    }

    override fun <T : Any> deserialize(source: SerializedRecord, deserializationContext: DeserializationContext): T? {
        return null
    }

    companion object {

        private const val QUALIFIER = "null"

    }
}