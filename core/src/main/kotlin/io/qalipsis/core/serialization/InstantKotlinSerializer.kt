package io.qalipsis.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

/**
 * Implementation of [KSerializer] for enabling the serialization of [Instant] type.
 * Example:
 * ```
 * @Serializable
 * data class InstantTest(@Serializable(with = InstantKotlinSerializer::class) val instant: Instant)
 * val instantTest = InstantTest(Instant.now())
 * println(Json.encodeToString(instantTest))
 * // Prints {"instant":1643811746048}
 * ```
 * @author Gabriel Moraes
 */
object InstantKotlinSerializer: KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.ofEpochMilli(decoder.decodeLong())
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.toEpochMilli())
    }
}
