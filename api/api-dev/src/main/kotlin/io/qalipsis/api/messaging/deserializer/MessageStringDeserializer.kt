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