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
package io.qalipsis.core.configuration

import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.internal.LettuceAssert
import java.nio.ByteBuffer

/**
 * A [ComposedRedisCodec] combines two [RedisCodec]s to encode/decode key and value to the command output.
 *
 * @author Dimitris Mandalidis
 * @since 5.2
 */
internal class ComposedRedisCodec<K, V>(keyCodec: RedisCodec<K, *>, valueCodec: RedisCodec<*, V>) : RedisCodec<K, V> {
    private val keyCodec: RedisCodec<K, *>
    private val valueCodec: RedisCodec<*, V>

    init {
        LettuceAssert.notNull(keyCodec, "Key codec must not be null")
        LettuceAssert.notNull(valueCodec, "Value codec must not be null")
        this.keyCodec = keyCodec
        this.valueCodec = valueCodec
    }

    override fun decodeKey(bytes: ByteBuffer): K {
        return keyCodec.decodeKey(bytes)
    }

    override fun decodeValue(bytes: ByteBuffer): V {
        return valueCodec.decodeValue(bytes)
    }

    override fun encodeKey(key: K): ByteBuffer {
        return keyCodec.encodeKey(key)
    }

    override fun encodeValue(value: V): ByteBuffer {
        return valueCodec.encodeValue(value)
    }
}