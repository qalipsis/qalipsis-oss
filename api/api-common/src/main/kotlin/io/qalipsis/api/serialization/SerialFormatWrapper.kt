/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
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