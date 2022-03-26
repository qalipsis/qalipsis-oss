package io.qalipsis.core.lifetime

import io.micronaut.core.order.Ordered
import java.util.Optional

/**
 * Interfaces able to specify the exit code of the main process of QALIPSIS.
 *
 * @author Eric Jessé
 */
interface ProcessExitCodeSupplier : Ordered {

    /**
     * Await the service completion and returns a potential process exit code.
     * When no particular exit code has to be specified, an [Optional.empty] should be returned.
     */
    suspend fun await(): Optional<Int>
}