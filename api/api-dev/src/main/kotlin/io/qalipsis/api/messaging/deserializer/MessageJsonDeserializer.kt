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
        mapper.registerModule(KotlinModule())
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