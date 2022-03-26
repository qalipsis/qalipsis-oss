package io.qalipsis.core.serialization

import io.qalipsis.api.serialization.SerializableClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * Wrapper of a serialized record.
 *
 * @property value serialized value
 * @property type concrete type of the original value
 * @property serializer qualifier of the serializer
 *
 * @author Eric Jessé
 */
@Serializable
class SerializedRecord(
    @SerialName("v") @Serializable(with = ByteArrayKotlinSerializer::class) val value: ByteArray,
    @SerialName("t") val type: SerializableClass,
    @SerialName("s") val serializer: String,
    @SerialName("m") val metadata: Map<String, String> = emptyMap()
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SerializedRecord

        if (!value.contentEquals(other.value)) return false
        if (type != other.type) return false
        if (serializer != other.serializer) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = value.contentHashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + serializer.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }

    companion object {

        @JvmStatic
        fun from(
            value: ByteArray,
            type: KClass<*>,
            serializer: String,
            metadata: Map<String, String> = emptyMap()
        ) = SerializedRecord(value, SerializableClass(type.java), serializer, metadata)
    }

}