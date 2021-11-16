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
