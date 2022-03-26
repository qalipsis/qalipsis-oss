/*
 * Copyright 2019-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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