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
 * Provides all the serializers for [SerialFormat]s.
 *
 * @author Eric Jessé
 */
@ExperimentalSerializationApi
object SerializersProvider {

    /**
     * All the [SerialFormatWrapper]s found in the classpath.
     */
    val serialFormatWrappers: Collection<SerialFormatWrapper<*>> =
        this.javaClass.classLoader.getResources("META-INF/services/qalipsis/serializers")
            .toList()
            .flatMap { ServicesFiles.readFile(it.openStream()) }
            .map { loaderClass ->
                Class.forName(loaderClass).getConstructor().newInstance() as SerialFormatWrapper<*>
            } + listOf(StringSerializationWrapper(), UnitSerializationWrapper(), ByteArraySerializationWrapper())

    /**
     * All the [SerialFormatWrapper]s found accessible by the type they support.
     */
    val serialFormatWrappersByType: Map<KClass<*>, Collection<SerialFormatWrapper<*>>> = serialFormatWrappers
        .flatMap { ser -> ser.types.map { type -> type to ser } }
        .groupBy { (key, _) -> key }
        .mapValues { (_, value) -> value.map { it.second } }
        .toMap()

}
