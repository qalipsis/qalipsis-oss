/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.api.io

import java.io.IOException

/**
 * Suspendable equivalent of [java.io.Closeable].
 *
 * @author Eric Jessé
 */
interface Closeable {

    /**
     * Closes this stream and releases any system resources associated
     * with it. If the stream is already closed then invoking this
     * method has no effect.
     */
    @Throws(IOException::class)
    suspend fun close()

    /**
     * Wrapper to encapsulate instances of [java.io.Closeable] as [io.qalipsis.api.io.Closeable].
     */
    class JavaCloseableWrapper(private val closeable: java.io.Closeable) : Closeable {

        override suspend fun close() {
            closeable.close()
        }

    }
}

