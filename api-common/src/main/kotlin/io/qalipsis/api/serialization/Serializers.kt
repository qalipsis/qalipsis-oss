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
