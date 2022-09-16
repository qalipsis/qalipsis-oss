package io.qalipsis.core.serialization.builtin

import io.qalipsis.api.serialization.SerialFormatWrapper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.reflect.KClass

@ExperimentalSerializationApi
internal class ByteArraySerializationWrapper : SerialFormatWrapper<ByteArray> {

    override val types: Array<KClass<*>> = arrayOf(ByteArray::class)

    override val qualifier: String = "qserializer"

    override fun serialize(entity: ByteArray): ByteArray = entity

    override fun deserialize(source: ByteArray): ByteArray = source
}