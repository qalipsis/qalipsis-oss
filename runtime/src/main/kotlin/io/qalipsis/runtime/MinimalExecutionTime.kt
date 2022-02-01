package io.qalipsis.runtime

import io.micronaut.context.annotation.Property
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.sync.Latch
import io.qalipsis.core.lifetime.ProcessBlocker
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration

/**
 * Process blocker to ensure that the execution of Qalipsis lets enough time for concurrent init to run
 * on systems with few CPU cores.
 *
 * The first join is blocked for the configured time, the other will only have to wait for the remaining time.
 *
 * @author Eric Jessé
 */
@Singleton
internal class MinimalExecutionTime(
    @Property(name = "runtime.minimal-duration", defaultValue = "1s") private val minimalDuration: Duration
) : ProcessBlocker {

    private var joined = false

    private val latch = Latch(true)

    private var releasingJob: Job? = null

    override fun getOrder() = Int.MIN_VALUE

    override suspend fun join() {
        if (!joined) {
            log.debug { "Waiting for $minimalDuration..." }
            releasingJob = withContext(Dispatchers.Default) {
                launch {
                    delay(minimalDuration.toMillis())
                    latch.cancel()
                }
            }
            joined = true
        }
        latch.await()
    }

    override fun cancel() {
        latch.cancel()
        runCatching { releasingJob?.cancel() }
    }

    private companion object {

        val log = logger()

    }
}