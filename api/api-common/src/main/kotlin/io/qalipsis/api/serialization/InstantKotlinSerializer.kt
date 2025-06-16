/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

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
