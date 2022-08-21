package io.qalipsis.core.head.configuration

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.paranamer.ParanamerModule
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.jackson.modules.BeanIntrospectionModule
import jakarta.inject.Singleton

@Singleton
internal class ObjectMapperCreationListener : BeanCreatedEventListener<ObjectMapper> {

    override fun onCreated(event: BeanCreatedEvent<ObjectMapper>): ObjectMapper {
        event.bean.apply {
            registerModule(Jdk8Module())
            registerModule(JavaTimeModule())
            registerModule(ParanamerModule())
            registerModule(BeanIntrospectionModule())
            registerModule(
                KotlinModule(
                    nullToEmptyCollection = true,
                    nullToEmptyMap = true,
                    nullIsSameAsDefault = true
                )
            )

            enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
            enable(MapperFeature.AUTO_DETECT_CREATORS)
            enable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            enable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)

            // Serialization configuration.
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

            // Deserialization configuration.
            enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)

            // Null fields are ignored.
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
            setDefaultPropertyInclusion(
                JsonInclude.Value.construct(
                    JsonInclude.Include.NON_NULL,
                    JsonInclude.Include.NON_NULL
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