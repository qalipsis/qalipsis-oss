package io.evolue.plugins.netty.tcp

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.AbstractStep
import io.evolue.plugins.netty.configuration.ExecutionEventsConfiguration
import io.evolue.plugins.netty.configuration.ExecutionMetricsConfiguration
import io.evolue.plugins.netty.tcp.spec.KeptAliveTcpClientStepSpecification

/**
 * Step to perform a TCP operations onto a server, reusing the same connection from a past action.
 *
 * @author Eric Jessé
 */
internal class KeptAliveTcpClientStep<I>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    internal val connectionOwner: TcpClientStep<*>,
    private val requestBlock: suspend (input: I) -> ByteArray,
    private val optionsConfiguration: KeptAliveTcpClientStepSpecification.OptionsConfiguration,
    private val metricsConfiguration: ExecutionMetricsConfiguration,
    private val eventsConfiguration: ExecutionEventsConfiguration
) : AbstractStep<I, Pair<I, ByteArray>>(id, retryPolicy) {

    override suspend fun execute(context: StepContext<I, Pair<I, ByteArray>>) {
        log.trace("Executing with {}", context)
        try {
            connectionOwner.execute(context, optionsConfiguration.closeOnFailure, optionsConfiguration.closeAfterUse,
                metricsConfiguration, eventsConfiguration, requestBlock)
        } finally {
            log.trace("Executed with {}", context)
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
