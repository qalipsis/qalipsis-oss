package io.qalipsis.core.serialization

/**
 * Interface for a serializer to cache and transport data over the wire.
 *
 * @author Eric Jessé
 */
interface DistributionSerializer {

    fun <T> serialize(
        entity: T,
        serializationContext: SerializationContext = SerializationContext.EMPTY
    ): ByteArray

    fun <T> deserialize(
        source: ByteArray,
        deserializationContext: DeserializationContext = DeserializationContext.EMPTY
    ): T?

    fun <T> serializeAsRecord(
        entity: T,
        serializationContext: SerializationContext = SerializationContext.EMPTY
    ): SerializedRecord

    fun <T> deserializeRecord(record: SerializedRecord): T?
}