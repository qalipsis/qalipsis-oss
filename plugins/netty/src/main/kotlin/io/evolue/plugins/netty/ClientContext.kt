package io.evolue.plugins.netty

import io.evolue.api.context.StepContext
import io.evolue.api.logging.LoggerHelper.logger
import io.evolue.plugins.netty.configuration.ExecutionEventsConfiguration
import io.evolue.plugins.netty.configuration.ExecutionMetricsConfiguration
import io.netty.channel.ChannelFuture
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Long-live client context for any kind of Netty connection.
 *
 * @property channel the Netty [ChannelFuture] representing the physical connection.
 * @property inboundChannel channel used to send a request as a [ByteArray] to the remote address.
 * @property resultChannel channel used to receive a response as a [ByteArray] from the remote address or a [Throwable] that prevails on the response.
 * @param usagesCount number of steps declared to reuse the same connection.
 * @property onClose hook to execute when closing the connection.
 * @property open indicates whether the connection is still active or not.
 * @param I type of the requests
 * @param O type of the responses
 * @author Eric Jessé
 */
internal class ClientContext<I, O>(
    private val channel: ChannelFuture,
    private val inboundChannel: Channel<I>,
    private val resultChannel: Channel<Result<O>>,
    private val executionContext: AtomicReference<ClientExecutionContext>,
    usagesCount: Int,
    private val onClose: () -> Unit
) {

    /**
     * Metrics configuration of the step creating the context.
     */
    val initialExecutionMetricsConfiguration = executionContext.get().metricsConfiguration

    /**
     * Events configuration of the step creating the context.
     */
    val initialExecutionEventsConfiguration = executionContext.get().eventsConfiguration

    var open = true

    private val remainingUsages = AtomicInteger(usagesCount)

    private val mutex = Mutex()

    /**
     * Executes the request using the configuration of the step creating the context.
     */
    suspend fun <T> executeWithInitialConfiguration(context: StepContext<T, Pair<T, O>>, closeOnFailure: Boolean,
                                                    closeAfterExecution: Boolean,
                                                    requestBlock: suspend (input: T) -> I) {
        execute(context, closeOnFailure, closeAfterExecution, initialExecutionMetricsConfiguration,
            initialExecutionEventsConfiguration, requestBlock)
    }

    /**
     * Executes the request using the configuration of a different step than the one using the context.
     */
    suspend fun <T> execute(context: StepContext<T, Pair<T, O>>, closeOnFailure: Boolean,
                            closeAfterExecution: Boolean, metricsConfiguration: ExecutionMetricsConfiguration,
                            eventsConfiguration: ExecutionEventsConfiguration, requestBlock: suspend (input: T) -> I) {
        if (!open) {
            throw RuntimeException("Connection is closed and cannot be reused")
        }
        var failure = false
        try {
            log.trace("Reusing the connection {} for the context {}", channel.channel().localAddress(), context)
            executionContext.set(ClientExecutionContext(context, metricsConfiguration, eventsConfiguration))
            log.trace("Waiting for input")
            val input = context.input.receive()
            log.trace("Processing input")
            val request = requestBlock(input)

            // The use of the connection has to be thread-safe to avoid collisions when reusing in several
            // concurrent steps.
            val result = mutex.withLock {
                log.trace("Sending request")
                inboundChannel.send(request)
                log.trace("Waiting for an answer")
                resultChannel.receive()
            }
            if (result.isSuccess) {
                log.trace("Answer received with success")
                context.output.send(input to result.getOrThrow())
            } else {
                throw result.exceptionOrNull()!!
            }
        } catch (t: Throwable) {
            failure = true
            log.warn("Unexpected error : '${t.message}' on context ${context}", t)
            throw t
        } finally {
            executionContext.set(null)
            if (open && (remainingUsages.decrementAndGet() <= 0 || (failure && closeOnFailure) || closeAfterExecution)) {
                close()
            }
        }
    }

    fun close() {
        log.trace("Closing the connection {}", channel.channel().localAddress())
        open = false
        try {
            channel.channel().close()
        } finally {
            try {
                onClose()
            } finally {
                inboundChannel.close()
                resultChannel.close()
            }
        }
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}