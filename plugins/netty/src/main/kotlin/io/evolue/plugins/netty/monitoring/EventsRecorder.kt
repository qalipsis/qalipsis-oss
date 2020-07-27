package io.evolue.plugins.netty.monitoring

import io.evolue.api.context.StepContext

/**
 * Interface for recording events on the different actions of the Netty clients.
 *
 * @author Eric Jessé
 */
internal interface EventsRecorder {

    fun recordConnecting(stepContext: StepContext<*, *>)

    fun recordSuccessfulConnection(stepContext: StepContext<*, *>)

    fun recordFailedConnection(stepContext: StepContext<*, *>)

    fun recordSending(stepContext: StepContext<*, *>)

    fun recordSent(stepContext: StepContext<*, *>)

    fun recordReceiving(stepContext: StepContext<*, *>)

    fun recordReceived(stepContext: StepContext<*, *>)

}