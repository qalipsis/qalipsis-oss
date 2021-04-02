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