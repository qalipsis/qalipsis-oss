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