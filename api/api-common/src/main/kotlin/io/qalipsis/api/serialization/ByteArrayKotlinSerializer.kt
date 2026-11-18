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
