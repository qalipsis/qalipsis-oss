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

