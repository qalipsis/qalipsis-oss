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

