package io.qalipsis.core.serialization

import io.qalipsis.api.serialization.Serializers
import jakarta.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Implementation of [DistributionSerializer] using a [SerializedRecord] to transport the data.
 *
 * @author Eric Jessé
 */
@Singleton
internal class RecordDistributionSerializer(
    serializers: List<RecordSerializer>
) : DistributionSerializer {

    private val sortedSerializers = serializers.sortedBy(RecordSerializer::order)

    override fun <T> serialize(entity: T, serializationContext: SerializationContext): ByteArray {
        val record = sortedSerializers.asSequence().filter { it.acceptsToSerialize(entity) }
            .firstNotNullOfOrNull { kotlin.runCatching { it.serialize(entity) }.getOrNull() }
            ?: throw SerializationException("The value of type ${entity?.let { it::class }} could not be serialized")

        return Serializers.json.encodeToString(record).encodeToByteArray()
    }

    override fun <T> deserialize(source: ByteArray, deserializationContext: DeserializationContext): T? {
        val record: SerializedRecord = Serializers.json.decodeFromString(source.decodeToString())
        return sortedSerializers.asSequence().filter { it.acceptsToDeserialize(record) }
            .firstNotNullOfOrNull { kotlin.runCatching { it.deserialize(record) as T? }.getOrNull() }
    }
}