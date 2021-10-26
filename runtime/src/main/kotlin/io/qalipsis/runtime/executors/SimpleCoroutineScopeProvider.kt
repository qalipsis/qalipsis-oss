package io.qalipsis.runtime.executors

import io.qalipsis.api.coroutines.CoroutineScopeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher

internal class SimpleCoroutineScopeProvider(
    override val global: CoroutineScope,
    override val campaign: CoroutineScope,
    override val io: CoroutineScope,
    override val background: CoroutineScope,
    override val orchestration: CoroutineScope
) : CoroutineScopeProvider {

    override fun close() {
        listOf(global, campaign, io, background, orchestration)
            .mapNotNull { it.coroutineContext as? ExecutorCoroutineDispatcher }
            .forEach { kotlin.runCatching { it.close() } }
    }

}