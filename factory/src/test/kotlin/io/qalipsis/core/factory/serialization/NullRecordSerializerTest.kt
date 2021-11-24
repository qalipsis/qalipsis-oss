package io.qalipsis.core.factory.serialization

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

internal class NullRecordSerializerTest {

    private val serializer = NullRecordSerializer()

    @Test
    internal fun `should serialize and deserialize the record with null`() {
        assertThat(serializer.acceptsToSerialize(null)).isTrue()

        // when
        val result = serializer.serialize(null)

        // then
        assertThat(serializer.acceptsToDeserialize(result)).isTrue()

        // when
        val deserialized = serializer.deserialize<String>(result)

        // then
        assertThat(deserialized).isNull()
    }

    @Test
    internal fun `should not accept to deserialize record serialized with another one`() {
        val record = SerializedRecord.from(ByteArray(0), Unit::class, "another")
        assertThat(serializer.acceptsToDeserialize(record)).isFalse()
    }
}