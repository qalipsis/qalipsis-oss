package io.evolue.plugins.netty.tcp

import io.evolue.api.context.MinionId
import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
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
) : AbstractClientStep<I, ByteArray>(id, retryPolicy, connectionConfiguration, metricsConfiguration.toMetricsConfiguration(),
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

    override suspend fun doExecute(
        channel: ChannelFuture,
        context: StepContext<I, Pair<I, ByteArray>>,
        inboundChannel: Channel<ByteArray>,
        resultChannel: Channel<Result<ByteArray>>,
        connectionExecutionContext: AtomicReference<ClientExecutionContext>) {

        val clientContext =
            ClientContext(channel, inboundChannel, resultChannel, connectionExecutionContext,
                usagesCount.get()) { clientContexts.remove(context.minionId) }

        clientContext.execute(context, connectionConfiguration.closeOnFailure, usagesCount.get() <= 1,
            connectionExecutionContext.get().metricsConfiguration, connectionExecutionContext.get().eventsConfiguration,
            requestBlock)
        // Only store the contexts if it is still open.
        if (clientContext.open) {
            clientContexts[context.minionId] = clientContext
        }
    }

    suspend fun <I> execute(context: StepContext<I, Pair<I, ByteArray>>, closeOnFailure: Boolean,
        closeAfterExecution: Boolean, metricsConfiguration: ExecutionMetricsConfiguration,
        eventsConfiguration: ExecutionEventsConfiguration, requestBlock: suspend (input: I) -> ByteArray) {
        val clientContext = clientContexts[context.minionId] ?: throw RuntimeException("No open connection")
        clientContext.execute(context, closeOnFailure, closeAfterExecution, metricsConfiguration, eventsConfiguration,
            requestBlock)
    }

    /**
     * Add a further step as a user of the same connection.
     */
    fun addUsage(withMonitoring: Boolean) {
        usagesCount.incrementAndGet()
        channelMonitoringForOtherStepsEnabled = channelMonitoringForOtherStepsEnabled || withMonitoring
    }

}
