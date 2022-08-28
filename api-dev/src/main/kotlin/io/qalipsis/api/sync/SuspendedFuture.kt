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
