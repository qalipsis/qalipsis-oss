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

package io.qalipsis.core.serialization

import com.fasterxml.jackson.annotation.JsonIgnore
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
 * Wrapper of a serialized record. Since the Kotlin serialization does not support nullable values for all the formats,
 * all the fields are non-nullable.
 *
 * @property value serialized value as a byte array
 * @property serType concrete type of the original value
 * @property serializer qualifier of the serializer
 *
 * @author Eric Jessé
 */
@Serializable
@SerialName("bin")
class BinarySerializedRecord(
    @SerialName("v") @Serializable(with = OtherByteArrayKotlinSerializer::class)
    val value: ByteArray = ByteArray(0),
    @SerialName("t")
    val serType: SerializableClass,
    @SerialName("s")
    override val serializer: String,
    @SerialName("m")
    val metadata: Map<String, String> = emptyMap()
) : SerializedRecord {

    @get:JsonIgnore
    override val type: Class<*>
        get() = serType.type

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BinarySerializedRecord

        if (!value.contentEquals(other.value)) return false
        if (serType != other.serType) return false
        if (serializer != other.serializer) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = value.contentHashCode()
        result = 31 * result + serType.hashCode()
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
        ) = BinarySerializedRecord(
            value,
            serType = SerializableClass(type.java),
            serializer = serializer,
            metadata = metadata
        )
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