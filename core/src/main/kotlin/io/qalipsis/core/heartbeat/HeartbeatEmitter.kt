package io.qalipsis.core.heartbeat

import java.time.Duration

/**
 * Generates an heartbeat on a regular basis.
 *
 * @author Eric Jessé
 */
interface HeartbeatEmitter {

    /**
     * Starts the periodic emitter of [Heartbeat]s to [channelName].
     */
    suspend fun start(factoryNodeId: String, channelName: String, period: Duration)

}
