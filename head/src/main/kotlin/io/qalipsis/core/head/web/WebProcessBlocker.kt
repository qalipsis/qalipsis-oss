package io.qalipsis.core.head.web

import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.lifetime.ProcessBlocker
import jakarta.inject.Singleton

/**
 * Blocker to keep the head process active when the web endpoints are enabled.
 *
 * @author Eric Jessé
 */
@Singleton
@Requirements(
    Requires(env = [ExecutionEnvironments.HEAD, ExecutionEnvironments.STANDALONE]),
    Requires(notEnv = [ExecutionEnvironments.AUTOSTART])
)
internal class WebProcessBlocker : ProcessBlocker {

    private val latch = Latch(true)

    override suspend fun join() {
        latch.await()
    }

    override fun cancel() {
        latch.cancel()
    }
}