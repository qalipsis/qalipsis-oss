package io.qalipsis.core.head.communication

import io.qalipsis.core.heartbeat.Heartbeat

/**
 * A [HeartbeatListener] receives [heartbeat]s for processing.
 *
 * @author Eric Jessé
 */
interface HeartbeatListener {

    /**
     * Processes the [Heartbeat].
     */
    suspend fun notify(heartbeat: Heartbeat)
}
