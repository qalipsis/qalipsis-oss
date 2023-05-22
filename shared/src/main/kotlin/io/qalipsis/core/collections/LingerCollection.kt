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
import io.qalipsis.api.sync.Latch
import io.qalipsis.api.sync.SuspendedCountLatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren

import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.time.Duration
import kotlin.coroutines.coroutineContext

/**
 * List of elements able to fairly release them on a regular basis, using the first event reached from [releaseDuration]
 * and [releaseSize].
 *
 * The released elements are removed from this list.
 *
 * @author Eric Jessé
 */
class LingerCollection<T> private constructor(
    private val releaseSize: Int,
    private val releaseDuration: Duration,
    private val delegated: MutableList<T>,
    private val releaseAction: suspend (List<T>) -> Unit
) : List<T> by delegated {

    private lateinit var countLatch: SuspendedCountLatch

    private val additionLatch = Latch(false)

    private lateinit var publicationJob: Job

    private var active = false

    constructor(
        releaseSize: Int,
        releaseDuration: Duration,
        releaseAction: suspend (List<T>) -> Unit
    ) : this(releaseSize, releaseDuration, concurrentList(), releaseAction)

    suspend fun start() {
        if (!active) {
            active = true
            countLatch = SuspendedCountLatch(releaseSize.toLong(), true)
            countLatch.reset()
            publicationJob = with(CoroutineScope(coroutineContext)) {
                launch {
                    try {
                        while (active) {
                            select<Unit> {
                                countLatch.onRelease {
                                    log.trace { "Counter released" }
                                    release()
                                }
                                timer(releaseDuration).onReceive {
                                    log.trace { "Timer released" }
                                    release()
                                }
                            }
                            coroutineContext.cancelChildren()
                        }
                    } catch (e: CancellationException) {
                        // Ignore.
                    } finally {
                        coroutineContext.cancelChildren()
                    }
                }
            }
        }
    }

    private suspend fun release() {
        log.trace { "Releasing values" }
        additionLatch.lock()
        val valuesBatches = mutableListOf<List<T>>()
        do {
            valuesBatches += delegated.pollFirst(releaseSize)
        } while (delegated.size > releaseSize)
        countLatch.reset()
        countLatch.decrement(delegated.size.toLong())

        additionLatch.release()

        valuesBatches.asSequence().filterNot { it.isEmpty() }.forEach { values ->
            releaseAction(values)
        }
    }

    suspend fun stop() {
        active = false
        publicationJob.cancel()
    }

    suspend fun add(element: T) {
        start()
        additionLatch.await()
        delegated.add(element)
        countLatch.decrement()
    }

    suspend fun addAll(elements: Collection<T>) {
        start()
        additionLatch.await()
        delegated.addAll(elements)
        countLatch.decrement(elements.size.toLong().coerceAtMost(countLatch.get()))
    }

    /**
     * Creates a simple timer.
     */
    private fun CoroutineScope.timer(duration: Duration) = produce {
        delay(duration.toMillis())
        send(Unit)
    }

    private companion object {

        val log = logger()

    }
}