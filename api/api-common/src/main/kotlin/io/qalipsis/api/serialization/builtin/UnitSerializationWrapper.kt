/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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