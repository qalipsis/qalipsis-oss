package io.qalipsis.core.head.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async

suspend fun <T> lazyPromise(scope: CoroutineScope, block: suspend CoroutineScope.() -> T): Lazy<T> {
    return lazy {
        scope.async(start = CoroutineStart.LAZY) {
            block.invoke(this)
        }.getCompleted()
    }
}