package io.qalipsis.core.serialization

/**
 * Service to serialize and deserialize objects of any kind.
 *
 * @author Eric Jessé
 */
interface RecordSerializer {

    /**
     * Order to attempt to use the serializer.
     */
    val order: Int

    fun acceptsToSerialize(entity: Any?): Boolean

    fun acceptsToDeserialize(record: SerializedRecord): Boolean

    fun <T : Any> serialize(
        entity: T?,
        serializationContext: SerializationContext = SerializationContext.EMPTY
    ): SerializedRecord

    fun <T : Any> deserialize(
        source: SerializedRecord,
        deserializationContext: DeserializationContext = DeserializationContext.EMPTY
    ): T?

}