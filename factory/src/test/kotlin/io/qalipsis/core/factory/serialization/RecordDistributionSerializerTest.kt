package io.qalipsis.core.factory.serialization

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameAs
import assertk.assertions.prop
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.serialization.SerializableClass
import io.qalipsis.api.serialization.Serializers
import io.qalipsis.test.mockk.WithMockk
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test

@Suppress("EXPERIMENTAL_API_USAGE")
@WithMockk
internal class RecordDistributionSerializerTest {

    @RelaxedMockK
    private lateinit var serializer1: RecordSerializer

    @RelaxedMockK
    private lateinit var serializer2: RecordSerializer

    @RelaxedMockK
    private lateinit var serializer3: RecordSerializer

    @Test
    internal fun `should serialize with the first that accepts and succeeds`() {
        // given
        val value = MyClass()
        val record = SerializedRecord.from(
            "Result".encodeToByteArray(),
            String::class,
            "serializer-3",
            mapOf("key1" to "value1")
        )
        every { serializer1.order } returns 0
        every { serializer1.acceptsToSerialize(refEq(value)) } returns false

        every { serializer2.order } returns 1
        every { serializer2.acceptsToSerialize(refEq(value)) } returns true
        every { serializer2.serialize(refEq(value)) } throws RuntimeException()

        every { serializer3.order } returns 2
        every { serializer3.acceptsToSerialize(refEq(value)) } returns true
        every { serializer3.serialize(refEq(value)) } returns record

        val serializer = RecordDistributionSerializer(listOf(serializer3, serializer2, serializer1))

        // when
        val result = serializer.serialize(value)

        // then
        val convertedResult: SerializedRecord = Serializers.json.decodeFromString(result.decodeToString())
        assertThat(convertedResult).all {
            prop(SerializedRecord::value).transform { it.decodeToString() }.isEqualTo("Result")
            prop(SerializedRecord::type).prop(SerializableClass::type).isEqualTo(String::class.java)
            prop(SerializedRecord::serializer).isEqualTo("serializer-3")
            prop(SerializedRecord::metadata).isEqualTo(mapOf("key1" to "value1"))
        }
    }

    @Test
    internal fun `should deserialize with the first that accepts and succeeds`() {
        // given
        val value = MyClass()
        val record = SerializedRecord.from(
            "Result".encodeToByteArray(),
            String::class,
            "serializer-3",
            mapOf("key1" to "value1")
        )
        every { serializer1.order } returns 0
        every { serializer1.acceptsToDeserialize(eq(record)) } returns false

        every { serializer2.order } returns 1
        every { serializer2.acceptsToDeserialize(eq(record)) } returns true
        every { serializer2.deserialize<MyClass>(eq(record)) } throws RuntimeException()

        every { serializer3.order } returns 2
        every { serializer3.acceptsToDeserialize(eq(record)) } returns true
        every { serializer3.deserialize<MyClass>(eq(record)) } returns value

        val serializer = RecordDistributionSerializer(listOf(serializer3, serializer2, serializer1))

        // when
        val result = serializer.deserialize<MyClass>(Serializers.json.encodeToString(record).encodeToByteArray())

        // then
        assertThat(result).isSameAs(value)
    }

    class MyClass
}