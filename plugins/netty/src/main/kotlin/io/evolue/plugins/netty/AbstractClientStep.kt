package io.evolue.plugins.netty

import io.evolue.api.context.StepContext
import io.evolue.api.context.StepId
import io.evolue.api.retry.RetryPolicy
import io.evolue.api.steps.AbstractStep
import io.evolue.plugins.netty.configuration.ConnectionConfiguration
import io.evolue.plugins.netty.configuration.EventsConfiguration
import io.evolue.plugins.netty.configuration.MetricsConfiguration
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.util.concurrent.Future
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

/**
 * Step to perform a TCP operations onto a server.
 *
 * @param I type of the the input of the step, which has to be converted to a [ByteArray] to be sent.
 * @param O type of the output of the step after conversion.
 *
 * @author Eric Jessé
 */
internal abstract class AbstractClientStep<I, O>(
    id: StepId,
    retryPolicy: RetryPolicy?,
    private val connectionConfiguration: ConnectionConfiguration,
    private val metricsConfiguration: MetricsConfiguration,
    private val eventsConfiguration: EventsConfiguration
) : AbstractStep<I, Pair<I, O>>(id, retryPolicy) {

    /**
     * Initializes the bootstrap relatively to the concrete implementation.
     */
    protected abstract fun Bootstrap.initBootstrap(inboundChannel: Channel<ByteArray>,
                                                   resultChannel: Channel<Result<O>>,
                                                   connectionExecutionContext: AtomicReference<ClientExecutionContext>): Bootstrap

    /**
     * Execute the step using the bootstrap.
     */
    protected abstract suspend fun doExecute(
        channel: ChannelFuture,
        context: StepContext<I, Pair<I, O>>,
        inboundChannel: Channel<ByteArray>,
        resultChannel: Channel<Result<O>>,
        connectionExecutionContext: AtomicReference<ClientExecutionContext>
    )

    /**
     * General common configuration of the channel and execution context.
     */
    override suspend fun execute(context: StepContext<I, Pair<I, O>>) {
        val inboundChannel = Channel<ByteArray>(1)
        val resultChannel = Channel<Result<O>>(1)

        // In case the connection is reused among several steps (when the protocol allows it), the context
        // of each execution is passed into a strong reference.
        val connectionExecutionContext = AtomicReference(ClientExecutionContext(
            context, metricsConfiguration.toExecutionMetricsConfiguration(),
            eventsConfiguration.toExecutionEventsConfiguration()
        ))
        val bootstrap = Bootstrap().initBootstrap(inboundChannel, resultChannel, connectionExecutionContext)
        val workerGroup = NioEventLoopGroup()

        bootstrap
            .group(workerGroup)
            .remoteAddress(InetSocketAddress(connectionConfiguration.host!!, connectionConfiguration.port!!))

        connectionConfiguration.let { config ->
            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.connectTimeout.toMillis().toInt())
            bootstrap.option(ChannelOption.SO_RCVBUF, config.receiveBufferSize)
            bootstrap.option(ChannelOption.SO_SNDBUF, config.sendBufferSize)
        }

        val connectTimeStart = System.nanoTime()
        val channel = bootstrap.connect()
        channel.addListener { future ->
            onConnectionEvent(future, Duration.ofNanos(System.currentTimeMillis() - connectTimeStart), resultChannel)
        }
        doExecute(channel, context, inboundChannel, resultChannel, connectionExecutionContext)
    }

    protected open fun onConnectionEvent(future: Future<in Void>, duration: Duration,
                                         resultChannel: Channel<Result<O>>) {
        if (!future.isSuccess) {
            runBlocking { resultChannel.send(Result.failure(future.cause().cause ?: future.cause())) }
        }
    }

}
