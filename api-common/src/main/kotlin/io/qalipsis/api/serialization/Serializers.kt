/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.api.serialization

import io.qalipsis.api.services.ServicesFiles
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/**
 * Set of [SerialFormat]s supported by QALIPSIS.
 *
 * @author Eric Jessé
 */
object Serializers {

    /**
     * Loads the [SerialFormatWrapper]s.
     */
    @Suppress("UNCHECKED_CAST")
    fun loadSerializers(): Collection<SerialFormatWrapper<*>> {
        return this.javaClass.classLoader.getResources("META-INF/qalipsis/serializers")
            .toList()
            .flatMap { ServicesFiles.readFile(it.openStream()) }
            .map { loaderClass ->
                Class.forName(loaderClass).getConstructor().newInstance() as SerialFormatWrapper<*>
            }
    }

    /**
     * Configured Kotlin native [Json].
     */
    @ExperimentalSerializationApi
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
        allowStructuredMapKeys = true
        allowSpecialFloatingPointValues = true
        classDiscriminator = "#cl"
    }
}

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
