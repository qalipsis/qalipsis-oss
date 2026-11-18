/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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
