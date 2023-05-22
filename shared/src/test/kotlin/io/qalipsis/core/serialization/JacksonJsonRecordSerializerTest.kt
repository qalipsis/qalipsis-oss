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
import org.junit.jupiter.api.Test

internal class JacksonJsonRecordSerializerTest {

    private val serializer = JacksonJsonRecordSerializer()

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
        val person = Person("alice", 38)
        assertThat(serializer.acceptsToSerialize(person)).isTrue()

        // when
        val result = serializer.serialize(person)

        // then
        assertThat(serializer.acceptsToDeserialize(result)).isTrue()

        // when
        val deserialized = serializer.deserialize<Person>(result)

        // then
        assertThat(deserialized).isNotNull().isDataClassEqualTo(person)
    }

    @Test
    internal fun `should not accept to deserialize record serialized with another one`() {
        val record = SerializedRecord.from(ByteArray(0), Person::class, "another")
        assertThat(serializer.acceptsToDeserialize(record)).isFalse()
    }

    data class Person(val name: String, val age: Int)
}