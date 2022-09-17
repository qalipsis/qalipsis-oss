package io.qalipsis.api.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration

/**
 * Implementation of [KSerializer] for enabling the serialization of [Duration] type.
 * Example:
 * ```
 * @Serializable
 * data class DurationTest( @Serializable(with = DurationKotlinSerializer::class) val duration: Duration)
 * val durationTest = DurationTest(Duration.ofDays(2))
 * println(Json.encodeToString(durationTest))
 * // Prints {"duration":"PT48H"}
 * ```
 * @author Gabriel Moraes
 */
object DurationKotlinSerializer : KSerializer<Duration> {

    override val descriptor = PrimitiveSerialDescriptor("QDuration", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Duration {
        return Duration.parse(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Duration) {
        encoder.encodeString(value.toString())
    }
}
