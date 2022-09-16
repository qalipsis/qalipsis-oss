package io.qalipsis.api.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@JvmInline
@Serializable(with = SerializableClassSerializer::class)
value class SerializableClass(val type: Class<*>)

internal object SerializableClassSerializer : KSerializer<SerializableClass> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("type", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SerializableClass) {
        encoder.encodeString(value.type.canonicalName)
    }

    override fun deserialize(decoder: Decoder): SerializableClass {
        val className = decoder.decodeString()
        return SerializableClass(Class.forName(className))
    }
}
