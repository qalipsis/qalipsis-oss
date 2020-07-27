package io.evolue.plugins.netty.monitoring

import io.evolue.api.context.StepContext

/**
 * Interface for recording metrics on the different actions of the Netty clients.
 *
 * @author Eric Jessé
 */
internal interface MetricsRecorder {

    /**
     * Record the elapsed time to successfully establish the connection.
     */
    fun recordSuccessfulConnectionTime(stepContext: StepContext<*, *>, durationNs: Long)

    /**
     * Record the elapsed time until the connection failed.
     */
    fun recordFailedConnectionTime(stepContext: StepContext<*, *>, durationNs: Long)

    /**
     * Record the elapsed time to successfully establish the TLS handshake with the remote.
     */
    fun recordSuccessfulTlsHandshakeTime(stepContext: StepContext<*, *>, durationNs: Long)

    /**
     * Record the elapsed time until the TLS handshake failed.
     */
    fun recordFailedTlsHandshakeTime(stepContext: StepContext<*, *>, durationNs: Long)

    /**
     * Record the elapsed time between the end of the request and the last received byte.
     */
    fun recordTimeToLastByte(stepContext: StepContext<*, *>, durationNs: Long)

    /**
     * Record the number of bytes sent in one or several requests.
     */
    fun recordDataSent(stepContext: StepContext<*, *>, count: Int)

    /**
     * Record the number of bytes received in one or several responses.
     */
    fun recordDataReceived(stepContext: StepContext<*, *>, count: Int)

}
