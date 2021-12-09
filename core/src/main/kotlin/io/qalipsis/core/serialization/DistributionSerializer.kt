package io.qalipsis.core.serialization

/**
 * Interface for a serializer to cache and transport data over the wire.
 *
 * @author Eric Jessé
 */
interface DistributionSerializer {

    fun <T : Any> serialize(
        entity: T,
        serializationContext: SerializationContext = SerializationContext.EMPTY
    ): ByteArray

    fun <T : Any> deserialize(
        source: ByteArray,
        deserializationContext: DeserializationContext = DeserializationContext.EMPTY
    ): T
}