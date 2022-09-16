package io.qalipsis.api.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Base64

/**
 * Serializes a byte array as a base64 string.
 *
 * @author Eric Jessé
 */
object ByteArrayKotlinSerializer : KSerializer<ByteArray> {

    private val base64Encoder = Base64.getEncoder()

    private val base64Decoder = Base64.getDecoder()

    override val descriptor = PrimitiveSerialDescriptor("QByteArray", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ByteArray {
        return base64Decoder.decode(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(base64Encoder.encodeToString(value))
    }
}
