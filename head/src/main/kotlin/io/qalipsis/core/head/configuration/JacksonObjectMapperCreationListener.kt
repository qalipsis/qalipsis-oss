/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.configuration

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.paranamer.ParanamerModule
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.jackson.modules.BeanIntrospectionModule
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.inject.Singleton

@Singleton
@Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE])
class JacksonObjectMapperCreationListener : BeanCreatedEventListener<ObjectMapper> {

    override fun onCreated(event: BeanCreatedEvent<ObjectMapper>): ObjectMapper {
        event.bean.apply {
            registerModule(Jdk8Module())
            registerModule(JavaTimeModule())
            registerModule(ParanamerModule())
            registerModule(BeanIntrospectionModule())
            registerModule(
                kotlinModule {
                    configure(KotlinFeature.NullToEmptyCollection, true)
                    configure(KotlinFeature.NullToEmptyMap, true)
                    configure(KotlinFeature.NullIsSameAsDefault, true)
                }
            )
            enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
            enable(MapperFeature.AUTO_DETECT_CREATORS)
            enable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            enable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)

            // Serialization configuration.
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)

            // Deserialization configuration.
            enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)

            setSerializationInclusion(JsonInclude.Include.ALWAYS)
            setDefaultPropertyInclusion(
                JsonInclude.Value.construct(
                    JsonInclude.Include.ALWAYS,
                    JsonInclude.Include.ALWAYS
                )
            )

            setVisibility(
                serializationConfig.defaultVisibilityChecker
                    .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withCreatorVisibility(JsonAutoDetect.Visibility.PUBLIC_ONLY)
            )
        }
        return event.bean
    }
}