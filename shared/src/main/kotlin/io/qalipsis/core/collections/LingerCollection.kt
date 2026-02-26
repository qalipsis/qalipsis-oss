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

package io.qalipsis.core.collections

import io.qalipsis.api.lang.concurrentList
import io.qalipsis.api.lang.pollFirst
import io.qalipsis.api.logging.LoggerHelper.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * A batching collection that accumulates items and releases them when either a size threshold [releaseSize] or a time duration [releaseDuration] is reached.
 *
 * @author Eric Jessé
 */
class LingerCollection<T> private constructor(
    private val releaseSize: Int,
    private val releaseDuration: Duration,
    private val delegated: MutableList<T>,
    private val releaseAction: suspend (List<T>) -> Unit
) : List<T> by delegated {

    private val started = AtomicBoolean(false)

    private val mutex = Mutex()

    private lateinit var publicationJob: Job

    constructor(
        releaseSize: Int,
        releaseDuration: Duration,
        releaseAction: suspend (List<T>) -> Unit
    ) : this(releaseSize, releaseDuration, concurrentList(), releaseAction)

    suspend fun start() {
        if (started.compareAndSet(false, true)) {
            publicationJob = with(CoroutineScope(coroutineContext)) {
                launch {
                    try {
                        while (started.get()) {
                            delay(releaseDuration.toMillis())
                            log.trace { "Timer released" }
                            release(drainAll = true)
                        }
                    } catch (e: CancellationException) {
                        // Ignore.
                    }
                }
            }
        }
    }

    private suspend fun release(drainAll: Boolean = false) {
        val valuesBatches = mutex.withLock {
            log.trace { "Releasing values" }
            val batches = mutableListOf<List<T>>()
            if (drainAll) {
                while (delegated.isNotEmpty()) {
                    batches += delegated.pollFirst(releaseSize)
                }
            } else {
                while (delegated.size >= releaseSize) {
                    batches += delegated.pollFirst(releaseSize)
                }
            }
            batches
        }
        valuesBatches.forEach { values ->
            releaseAction(values)
        }
    }

    suspend fun stop() {
        if (started.compareAndSet(true, false)) {
            publicationJob.cancel()
            release(drainAll = true)
        }
    }

    suspend fun add(element: T) {
        start()
        delegated.add(element)
        if (delegated.size >= releaseSize) {
            release()
        }
    }

    suspend fun addAll(elements: Collection<T>) {
        start()
        delegated.addAll(elements)
        if (delegated.size >= releaseSize) {
            release()
        }
    }

    private companion object {

        val log = logger()

    }
}
