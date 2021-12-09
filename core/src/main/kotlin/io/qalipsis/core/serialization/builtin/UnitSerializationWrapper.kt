package io.qalipsis.core.serialization.builtin

import io.qalipsis.api.serialization.SerialFormatWrapper
import io.qalipsis.api.serialization.Serializers
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.reflect.KClass

@ExperimentalSerializationApi
class UnitSerializationWrapper : SerialFormatWrapper<Unit> {

    override val types: Array<KClass<*>> = arrayOf(Unit::class)

    override val qualifier: String = "kjson"

    override fun serialize(entity: Unit): ByteArray =
        Serializers.json.encodeToString(entity).encodeToByteArray()

    override fun deserialize(source: ByteArray): Unit =
        Serializers.json.decodeFromString(source.decodeToString())
}