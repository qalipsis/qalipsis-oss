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

package io.qalipsis.api.sync

import java.time.Duration
import java.util.concurrent.CompletionStage

/**
 * Wrapper for non-blocking operations to suspendable ones.
 *
 * @author Eric Jessé
 */
interface SuspendedFuture<V> {

    suspend fun get(): V

    suspend fun get(timeout: Duration): V
}

/**
 * Creates a [SuspendedFuture] based upon this [CompletionStage].
 */
fun <V> CompletionStage<V>.asSuspended(): SuspendedFuture<V> = SuspendedFutureForCompletionStage(this)

/**
 * Implementation of [SuspendedFuture] for the [CompletionStage].
 */
internal class SuspendedFutureForCompletionStage<V>(completionStage: CompletionStage<V>) :
    SuspendedFuture<V> {

    private var result = ImmutableSlot<Result<V>>()

    init {
        completionStage.whenComplete { v: V, t: Throwable? ->
            if (t != null) {
                result.offer(Result.failure(t))
            } else {
                result.offer(Result.success(v))
            }
        }
    }

    override suspend fun get(): V {
        return result.get().getOrThrow()
    }

    override suspend fun get(timeout: Duration): V {
        return result.get(timeout).getOrThrow()
    }
}
