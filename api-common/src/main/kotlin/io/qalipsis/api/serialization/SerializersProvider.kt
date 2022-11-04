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
import io.qalipsis.core.serialization.builtin.ByteArraySerializationWrapper
import io.qalipsis.core.serialization.builtin.StringSerializationWrapper
import io.qalipsis.core.serialization.builtin.UnitSerializationWrapper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialFormat
import kotlin.reflect.KClass

/**
 * Provides all the serializers for [SerialFormat]s, fetching the list of serializers from the compile-time generated
 * lists.
 *
 * @author Eric Jessé
 */
@ExperimentalSerializationApi
class SerializersProvider(
    private val serialFormats: Collection<SerialFormat> = listOf(JsonSerializers.json, ProtobufSerializers.protobuf)
) {

    /**
     * All the [SerialFormatWrapper]s found in the classpath.
     */
    val serialFormatWrappers: Collection<SerialFormatWrapper<*>> =
        this.javaClass.classLoader.getResources("META-INF/services/qalipsis/serializers")
            .toList()
            .flatMap { ServicesFiles.readFile(it.openStream()) }
            .map { wrapperClass ->
                buildWrapperInstance(wrapperClass)
            } + NATIVE_SERIALIZERS

    /**
     * Creates a new instance of a [SerialFormatWrapper], passing the convenient [SerialFormat] to the constructor.
     */
    private fun buildWrapperInstance(wrapperClass: String): SerialFormatWrapper<out Any?> {
        val serializerWrapperClass = Class.forName(wrapperClass)
        // Searches the constructor that has a [kotlinx.serialization.SerialFormat] as argument.
        return serializerWrapperClass.constructors.firstOrNull {
            it.parameterCount == 1 && SerialFormat::class.java.isAssignableFrom(it.parameterTypes.first())
        }?.let { constructor ->
            val constructorArgument =
                serialFormats.first {
                    constructor.parameterTypes.first().isAssignableFrom(it::class.java)
                }
            constructor.newInstance(constructorArgument) as SerialFormatWrapper<*>
        } ?: run {
            // Uses the default constructor when no constructors was found with a [kotlinx.serialization.SerialFormat].
            serializerWrapperClass.getConstructor().newInstance() as SerialFormatWrapper<*>
        }
    }

    /**
     * All the [SerialFormatWrapper]s in the classpath grouped by the type they support.
     */
    val serialFormatWrappersByType: Map<KClass<*>, Collection<SerialFormatWrapper<*>>> =
        serialFormatWrappers
            .flatMap { ser -> ser.types.map { type -> type to ser } }
            .groupBy { it.first }
            .mapValues { (_, value) -> value.map { it.second } }
            .toMap()

    fun <T : Any> forType(type: KClass<T>): Collection<SerialFormatWrapper<T>> {
        return serialFormatWrappersByType[type].orEmpty() as Collection<SerialFormatWrapper<T>>
    }

    private companion object {

        val NATIVE_SERIALIZERS =
            listOf(StringSerializationWrapper(), UnitSerializationWrapper(), ByteArraySerializationWrapper())

    }
}
