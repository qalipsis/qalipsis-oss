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

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import org.junit.jupiter.api.Test

internal class MessageStringDeserializerTest {

    @Test
    fun `should deserialize byte array to string using utf-8 charset`() {
        val deserializer = MessageStringDeserializer()

        val result = deserializer.deserialize("Test message with accentuation: é".toByteArray())

        assertThat(result).isEqualTo("Test message with accentuation: é")
    }

    @Test
    fun `should not deserialize byte array to string using us_ascii charset when string has accentuation`() {
        val deserializer = MessageStringDeserializer(Charsets.US_ASCII)

        val result = deserializer.deserialize("Test message with accentuation: é".toByteArray())

        assertThat(result).isNotEqualTo("Test message with accentuation: é")
    }

    @Test
    fun `should deserialize byte array to string using us_ascii charset when string does not have accentuation`() {
        val deserializer = MessageStringDeserializer(Charsets.US_ASCII)

        val result = deserializer.deserialize("Test message without accentuation".toByteArray())

        assertThat(result).isEqualTo("Test message without accentuation")
    }
}