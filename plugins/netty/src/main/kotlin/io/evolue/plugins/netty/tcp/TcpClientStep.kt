package io.evolue.plugins.netty.tcp

import io.evolue.api.context.MinionId
import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.api.retry.RetryPolicy
import io.evolue.plugins.netty.AbstractClientStep
import io.evolue.plugins.netty.ClientContext
import io.evolue.plugins.netty.ClientExecutionContext
import io.evolue.plugins.netty.configuration.ExecutionEventsConfiguration
import io.evolue.plugins.netty.configuration.ExecutionMetricsConfiguration
import io.evolue.plugins.netty.monitoring.EventsRecorder
import io.evolue.plugins.netty.monitoring.MetricsRecorder
import io.evolue.plugins.netty.tcp.spec.TcpConnectionConfiguration
import io.evolue.plugins.netty.tcp.spec.TcpEventsConfiguration
import io.evolue.plugins.netty.tcp.spec.TcpMetricsConfiguration
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelOption
import io.netty.channel.socket.nio.NioSocketChannel
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Step to send and receive data using TCP.
 *
 * @author Eric Jessé
 */
internal class TcpClientStep<I>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    private val requestBlock: suspend (input: I) -> ByteArray,
    private val connectionConfiguration: TcpConnectionConfiguration,
    private val metricsConfiguration: TcpMetricsConfiguration,
    private val eventsConfiguration: TcpEventsConfiguration,
    private val metricsRecorder: MetricsRecorder,
    private val eventsRecorder: EventsRecorder
) : AbstractClientStep<I, ByteArray>(id, retryPolicy, connectionConfiguration,
    metricsConfiguration.toMetricsConfiguration(),
    eventsConfiguration.toEventsConfiguration()) {

    /**
     * Maintains the number of steps actually using the same connection.
     * Default is 1, considering the present step.
     */
    private val usagesCount = AtomicInteger(1)

    /**
     * Specifies if the present step or one reusing the connection requires monitoring.
     */
    private var channelMonitoringForOtherStepsEnabled = false

    /**
     * Long-live client contexts to reuse the connection between different steps.
     */
    private val clientContexts = ConcurrentHashMap<MinionId, ClientContext<ByteArray, ByteArray>>()

    override fun Bootstrap.initBootstrap(inboundChannel: Channel<ByteArray>, resultChannel: Channel<Result<ByteArray>>,
                                         connectionExecutionContext: AtomicReference<ClientExecutionContext>): Bootstrap {
        return channel(NioSocketChannel::class.java)
            .option(ChannelOption.TCP_NODELAY, connectionConfiguration.noDelay)
            .handler(TcpChannelInitializer(
                connectionConfiguration.tlsConfiguration,
                connectionConfiguration.proxyConfiguration,
                metricsConfiguration,
                eventsConfiguration,
                channelMonitoringForOtherStepsEnabled,
                connectionExecutionContext,
                inboundChannel,
                resultChannel,
                metricsRecorder,
                eventsRecorder
            ))
    }

    override suspend fun execute(context: StepContext<I, Pair<I, ByteArray>>) {
        log.trace("Executing with {}", context)
        try {
            if (clientContexts.containsKey(context.minionId)) {
                // If a connection was already open in a previous iteration, reuse it.
                clientContexts[context.minionId]!!.executeWithInitialConfiguration(context,
                    connectionConfiguration.closeOnFailure, usagesCount.get() <= 1 && !connectionConfiguration.keepOpen,
                    requestBlock)
            } else {
                super.execute(context)
            }
        } finally {
            log.trace("Executed with {}", context)
        }
    }

    override suspend fun doExecute(
        channel: ChannelFuture,
        context: StepContext<I, Pair<I, ByteArray>>,
        inboundChannel: Channel<ByteArray>,
        resultChannel: Channel<Result<ByteArray>>,
        connectionExecutionContext: AtomicReference<ClientExecutionContext>) {

        log.trace("Creating a client context for channel ${channel.channel()
            .localAddress()} with ${usagesCount.get()} planned usages")
        val clientContext =
            ClientContext(channel, inboundChannel, resultChannel, connectionExecutionContext,
                usagesCount.get()) { clientContexts.remove(context.minionId) }

        clientContext.execute(context, connectionConfiguration.closeOnFailure, usagesCount.get() <= 1,
            connectionExecutionContext.get().metricsConfiguration, connectionExecutionContext.get().eventsConfiguration,
            requestBlock)
        // Store the contexts only if it is still open.
        if (clientContext.open) {
            clientContexts[context.minionId] = clientContext
        }
    }

    suspend fun <I> execute(context: StepContext<I, Pair<I, ByteArray>>, closeOnFailure: Boolean,
                            closeAfterExecution: Boolean, metricsConfiguration: ExecutionMetricsConfiguration,
                            eventsConfiguration: ExecutionEventsConfiguration,
                            requestBlock: suspend (input: I) -> ByteArray) {
        val clientContext = clientContexts[context.minionId] ?: throw RuntimeException("No open connection")
        clientContext.execute(context, closeOnFailure, closeAfterExecution, metricsConfiguration, eventsConfiguration,
            requestBlock)
    }

    /**
     * Keep the connections open, waiting for a later step to close them manually.
     */
    fun keepOpen() {
        connectionConfiguration.keepOpen = true
    }

    /**
     * Close the connection if not yet done.
     */
    fun close(context: StepContext<*, *>) {
        if (clientContexts.containsKey(context.minionId)) {
            clientContexts[context.minionId]!!.close()
        }
    }

    /**
     * Add a further step as a user of the same connection.
     */
    fun addUsage(withMonitoring: Boolean, count: Int = 1) {
        usagesCount.addAndGet(count)
        channelMonitoringForOtherStepsEnabled = channelMonitoringForOtherStepsEnabled || withMonitoring
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
