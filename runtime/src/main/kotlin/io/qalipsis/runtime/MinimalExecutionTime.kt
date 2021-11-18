package io.qalipsis.runtime

import io.micronaut.context.annotation.Property
import io.qalipsis.core.heads.lifetime.ProcessBlocker
import jakarta.inject.Singleton
import kotlinx.coroutines.delay
import java.time.Duration

/**
 * Process blocker to ensure that the execution of Qalipsis lets enough time for concurrent init to run
 * on systems with few CPU cores.
 *
 * @author Eric Jessé
 */
@Singleton
internal class MinimalExecutionTime(
    @Property(name = "runtime.minimal-duration", defaultValue = "1s") private val minimalDuration: Duration
) : ProcessBlocker {

    private var joined = false

    override fun getOrder() = Int.MIN_VALUE

    override suspend fun join() {
        if (!joined) {
            delay(minimalDuration.toMillis())
            joined = true
        }
    }
}