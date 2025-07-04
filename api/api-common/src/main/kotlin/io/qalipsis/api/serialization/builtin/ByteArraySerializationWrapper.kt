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