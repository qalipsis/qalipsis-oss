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

package io.qalipsis.api.messaging.deserializer

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.micronaut.jackson.modules.BeanIntrospectionModule
import kotlin.reflect.KClass

/**
 * Implementation of [MessageDeserializer] used to deserialize a JSON body to a desired type.
 *
 * @param targetClass desired class to deserialize the JSON body to.
 * @param mapperConfiguration configures the [JsonMapper] used in the deserialization with additional properties.
 */
class MessageJsonDeserializer<V : Any>(
    private val targetClass: KClass<V>,
    mapperConfiguration: (JsonMapper.() -> Unit)? = null
) : MessageDeserializer<V> {

    private val mapper = JsonMapper()

    init {
        mapper.registerModule(BeanIntrospectionModule())
        mapper.registerModule(JavaTimeModule())
        mapper.registerModule(KotlinModule.Builder().build())
        mapper.registerModule(Jdk8Module())

        mapperConfiguration?.let {
            mapper.mapperConfiguration()
        }
    }

    /**
     * Deserializes the [body] using the jackson Json library to the specified class [V].
     */
    override fun deserialize(body: ByteArray): V {

        return mapper.readValue(body, targetClass.java)
    }
}