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

package io.qalipsis.core.serialization

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.api.serialization.Serializable
import io.qalipsis.api.serialization.SerializablePerson
import jakarta.inject.Inject
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.Test

@ExperimentalSerializationApi
@Serializable([SerializablePerson::class])
@MicronautTest(startApplication = false)
internal class SerialFormatRecordSerializerIntegrationTest {

    @Inject
    private lateinit var serializer: SerialFormatRecordSerializer

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
        val record = BinarySerializedRecord.from(ByteArray(0), MyClass::class, "another")
        assertThat(serializer.acceptsToDeserialize(record)).isFalse()
    }

    class MyClass
}