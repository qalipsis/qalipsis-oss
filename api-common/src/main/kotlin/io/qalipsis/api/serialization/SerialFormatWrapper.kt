package io.qalipsis.api.serialization

import kotlin.reflect.KClass

/**
 * Interface for generated wrappers of [kotlinx.serialization.SerialFormat]s.
 *
 * @author Eric Jessé
 */
interface SerialFormatWrapper<T> {

    /**
     * Serializes a entity [T] as a [ByteArray].
     */
    fun serialize(entity: T): ByteArray

    /**
     * Deserializes a [ByteArray] as an entity [T].
     */
    fun deserialize(source: ByteArray): T

    /**
     * Lists all the classes supported by the [kotlinx.serialization.SerialFormat].
     */
    val types: Array<KClass<*>>

    /**
     * Name of the module used by the [kotlinx.serialization.SerialFormat].
     */
    val qualifier: String
}