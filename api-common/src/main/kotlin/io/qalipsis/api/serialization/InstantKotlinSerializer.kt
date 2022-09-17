package io.qalipsis.api.serialization

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
 * // Prints {"instant":"2022-09-15T11:53:55.151221Z"}
 * ```
 * @author Gabriel Moraes
 */
object InstantKotlinSerializer : KSerializer<Instant> {

    override val descriptor = PrimitiveSerialDescriptor("QInstant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

}
