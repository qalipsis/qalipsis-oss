package io.qalipsis.api.messaging.deserializer

import java.nio.charset.Charset

/**
 * Implementation of [MessageDeserializer] used to deserialize the native body to [String] using the
 * defined [Charset].
 *
 * @param charset used to deserialize the [ByteArray] to [String], defaults to UTF-8.
 *
 * @author Gabriel Moraes
 */
class MessageStringDeserializer(private val charset: Charset = Charsets.UTF_8) : MessageDeserializer<String> {

    /**
     * Deserializes the [body] to a [String] object using the defined charset.
     */
    override fun deserialize(body: ByteArray): String {

        return String(body, charset)
    }
}