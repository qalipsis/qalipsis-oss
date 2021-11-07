package io.qalipsis.runtime.executors

import io.qalipsis.api.coroutines.CoroutineScopeProvider
import io.qalipsis.api.lang.tryAndLogOrNull
import io.qalipsis.api.logging.LoggerHelper.logger
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
            .forEach { scope ->
                val context = scope.coroutineContext
                if (context is ExecutorCoroutineDispatcher) {
                    log.info { "Closing the coroutine dispatcher ${context}" }
                    tryAndLogOrNull(log) {
                        context.close()
                    }
                }
            }
        log.info { "All the coroutine dispatchers were closed" }
    }

    private companion object {

        @JvmStatic
        val log = logger()
    }
}