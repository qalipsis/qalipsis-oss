package io.qalipsis.core.factory.serialization.builtin

import io.qalipsis.api.serialization.SerialFormatWrapper
import io.qalipsis.api.serialization.Serializers
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.reflect.KClass

@ExperimentalSerializationApi
class StringSerializationWrapper : SerialFormatWrapper<String> {

    override val types: Array<KClass<*>> = arrayOf(String::class)

    override val qualifier: String = "kjson"

    override fun serialize(entity: String): ByteArray =
        Serializers.json.encodeToString(entity).encodeToByteArray()

    override fun deserialize(source: ByteArray): String =
        Serializers.json.decodeFromString(source.decodeToString())
}
