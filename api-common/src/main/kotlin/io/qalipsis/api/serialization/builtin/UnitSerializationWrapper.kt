package io.qalipsis.core.serialization.builtin

import io.qalipsis.api.serialization.SerialFormatWrapper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.reflect.KClass

@ExperimentalSerializationApi
internal class UnitSerializationWrapper : SerialFormatWrapper<Unit> {

    override val types: Array<KClass<*>> = arrayOf(Unit::class)

    override val qualifier: String = "qserializer"

    override fun serialize(entity: Unit): ByteArray = ByteArray(0)

    override fun deserialize(source: ByteArray): Unit = Unit
}