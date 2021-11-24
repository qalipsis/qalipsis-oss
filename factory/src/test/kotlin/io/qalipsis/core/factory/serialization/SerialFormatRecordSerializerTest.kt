package io.qalipsis.core.factory.serialization

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import io.qalipsis.api.serialization.Serializable
import io.qalipsis.api.serialization.SerializablePerson
import org.junit.jupiter.api.Test

@Suppress("EXPERIMENTAL_API_USAGE")
@Serializable([SerializablePerson::class])
internal class SerialFormatRecordSerializerTest {

    private val serializer = SerialFormatRecordSerializer()

    @Test
    internal fun `should serialize and deserialize the record with a string`() {
        assertThat(serializer.acceptsToSerialize("This is a test")).isTrue()

        // when
        val result = serializer.serialize("This is a test")

        // then
        assertThat(serializer.acceptsToDeserialize(result)).isTrue()

        // when
        val deserialized = serializer.deserialize<String>(result)

        // then
        assertThat(deserialized).isEqualTo("This is a test")
    }

    @Test
    internal fun `should serialize and deserialize the record with an object`() {
        // given
        val person = SerializablePerson("alice", 38)
        assertThat(serializer.acceptsToSerialize(person)).isTrue()

        // when
        val result = serializer.serialize(person)

        // then
        assertThat(serializer.acceptsToDeserialize(result)).isTrue()

        // when
        val deserialized = serializer.deserialize<SerializablePerson>(result)

        // then
        assertThat(deserialized).isNotNull().isDataClassEqualTo(person)
    }

    @Test
    internal fun `should not accept not serialized object`() {
        assertThat(serializer.acceptsToSerialize(MyClass())).isFalse()
    }

    @Test
    internal fun `should not accept to deserialize record serialized with another one`() {
        val record = SerializedRecord.from(ByteArray(0), MyClass::class, "another")
        assertThat(serializer.acceptsToDeserialize(record)).isFalse()
    }

    class MyClass
}