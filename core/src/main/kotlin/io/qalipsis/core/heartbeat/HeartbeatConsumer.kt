package io.qalipsis.core.heartbeat

import kotlinx.coroutines.Job

/**
 * Consumes the [Heartbeat]s coming from the factories.
 *
 * @author Eric Jessé
 */
interface HeartbeatConsumer {

    /**
     * Starts the consumer of [Heartbeat]s from [channelName].
     *
     * @param channelName the name of the channel to consume
     * @param onReceive the action to perform when a [Heartbeat] is received
     */
    suspend fun start(channelName: String, onReceive: suspend (Heartbeat) -> Unit): Job

}
