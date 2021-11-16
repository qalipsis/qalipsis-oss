package io.qalipsis.api.messaging

import java.time.Instant

/**
 * Record to transport data in a [Topic].
 *
 * @author Eric Jessé
 */
data class Record<T>(
    val headers: MutableMap<String, Any> = mutableMapOf(),
    val received: Instant = Instant.now(),
    val value: T
)