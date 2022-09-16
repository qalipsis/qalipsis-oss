package io.qalipsis.core.serialization.builtin

import io.qalipsis.api.serialization.SerialFormatWrapper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.reflect.KClass

@ExperimentalSerializationApi
internal class StringSerializationWrapper : SerialFormatWrapper<String> {

    override val types: Array<KClass<*>> = arrayOf(String::class)

    override val qualifier: String = "qserializer"

    override fun serialize(entity: String): ByteArray = entity.encodeToByteArray()

    override fun deserialize(source: ByteArray): String = source.decodeToString()
}