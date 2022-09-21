/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.serialization

import io.qalipsis.api.serialization.SerializableClass
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Base64
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
    @SerialName("v") @Serializable(with = OtherByteArrayKotlinSerializer::class) val value: ByteArray,
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

    object OtherByteArrayKotlinSerializer : KSerializer<ByteArray> {

        private val base64Encoder = Base64.getEncoder()

        private val base64Decoder = Base64.getDecoder()

        override val descriptor = PrimitiveSerialDescriptor("QByteArray", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: ByteArray) {
            encoder.encodeString(base64Encoder.encodeToString(value))
        }

        override fun deserialize(decoder: Decoder): ByteArray {
            return base64Decoder.decode(decoder.decodeString())
        }
    }

}