package io.qalipsis.core.factory.serialization

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import jakarta.inject.Singleton

/**
 * Implementation of [RecordSerializer] using the Jackson library for JSON.
 *
 * @author Eric Jessé
 */
@Suppress("UNCHECKED_CAST")
@Singleton
internal class JacksonJsonRecordSerializer : RecordSerializer {

    override val order: Int = 0

    private val mapper = jsonMapper {
        addModule(JavaTimeModule())
        addModule(KotlinModule())
        addModule(Jdk8Module())

        disable(SerializationFeature.FAIL_ON_SELF_REFERENCES)
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
        disable(SerializationFeature.WRITE_CHAR_ARRAYS_AS_JSON_ARRAYS)
        disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)

        disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)

        enable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    }

    override fun acceptsToSerialize(entity: Any?) = entity != null

    override fun acceptsToDeserialize(record: SerializedRecord): Boolean {
        return record.serializer == QUALIFIER
    }

    override fun <T : Any> serialize(entity: T?, serializationContext: SerializationContext): SerializedRecord {
        val entityType = (entity!!)::class
        return SerializedRecord.from(mapper.writeValueAsBytes(entity), entityType, QUALIFIER)
    }

    override fun <T : Any> deserialize(source: SerializedRecord, deserializationContext: DeserializationContext): T? {
        return mapper.readValue(source.value, source.type.type) as T
    }

    companion object {

        private const val QUALIFIER = "jackson-json"

    }
}